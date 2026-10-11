package com.wlf.mail;

import com.wlf.config.RabbitConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 把发信变成「投一条消息」：调用方立刻返回，真正发信由 {@link MailDispatchConsumer} 去做。
 * 见《概要设计》§6.6。
 *
 * <h2>为什么带 {@code @Primary}，而不是让两个实现互斥</h2>
 *
 * <p>{@link SmtpMailService} <b>始终</b>是 Bean（消费者要用它去发信），所以任意时刻
 * <b>存在两个</b> {@code MailService} 实现，按接口注入必须有个说法：
 *
 * <ul>
 *   <li>{@code jotang.mail.mode=smtp}（默认）下本类不注册，接口只有一个候选
 *       {@code SmtpMailService}，无歧义</li>
 *   <li>{@code mode=queue} 下本类注册并带 {@code @Primary}，指向自己</li>
 * </ul>
 *
 * <p>两种情形都<b>确定性解析</b>，不依赖 Bean 名字典序这类隐性规则。
 * 换成「给 {@code SmtpMailService} 也加一个 {@code havingValue="smtp"} 条件」同样能互斥，
 * 但那样消费者在 queue 模式下就没有可注入的实现，得再拆一层——不如现在这样直白。
 *
 * <h2>异常原样外抛，不在这里降级</h2>
 *
 * <p>Broker 连不上时 {@code convertAndSend} 抛 {@code AmqpException}，
 * 本类<b>不吞</b>：要不要让「发不出信」影响这次请求，是调用方
 * （{@code PasswordResetService}）的决定。这与 {@code StorageService} 的规矩一致——
 * 基础设施层只报告事实，业务层定策略。
 */
@Slf4j
@Component
@Primary
@ConditionalOnProperty(name = "jotang.mail.mode", havingValue = "queue")
public class QueuedMailService implements MailService {

    private final RabbitTemplate rabbitTemplate;

    public QueuedMailService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
        // 启动横幅：它是「当前走队列」的唯一明显迹象。没看到这行就是还在 smtp 模式
        // （见 application.yaml 里 jotang.mail.mode 那段）
        log.info("邮件投递方式：RabbitMQ 队列 queue={} routingKey={}",
                RabbitConfig.PASSWORD_RESET_QUEUE, RabbitConfig.MAIL_ROUTING_KEY);
    }

    @Override
    public void send(String to, String subject, String textBody) {
        MailDispatch dispatch = new MailDispatch(UUID.randomUUID().toString(), to, subject, textBody);
        rabbitTemplate.convertAndSend(RabbitConfig.MAIL_EXCHANGE, RabbitConfig.MAIL_ROUTING_KEY, dispatch,
                message -> {
                    // 非持久化：明文令牌不落 Broker 的磁盘。注意它只挡「Broker 重启」这一种，
                    // 挡不住「消息还在队列里时被人从管理台看」——那半边靠的是 5672/15672
                    // 不对公网开放（§8.3），以及消息只在队列里待最多 10 分钟
                    message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.NON_PERSISTENT);
                    return message;
                });

        // convertAndSend 返回只代表「写进了 socket」，不代表 Broker 收下了。
        // 要真知道得开 publisher-confirm-type 并等 ConfirmCallback 回调——
        // 但那只能在「邮箱存在」这条分支上等，等于把刚消掉的耗时差搬回来，
        // 所以刻意不做（§6.6、§8.4）
        log.debug("重置邮件已投递 messageId={} to={}", dispatch.messageId(), to);
    }
}
