package com.wlf.config;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 阿里云 OSS 客户端配置：两个访问面、两个 Bucket（笔记私有 / 头像公共读）。
 * 见《概要设计》§1.2、§6.7。
 *
 * <p><b>为什么要两个客户端</b>：签名 URL 的主机名取自客户端构造时配的 Endpoint。
 * 若用内网客户端去签名，签出来的 URL 指向 {@code *-internal.aliyuncs.com}——
 * 该域名只在阿里云内网可解析，<b>浏览器根本连不上</b>。这类错误单元测试发现不了，
 * 只有真发一次 HTTP 请求才暴露，故在设计上直接拆开：
 *
 * <ul>
 *   <li>{@code ossServer}：数据面（上传 / 删除），走 {@code endpoint-server}</li>
 *   <li>{@code ossBrowser}：签名面，走 {@code endpoint-browser}</li>
 * </ul>
 *
 * <p>两个 Endpoint 都做成配置项而非写死内网/公网：生产上 ECS 与 OSS 同 Region，
 * {@code endpoint-server} 取内网（流量免费、不占公网出口，§1.2）；
 * 但本机开发不在阿里云内网，内网域名解析到 {@code 100.115.x.x} 且不可达，
 * 由 application-local.yaml 覆写为公网——「哪个 Endpoint」是环境问题，不是代码问题。
 *
 * <p>AccessKey 缺失或为空时构造即失败——与 {@code JwtTokenProvider} 同一取舍：
 * 配置错误在启动时暴露，不留到运行时。
 */
@Configuration
public class OssConfig {

    private final String accessKeyId;
    private final String accessKeySecret;
    private final String endpointServer;
    private final String endpointBrowser;

    public OssConfig(@Value("${aliyun.oss.access-key-id}") String accessKeyId,
                     @Value("${aliyun.oss.access-key-secret}") String accessKeySecret,
                     @Value("${aliyun.oss.endpoint-server}") String endpointServer,
                     @Value("${aliyun.oss.endpoint-browser}") String endpointBrowser) {
        this.accessKeyId = accessKeyId;
        this.accessKeySecret = accessKeySecret;
        this.endpointServer = endpointServer;
        this.endpointBrowser = endpointBrowser;
    }

    /** 数据面：上传、删除等所有服务端操作。生产走内网 Endpoint。 */
    @Bean(destroyMethod = "shutdown")
    public OSS ossServer() {
        return new OSSClientBuilder().build(endpointServer, accessKeyId, accessKeySecret, clientConfig());
    }

    /** 签名面：仅用于 {@code generatePresignedUrl}，签出的 URL 供浏览器公网直连。 */
    @Bean(destroyMethod = "shutdown")
    public OSS ossBrowser() {
        return new OSSClientBuilder().build(endpointBrowser, accessKeyId, accessKeySecret, clientConfig());
    }

    /**
     * 每次调用返回新实例，两个客户端各持一份——避免共享同一个可变配置对象。
     *
     * <p>socket 超时按上传场景放宽：单文件可达 100MB（§6.1），用 SDK 默认值容易在大文件上误超时。
     * 刻意<b>不</b>设 requestTimeout：本方法的入参是浏览器上传的流，putObject 的耗时受客户端
     * 上行速度牵制，设死总超时会让慢网络下的大文件上传被误杀。
     */
    private static ClientBuilderConfiguration clientConfig() {
        ClientBuilderConfiguration config = new ClientBuilderConfiguration();
        config.setConnectionTimeout(5_000);
        config.setSocketTimeout(60_000);
        config.setMaxConnections(200);
        return config;
    }
}
