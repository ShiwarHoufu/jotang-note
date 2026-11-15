package com.wlf.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * 走 SMTP 直接发信。<b>全项目唯一接触 {@code JavaMailSender} 的地方。</b>
 * 见《概要设计》§6.6。
 *
 * <p><b>本类刻意不加 {@code @ConditionalOnProperty}，永远注册。</b>这是引入 MQ 之后
 * 仍然必须成立的一条：消费者（{@code MailDispatchConsumer}）要发信，就得拿到一个
 * 「真的会发 SMTP」的实现。如果本类也跟着 {@code jotang.mail.mode} 一起被门控，
 * 那么 queue 模式下它就不存在了——消费者无处可注入。真正需要门控的是
 * <b>「谁来调本类」</b>（同步的直调 vs 队列的消费者），不是本类自己。
 *
 * <p>超时不在代码里设，全部走 {@code spring.mail.properties.mail.smtp.*}
 * （{@code application.yaml} 里三个都给了 5 秒）。默认值是无超时——同步发信时期
 * 这会把一次 SMTP 卡住原样传导成接口卡住，必须显式设。
 */
@Slf4j
@Component
public class SmtpMailService implements MailService {

    private final JavaMailSender mailSender;

    /**
     * 发件人。
     *
     * <p>必须与 {@code spring.mail.username}（已认证的账号）一致，否则 QQ SMTP 会拒收——
     * 这个约束由配置保证，代码里无从校验。留空时不设置该头，交给 JavaMail 用账号默认值，
     * 这比塞一个空字符串进 {@code From} 头要好（后者会被某些服务器判成非法信头）。
     */
    private final String from;

    public SmtpMailService(JavaMailSender mailSender,
                           @Value("${jotang.mail.from:}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    /**
     * 同步发出。<b>日志不记正文</b>——重置邮件里带着明文令牌，它只该出现在收件人的信箱里，
     * 不该顺带落进应用日志（§8.3）。
     */
    @Override
    public void send(String to, String subject, String textBody) {
        SimpleMailMessage message = new SimpleMailMessage();
        if (!from.isBlank()) {
            message.setFrom(from);
        }
        message.setTo(to);
        message.setSubject(subject);
        message.setText(textBody);
        mailSender.send(message);
    }
}
