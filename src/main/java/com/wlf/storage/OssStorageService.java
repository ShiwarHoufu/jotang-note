package com.wlf.storage;

import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import com.aliyun.oss.model.ResponseHeaderOverrides;
import com.wlf.common.BusinessException;
import com.wlf.common.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

/**
 * StorageService 的阿里云 OSS 实现，数据读写一律走内网 Endpoint。
 * 见《概要设计》§1.2。
 *
 * <p>持有两个客户端：{@code ossServer} 做数据面（上传 / 删除），
 * {@code ossBrowser} 只用于签名（原因见 {@link com.wlf.config.OssConfig}）。
 */
@Slf4j
@Service
public class OssStorageService implements StorageService {

    private final OSS ossServer;
    private final OSS ossBrowser;
    private final String bucketNote;
    private final String bucketAvatar;
    private final String endpointBrowser;

    public OssStorageService(@Qualifier("ossServer") OSS ossServer,
                             @Qualifier("ossBrowser") OSS ossBrowser,
                             @Value("${aliyun.oss.bucket-note}") String bucketNote,
                             @Value("${aliyun.oss.bucket-avatar}") String bucketAvatar,
                             @Value("${aliyun.oss.endpoint-browser}") String endpointBrowser) {
        this.ossServer = ossServer;
        this.ossBrowser = ossBrowser;
        this.bucketNote = bucketNote;
        this.bucketAvatar = bucketAvatar;
        this.endpointBrowser = endpointBrowser;
    }

    @Override
    public StoredObject upload(Bucket bucket, String key, InputStream in, long size, String contentType) {
        String bucketName = resolve(bucket);
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            // InputStream 入参下 SDK 无法自行推断长度：给错会失败，给 -1 会退化成先缓冲再传，大文件会吃内存
            metadata.setContentLength(size);
            metadata.setContentType(contentType);

            ossServer.putObject(new PutObjectRequest(bucketName, key, in, metadata));
            return new StoredObject(key, contentType, size);
        } catch (OSSException e) {
            log.error("上传对象失败 bucket={} key={} ossErrorCode={}", bucketName, key, e.getErrorCode(), e);
            throw new BusinessException(ErrorCode.SERVER_ERROR);
        }
    }

    @Override
    public String presignedUrl(Bucket bucket, String key, Duration ttl, String downloadName) {
        String bucketName = resolve(bucket);
        try {
            GeneratePresignedUrlRequest request =
                    new GeneratePresignedUrlRequest(bucketName, key, HttpMethod.GET);
            request.setExpiration(new Date(System.currentTimeMillis() + ttl.toMillis()));

            if (downloadName != null) {
                // 只覆写 Content-Disposition。Content-Type 刻意不碰：它取自对象元数据（上传时由服务端判定并写入），
                // 且 OSS 自 2025-01-20 起禁止签名 URL 覆盖 response-content-type
                // 浏览器访问这个链接直接弹出下载框，而不是在线预览，下载文件名为downloadName
                ResponseHeaderOverrides overrides = new ResponseHeaderOverrides();
                overrides.setContentDisposition(attachmentDisposition(downloadName));
                request.setResponseHeaders(overrides);
            }

            // 必须用浏览器面客户端：签名里的主机名取自客户端 Endpoint（见 OssConfig 注释）
            return ossBrowser.generatePresignedUrl(request).toString();
        } catch (OSSException e) {
            log.error("签发签名 URL 失败 bucket={} key={} ossErrorCode={}", bucketName, key, e.getErrorCode(), e);
            throw new BusinessException(ErrorCode.SERVER_ERROR);
        }
    }

    @Override
    public String publicUrl(Bucket bucket, String key) {
        // 纯字符串拼接，不经过 SDK，也不签名（头像桶公共读，§6.7）
        return "https://" + resolve(bucket) + "." + endpointBrowser + "/" + key;
    }

    @Override
    public void delete(Bucket bucket, String key) {
        String bucketName = resolve(bucket);
        try {
            // deleteObject 对不存在的对象同样返回 204，天然幂等，无需先判存在
            ossServer.deleteObject(bucketName, key);
        } catch (OSSException e) {
            log.error("删除对象失败 bucket={} key={} ossErrorCode={}", bucketName, key, e.getErrorCode(), e);
            throw new BusinessException(ErrorCode.SERVER_ERROR);
        }
    }

    private String resolve(Bucket bucket) {
        return switch (bucket) {
            case NOTE -> bucketNote;
            case AVATAR -> bucketAvatar;
        };
    }

    /**
     * 构造「下载并还原原始文件名」的 Content-Disposition（§6.2）。
     *
     * <p>文件名可能含中文，须用 RFC 5987 的 {@code filename*=UTF-8''} 写法。
     * 注意 {@link URLEncoder} 把空格编成 {@code +}，在 filename* 里必须还原为 {@code %20}，
     * 否则文件名带空格时会被还原成加号。
     */
    private static String attachmentDisposition(String fileName) {
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename*=UTF-8''" + encoded;
    }
}
