package com.wlf.mail;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真发一封邮件，验证 SMTP 这条链路本身。默认被 pom 的 {@code excludedGroups} 排除。
 *
 * <pre>
 *   在 application-local.yaml 里配好 spring.mail.username / .password（QQ 授权码）
 *   ./mvnw test -DexcludedGroups= -Dgroups=mail
 * </pre>
 *
 * <p><b>为什么非要有这一层。</b>本功能其余全部用例都把 {@code MailService} 换成了 mock，
 * 所以「邮件到底发不发得出去」在默认套件里<b>一个字都没验证过</b>。
 * 而这条链路的错误方式恰恰都是静默的：主机名写错、端口 465 配成 587 却仍开着隐式 SSL、
 * 发件人与已认证账号不一致（QQ 会拒收）——这些都不会让应用启动失败，也不会让任何用例变红，
 * 只会表现为「用户说没收到信」。与 {@code OrphanObjectSweeperOssTest} 是同一条理由。
 *
 * <p><b>⚠ 本用例在本仓库当前状态下尚未被实际执行过</b>——本机没有配 SMTP 授权码。
 * 也就是说它「写出来了」不等于「它是对的」：第一次跑的时候若失败，
 * 先怀疑这个用例本身（断言、收件人选取），再怀疑配置。
 *
 * <p>收件人取发件人自己（{@code spring.mail.username}），不往任何真实用户信箱发。
 */
@Tag("mail")
@SpringBootTest
@ActiveProfiles("local")
class SmtpMailServiceTest {

    /**
     * 直接注入具体类而不是 {@code MailService} 接口：{@code jotang.mail.mode=queue} 时
     * 接口上会解析到 {@code QueuedMailService}，那样这个用例就变成「投一条消息」并通过，
     * 而 SMTP 一个字节都没发出去——一个会骗人的自测。
     */
    @Autowired
    private SmtpMailService smtpMailService;

    @Value("${spring.mail.username:}")
    private String account;

    @Test
    void sendsARealMessage() {
        assertThat(account)
                .as("需要在 application-local.yaml 里配 spring.mail.username（发件邮箱）才能跑本用例")
                .isNotBlank();

        smtpMailService.send(account,
                "【Jotang Note】SMTP 连通性自测",
                "这是一封自测邮件，收到即说明 SMTP 链路（主机 / 端口 / SSL / 授权码 / 发件人）可用。");
    }
}
