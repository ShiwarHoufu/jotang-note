package com.wlf.note;

import com.wlf.common.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 multipart 的落盘配置真的生效（§6.1）。
 *
 * <p><b>为什么必须真起一个服务</b>：{@code MockMvc} 用的 {@code MockMultipartFile} 是内存对象，
 * 压根不经过 servlet 的 multipart 解析，因此 {@code file-size-threshold} 与
 * {@code location} 这两项配置在 {@code MockMvc} 下完全没被走到——写错了也一片绿。
 * 而它们的失效方式是「部署后传大文件才炸」，属于最该被自动化挡下的那一类。
 *
 * <p>用例只发一个超过 1MB 的 <b>非法类型</b> 文件（svg）：请求体的解析照常落盘，
 * 随后被 {@link NoteFilePolicy} 以 42200 拒掉，因此不碰 OSS，也不往库里写任何东西。
 *
 * <p>token 用一个不存在的 userId 签。JWT 只验签名与有效期、不查库
 * （见 {@code AuthenticatedUser} 的注释），鉴权这一关照样过；
 * 而上传在文件校验就失败了，那个 userId 不会被写进任何一行。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
class NoteMultipartConfigTest {

    @Value("${local.server.port}")
    private int port;

    @Value("${spring.servlet.multipart.location}")
    private String uploadTmp;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void oversizedPartIsBufferedIntoTheDedicatedTempDirectory() throws Exception {
        Path tempDir = Paths.get(uploadTmp);
        // 从干净状态开始：目录若本来就存在（上次运行留下的），「它存在」这条断言就成了恒真。
        // 删完立刻确认它真的不在了——否则删除失败（比如被别的进程占着）会让本用例悄悄失效
        deleteQuietly(tempDir);
        assertThat(tempDir).as("前置条件：中转目录此刻不应存在").doesNotExist();

        // 2MB > file-size-threshold(1MB)，故该部件会落到 location 指定的目录而不是留在堆里
        byte[] body = buildMultipartBody("evil.svg", "image/svg+xml", new byte[2 * 1024 * 1024]);

        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/notes"))
                        .header("Authorization", "Bearer " + jwtTokenProvider.issue(999_999_999L, "cfg_tester", "USER"))
                        .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(422);
        assertThat(response.body()).contains("42200");
        assertThat(tempDir).as("multipart.location 指定的中转目录应由容器按需创建").exists().isDirectory();
    }

    private static final String BOUNDARY = "----JotangTestBoundary";

    private static byte[] buildMultipartBody(String filename, String contentType, byte[] content) throws IOException {
        var out = new java.io.ByteArrayOutputStream();
        out.write(("--" + BOUNDARY + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(content);
        out.write(("\r\n--" + BOUNDARY + "\r\n"
                + "Content-Disposition: form-data; name=\"title\"\r\n\r\n配置校验\r\n"
                + "--" + BOUNDARY + "\r\n"
                + "Content-Disposition: form-data; name=\"courseId\"\r\n\r\n1\r\n"
                + "--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private static void deleteQuietly(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // 删不掉就留着，断言会给出结论
                }
            });
        }
    }
}
