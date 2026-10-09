package com.wlf.storage;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.OSSObject;
import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
                    Bucket.NOTE, key, new ByteArrayInputStream(payload), payload.length, "text/plain", null);
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

    /**
     * 读取原语：§6.2 的 MD / 文本预览走后端代理，要靠它把对象内容取回来。
     *
     * <p>用 try-with-resources 走一遍，同时验证返回的流能被正常关闭——OSSObject 持有的
     * HTTP 连接由该流负责释放，若实现只取了内容而丢掉 OSSObject，连接就会泄漏。
     */
    @Test
    void openReturnsFullObjectContent() throws Exception {
        String key = "test/" + UUID.randomUUID() + ".txt";
        byte[] payload = "后端代理预览 · /raw".getBytes(StandardCharsets.UTF_8);

        try {
            storageService.upload(Bucket.NOTE, key, new ByteArrayInputStream(payload),
                    payload.length, "text/plain", null);

            try (InputStream in = storageService.open(Bucket.NOTE, key)) {
                assertThat(in.readAllBytes()).isEqualTo(payload);
            }
        } finally {
            storageService.delete(Bucket.NOTE, key);
        }
    }

    /**
     * 对象不存在时统一翻译成 50000，不把 OSS 的 NoSuchKey 原样泄漏给上层。
     *
     * <p>正常业务流程走不到这里——读取前调用方已校验笔记状态。真出现「库里记着、对象没了」
     * 说明数据不一致，是服务端问题，不该让前端按「资源不存在」处理。
     */
    @Test
    void openTranslatesMissingObjectIntoServerError() {
        String missingKey = "test/" + UUID.randomUUID() + "-absent.txt";

        assertThatThrownBy(() -> storageService.open(Bucket.NOTE, missingKey))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SERVER_ERROR));
    }

    /**
     * 预览路径：{@code downloadName} 传 null，签出的 URL 供前端内联展示。
     *
     * <p>与下载用例的区别是本方法不携带下载文件名——URL 里既没有 filename 也没有 attachment，
     * 否则 OSS 会把它当成附件下载并在保存时改名。
     *
     * <p><b>注意</b>：拿这个 URL 在浏览器地址栏直接打开会被 OSS <b>强制下载</b>。默认域名
     * （*.aliyuncs.com）会对图片、PDF 等类型附加 {@code Content-Disposition: attachment}
     * 与 {@code x-oss-force-download: true}（实测确认，阿里云错误码 0048-00000101），
     * 显式请求 inline 也压不过它。但这只影响「导航」场景：前端用 {@code <img src>} 加载
     * 子资源时浏览器会忽略该响应头，图片照常显示；真正受影响的只有必须走 {@code <iframe>}
     * 的 PDF 预览。详见《概要设计》§6.2。故本用例只断言「URL 由我们构造正确」，
     * 不对那个由平台强加的响应头做断言。
     */
    @Test
    void previewUrlCarriesInlineDispositionAndNoDownloadName() throws Exception {
        String key = "test/" + UUID.randomUUID() + ".png";
        byte[] payload = onePixelPng();

        try {
            storageService.upload(Bucket.NOTE, key, new ByteArrayInputStream(payload),
                    payload.length, "image/png", null);

            // downloadName 传 null = 预览（§6.2：TTL 5min）
            String url = storageService.presignedUrl(Bucket.NOTE, key, Duration.ofMinutes(5), null);
            HttpResponse<byte[]> response = get(url);

            assertThat(response.statusCode())
                    .withFailMessage("签名 URL 请求失败：HTTP %d，OSS 响应体=%s%nURL=%s",
                            response.statusCode(),
                            new String(response.body(), StandardCharsets.UTF_8),
                            url)
                    .isEqualTo(200);
            assertThat(response.body()).isEqualTo(payload);
            // 类型取自对象元数据（上传时写入），浏览器据此渲染
            assertThat(response.headers().firstValue("Content-Type")).contains("image/png");

            // 我们这一侧能控制的部分：声明 inline，且绝不夹带下载文件名
            assertThat(url)
                    .withFailMessage("预览 URL 不该带下载文件名，实际=%s", url)
                    .contains("response-content-disposition=inline")
                    .doesNotContain("filename")
                    .doesNotContain("attachment");
        } finally {
            storageService.delete(Bucket.NOTE, key);
        }
    }

    @Test
    void publicUrlPointsAtAvatarBucketOnPublicEndpoint() {
        String url = storageService.publicUrl(Bucket.AVATAR, "avatars/1/abc.png");

        assertThat(url)
                .isEqualTo("https://" + bucketAvatar + "." + endpointBrowser + "/avatars/1/abc.png");
    }

    /**
     * 头像对象的 {@code Cache-Control} 必须真的写进元数据——它是 §6.7「版本化键 + 长强缓存」
     * 的另一半。只断言「调用方传了这个参数」抓不到「SDK 没把它落到对象上」这类错，
     * 所以真传一次、再从头像桶的公网地址读回来。
     *
     * <p>本用例同时验证了头像桶确实是<b>公共读</b>：非 200 多半是桶的策略没配对，
     * 而那种错在单元测试里完全看不出来。
     */
    @Test
    void avatarUploadCarriesImmutableCacheControl() throws Exception {
        String key = "test/avatars/" + UUID.randomUUID() + ".png";
        byte[] payload = onePixelPng();
        String url = storageService.publicUrl(Bucket.AVATAR, key);

        try {
            storageService.upload(Bucket.AVATAR, key, new ByteArrayInputStream(payload),
                    payload.length, "image/png", "public, max-age=31536000, immutable");

            HttpResponse<byte[]> response = get(url);

            assertThat(response.statusCode())
                    .withFailMessage("头像公网直读失败：HTTP %d，多半是头像桶没配成公共读%nURL=%s",
                            response.statusCode(), url)
                    .isEqualTo(200);
            assertThat(response.body()).isEqualTo(payload);
            assertThat(response.headers().firstValue("Cache-Control")).contains("max-age=31536000");
        } finally {
            storageService.delete(Bucket.AVATAR, key);
        }
    }

    /**
     * 用 ImageIO 生成一张 1×1 PNG。
     *
     * <p>刻意生成真实图片而不是随手编一串字节：用例断言的是「图片能内联预览」，
     * 若将来有人加上真正的图片校验，编造的字节会让这个用例变成假的通过。
     */
    private static byte[] onePixelPng() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), "png", out);
        return out.toByteArray();
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
