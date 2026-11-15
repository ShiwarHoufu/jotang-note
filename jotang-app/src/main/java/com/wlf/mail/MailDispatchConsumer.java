package com.wlf.mail;

import com.wlf.config.RabbitConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 队列的消费端：把 {@link MailDispatch} 真正发出去。见《概要设计》§6.6。
 *
 * <p>失败必须原样外抛。本方法不 try/catch。抛出去，容器才会按
 * {@code spring.rabbitmq.listener.simple.retry} 的配置重试；重试耗尽后消息被送进死信队列。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "jotang.mail.mode", havingValue = "queue")
public class MailDispatchConsumer {

    private final SmtpMailService smtpMailService;

    public MailDispatchConsumer(SmtpMailService smtpMailService) {
        this.smtpMailService = smtpMailService;
        // 启动横幅的第二半（另一半在 QueuedMailService）：两行都在才是「走队列且消费者已就位」
        log.info("重置邮件消费者已启动，监听 queue={}", RabbitConfig.PASSWORD_RESET_QUEUE);
    }

    /**
     * 一条消息 = 一封邮件。
     */
    @RabbitListener(queues = RabbitConfig.PASSWORD_RESET_QUEUE)
    public void onMessage(MailDispatch dispatch) {
        // 到这里说明消息确实是 MailDispatch：转换器的类型优先级是 INFERRED，
        // 目标类型只从本方法的签名推断，消息头里的 __TypeId__ 一概不看（见 RabbitConfig）
        log.debug("收到待发邮件 messageId={}", dispatch.messageId());
        smtpMailService.send(dispatch.to(), dispatch.subject(), dispatch.textBody());
    }
}
