package com.wlf.storage;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.OSSObject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * storage 模块的集成测试：真打阿里云 OSS。
 *
 * <p><b>为什么必须真打而不是 mock</b>：签名 URL 最容易错的三处——
 * ①用了内网 Endpoint 导致浏览器连不上；②{@code ResponseHeaderOverrides} 没生效；
 * ③中文文件名的 {@code filename*=UTF-8''} 编码写错——在 mock 面前全是绿的，
 * 只有真发一次 HTTP 请求才会暴露。
 *
 * <p>打上 {@code @Tag("oss")}，并在 pom 的 surefire 中默认排除：本测试依赖网络与有效
 * AccessKey，不应让离线环境或 CI 因它变红。单独运行：
 * <pre>./mvnw test -DexcludedGroups= -Dgroups=oss</pre>
 * 两个参数必须同时给——JUnit 的排除优先于包含，只给 {@code -Dgroups=oss} 会一个都不跑。
 *
 * <p>不加 {@code @Transactional}（那是数据库用例的隔离手段），本测试不碰库。
 * 每个用例用 {@code test/} 前缀的随机键，finally 中清理，不在桶里留残留。
 */
@Tag("oss")
@SpringBootTest
@ActiveProfiles("local")
class OssStorageServiceTest {

    @Autowired
    private StorageService storageService;

    /** 直接注入数据面客户端用于读回校验——StorageService 只提供存/签/删，刻意不提供读。 */
    @Autowired
    @Qualifier("ossServer")
    private OSS ossServer;

    @Value("${aliyun.oss.bucket-note}")
    private String bucketNote;

    @Value("${aliyun.oss.bucket-avatar}")
    private String bucketAvatar;

    @Value("${aliyun.oss.endpoint-browser}")
    private String endpointBrowser;

    @Test
    void uploadSignThenDeleteRoundTrip() throws Exception {
        String key = "test/" + UUID.randomUUID() + ".txt";
        byte[] payload = "jotang storage 集成测试 · 中文内容".getBytes(StandardCharsets.UTF_8);
        String downloadName = "我的笔记 v1.txt";

        try {
            // 1. 流式上传
            StoredObject stored = storageService.upload(
                    Bucket.NOTE, key, new ByteArrayInputStream(payload), payload.length, "text/plain");
            assertThat(stored.key()).isEqualTo(key);
            assertThat(stored.contentType()).isEqualTo("text/plain");
            assertThat(stored.size()).isEqualTo(payload.length);

            // 2. 读回校验字节——证明内容完整落到了 OSS，而非只写了元数据
            try (OSSObject object = ossServer.getObject(bucketNote, key)) {
                assertThat(object.getObjectContent().readAllBytes()).isEqualTo(payload);
            }

            // 3. 签名 URL 真发一次请求
            String url = storageService.presignedUrl(Bucket.NOTE, key, Duration.ofMinutes(2), downloadName);
            HttpResponse<byte[]> response = get(url);

            assertThat(response.statusCode())
                    .withFailMessage("签名 URL 请求失败：HTTP %d，OSS 响应体=%s%nURL=%s",
                            response.statusCode(),
                            new String(response.body(), StandardCharsets.UTF_8),
                            url)
                    .isEqualTo(200);
            assertThat(response.body()).isEqualTo(payload);
            // Content-Type 来自对象元数据（上传时写入），不是 URL 参数——OSS 已禁止后者
            assertThat(response.headers().firstValue("Content-Type")).contains("text/plain");
            assertThat(response.headers().firstValue("Content-Disposition"))
                    .hasValueSatisfying(disposition -> assertThat(disposition)
                            .startsWith("attachment")
                            .contains(rfc5987Encode(downloadName)));
        } finally {
            storageService.delete(Bucket.NOTE, key);
        }

        // 4. 删除后对象确实不在（既验证 delete 语义，也确认测试没在桶里留垃圾）
        assertThat(ossServer.doesObjectExist(bucketNote, key)).isFalse();
    }

    @Test
    void publicUrlPointsAtAvatarBucketOnPublicEndpoint() {
        String url = storageService.publicUrl(Bucket.AVATAR, "avatars/1/abc.png");

        assertThat(url)
                .isEqualTo("https://" + bucketAvatar + "." + endpointBrowser + "/avatars/1/abc.png");
    }

    private static HttpResponse<byte[]> get(String url) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(
                    HttpRequest.newBuilder(URI.create(url)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
        }
    }

    /** 与 OssStorageService 的编码保持一致：filename* 里空格必须是 %20 而非 +。 */
    private static String rfc5987Encode(String fileName) {
        return URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
