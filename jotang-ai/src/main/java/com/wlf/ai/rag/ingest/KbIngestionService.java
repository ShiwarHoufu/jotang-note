package com.wlf.ai.rag.ingest;

import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 知识库导入：先把目录下的文件加载成 {@link Resource}。 */
@Slf4j
@Service
public class KbIngestionService {

    /** 目标块大小（token）。中文约 400~600 字，适合作为一条检索结果 */
    private static final int CHUNK_SIZE = 600;
    /** 按标点回退时，块短于这个字符数就不回退，避免切出碎块 */
    private static final int MIN_CHUNK_SIZE_CHARS = 200;
    /** 短于这个长度的块直接丢弃 */
    private static final int MIN_CHUNK_LENGTH_TO_EMBED = 10;
    /** 单篇文档最多切多少块，防止异常文件把索引撑爆 */
    private static final int MAX_NUM_CHUNKS = 5000;

    /** 英文标点之外补上中文句读，切分点更自然 */
    private static final List<Character> PUNCTUATION =
            List.of('.', '?', '!', '\n', '。', '？', '！', '；', ';');

    private final TokenTextSplitter splitter = TokenTextSplitter.builder()
            .withChunkSize(CHUNK_SIZE)
            .withMinChunkSizeChars(MIN_CHUNK_SIZE_CHARS)
            .withMinChunkLengthToEmbed(MIN_CHUNK_LENGTH_TO_EMBED)
            .withMaxNumChunks(MAX_NUM_CHUNKS)
            .withKeepSeparator(true)
            .withPunctuationMarks(PUNCTUATION)
            .build();

    private final ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    private final String kbDir;
    private final KbDocumentReader documentReader;
    private final VectorStore vectorStore;

    public KbIngestionService(@Value("${jotang.ai.kb.dir}") String kbDir,
                              KbDocumentReader documentReader,
                              VectorStore vectorStore) {
        this.kbDir = kbDir;
        this.documentReader = documentReader;
        this.vectorStore = vectorStore;
    }

    /** 加载知识库目录下的所有普通文件，按路径排序；不支持的类型留给读取阶段拒绝。 */
    public List<Resource> loadResources() {
        Path root = Paths.get(kbDir).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "知识库目录不存在：" + root);
        }

        String base = root.toUri().toString();
        if (!base.endsWith("/")) {
            base += "/";
        }

        Resource[] found;
        try {
            found = resolver.getResources(base + "**/*");
        } catch (IOException e) {
            log.error("扫描知识库目录失败：{}", root, e);
            throw new BusinessException(ErrorCode.SERVER_ERROR, "扫描知识库目录失败");
        }

        List<Resource> resources = Arrays.stream(found)
                // 去掉目录、隐藏文件（.DS_Store 之类）
                .filter(resource -> resource instanceof FileSystemResource file && file.getFile().isFile())
                .filter(resource -> {
                    String filename = resource.getFilename();
                    return filename != null && !filename.startsWith(".");
                })
                .sorted(Comparator.comparing(Resource::getDescription))
                .toList();

        log.info("知识库目录 {} 加载到 {} 个文件", root, resources.size());
        return resources;
    }

    /** 把读取到的文档切成检索块；Spring AI 会把原文档的元数据复制到每个块。 */
    public List<Document> split(List<Document> documents) {
        return splitter.apply(documents);
    }

    /** 导入整个知识库目录；单个文件失败不影响其他文件。 */
    public IngestReport ingestAll() {
        List<Resource> resources = loadResources();
        List<String> failed = new ArrayList<>();
        int indexed = 0;

        for (Resource resource : resources) {
            try {
                int chunks = ingest(resource);
                indexed++;
                log.info("已导入 {}，{} 个块", resource.getFilename(), chunks);
            } catch (BusinessException e) {
                failed.add(resource.getFilename() + "：" + e.getMessage());
                log.warn("导入失败 {}：{}", resource.getFilename(), e.getMessage());
            } catch (RuntimeException e) {
                failed.add(resource.getFilename() + "：" + e.getMessage());
                log.error("导入失败 {}", resource.getFilename(), e);
            }
        }

        log.info("知识库导入完成：文件 {} 个，成功 {} 个，失败 {} 个",
                resources.size(), indexed, failed.size());
        return new IngestReport(resources.size(), indexed, failed);
    }

    /** 导入单个文件：解析 → 切块 → 补元数据 → 覆盖该文档在向量库里的旧块。 */
    public int ingest(Resource resource) {
        KbMetadata metadata = metadataOf(resource);
        List<Document> chunks = enrich(split(documentReader.read(resource)), metadata);
        if (chunks.isEmpty()) {
            return 0;
        }

        // 先删旧块：文档更新后 chunk 数可能变少，只靠 upsert 会留下多余的点
        vectorStore.delete(new FilterExpressionBuilder()
                .eq("document_id", metadata.documentId())
                .build());
        vectorStore.add(chunks);
        return chunks.size();
    }

    private List<Document> enrich(List<Document> chunks, KbMetadata metadata) {
        return chunks.stream().map(chunk -> {
            int chunkIndex = intValue(chunk.getMetadata().get("chunk_index"));
            int totalChunks = intValue(chunk.getMetadata().get("total_chunks"));

            // Qdrant payload 只接受标量；TextReader 的 charset 等对象字段不能带进去
            Map<String, Object> payload = new HashMap<>();
            payload.put("document_id", metadata.documentId());
            payload.put("document_path", metadata.path());
            payload.put("title", metadata.title());
            payload.put("department", metadata.department());
            payload.put("source_url", metadata.sourceUrl());
            payload.put("status", "active");
            payload.put("chunk_index", chunkIndex);
            payload.put("total_chunks", totalChunks);

            return Document.builder()
                    .id(pointId(metadata.documentId(), chunkIndex))
                    .text(chunk.getText())
                    .metadata(payload)
                    .build();
        }).toList();
    }

    private KbMetadata metadataOf(Resource resource) {
        String path = relativePath(resource);
        String filename = resource.getFilename();
        String title = stripExtension(filename);
        int separator = path.indexOf('/');
        String department = separator > 0 ? path.substring(0, separator) : "";
        // source_url 等官网来源信息等 manifest 接入后再补
        return new KbMetadata(sha256(path), path, title, department, "");
    }

    private String relativePath(Resource resource) {
        if (!(resource instanceof FileSystemResource file)) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "无法解析文件路径：" + resource);
        }
        Path root = Paths.get(kbDir).toAbsolutePath().normalize();
        Path path = file.getFile().toPath().toAbsolutePath().normalize();
        if (!path.startsWith(root)) {
            throw new BusinessException(ErrorCode.SERVER_ERROR, "文件不在知识库目录内：" + path);
        }
        return root.relativize(path).toString().replace('\\', '/');
    }

    private static int intValue(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    /** Qdrant 的 point id 必须是 UUID；用文档 id + 块序号生成，重复导入会覆盖同一个点。 */
    private static String pointId(String documentId, int chunkIndex) {
        String seed = documentId + ":" + chunkIndex;
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
    }

    /** 文档 id 取相对路径的哈希：文件挪动或改名视为新文档，重跑是幂等的。 */
    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 不支持 SHA-256", e);
        }
    }

    private static String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private record KbMetadata(String documentId, String path, String title,
                              String department, String sourceUrl) {
    }

    /** 一次全量导入的结果。 */
    public record IngestReport(int total, int indexed, List<String> failed) {

        public IngestReport {
            failed = List.copyOf(failed);
        }
    }
}
