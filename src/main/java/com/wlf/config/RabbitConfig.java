package com.wlf.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.JacksonJavaTypeMapper;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 拓扑与消息格式。见《概要设计》§6.6。
 *
 * <h2>为什么是 direct exchange 而不是默认的 exchange</h2>
 *
 * <p>只有一条链路、一个路由键，用默认 exchange（每个队列自动绑定自己的名字）也够。
 * 但仍然显式 declare 一个 direct exchange：将来加第二条异步链路时，路由关系是现成的，
 * 而「默认 exchange 不能显式绑定」（broker 会拒绝）是个改起来才发现、且要先清理已有队列的坑。
 *
 * <h2>为什么这些名字是常量而不是配置键</h2>
 *
 * <p>拓扑名是<b>代码契约</b>：发布端与消费端各写一份，写错就是「消息发出去没人收」或者
 * 「队列声明了两遍不一致」。做成配置键只是多给它一个被改歪的机会——
 * 而它并没有需要按环境变化的地方。与 Redis 那个 {@code note:meta:} 前缀同理。
 *
 * <h2>本类不加 {@code @ConditionalOnProperty}</h2>
 *
 * <p>它只声明 Bean，不建立连接：{@code RabbitAdmin} 的声明动作挂在连接监听上，
 * 只有真的连上 Broker 才会执行。所以「没起 Docker 也不影响其余功能」这件事不需要在这里门控——
 * 需要门控的是<b>会主动连过去的东西</b>（{@code QueuedMailService} 与 {@code MailDispatchConsumer}，
 * 它们各自带着条件）。见 {@code application.yaml} 里 {@code jotang.mail.mode} 那段的说明。
 */
@Slf4j
@Configuration
public class RabbitConfig {

    /** 邮件交换机。direct，durable。 */
    public static final String MAIL_EXCHANGE = "jotang.mail.exchange";

    /** 重置邮件的路由键。目前是唯一的键。 */
    public static final String MAIL_ROUTING_KEY = "mail.password-reset";

    /** 重置邮件的工作队列。名字里的 {@code .q} 是队列后缀，与死信队列的 {@code .dlq} 区分。 */
    public static final String PASSWORD_RESET_QUEUE = "jotang.mail.password-reset.q";

    /** 死信交换机。独立的 exchange，不挂在 {@link #MAIL_EXCHANGE} 上。 */
    public static final String MAIL_DLX = "jotang.mail.dlx";

    /** 死信路由键 */
    public static final String MAIL_DEAD_ROUTING_KEY = "mail.password-reset.dead";

    /**
     * 死信队列。<b>刻意不挂消费者</b>——它是取证面，不是重试池。
     *
     * <p>挂上消费者就等于「把这些必然失败的邮件再打一遍」，而它们失败的原因（SMTP 凭据错、
     * 收件人不存在、正文格式变了）需要人看一眼才知道。管理台上看到它非空时的处置见 §7.3。
     */
    public static final String PASSWORD_RESET_DLQ = "jotang.mail.password-reset.dlq";

    @Bean
    public DirectExchange mailExchange() {
        // durable=true：Broker 重启后交换机还在。autoDelete=false：没有绑定也不自动摘除
        return new DirectExchange(MAIL_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange mailDeadLetterExchange() {
        return new DirectExchange(MAIL_DLX, true, false);
    }

    /**
     * 工作队列。四个参数各有作用，<b>缺一不可</b>：
     *
     * <ul>
     *   <li>{@code x-dead-letter-exchange} + {@code x-dead-letter-routing-key}：没有它，
     *       {@code default-requeue-rejected=false} 就是<b>直接把消息扔掉</b>——
     *       重试耗尽的邮件会无声消失，而不是落进死信队列</li>
     *   <li>{@code x-message-ttl} = 10 分钟：明文令牌在 Broker 里的<b>最长驻留时间</b>。
     *       令牌本身 30 分钟过期，这里再短一截，是让「积压太久、用户早就重新申请过了」
     *       那些消息自己消失，不必真发出去</li>
     *   <li>{@code x-max-length}：兜底。Broker 长时间不可用、或消费者一直失败时，
     *       不让队列无界增长把 broker 撑爆。满队列时新消息从队头挤掉旧的——对本场景是对的，
     *       因为「最新的那封邮件」才是用户需要的</li>
     * </ul>
     *
     * <p><b>⚠ 队列参数创建即固定。</b>改了这里的任何一个参数，而 Broker 上已经有同名队列时，
     * 声明会被拒绝（{@code PRECONDITION_FAILED}），消费容器直接起不来。开发期先删：
     * <pre>docker exec rabbitmq rabbitmqctl delete_queue jotang.mail.password-reset.q</pre>
     * 这条坑记在 §7.4——它和「加索引不能只重跑一遍迁移脚本」是同一类：改了不生效，还报一个
     * 看起来与改动无关的错。
     */
    @Bean
    public Queue passwordResetQueue() {
        return QueueBuilder.durable(PASSWORD_RESET_QUEUE)
                .deadLetterExchange(MAIL_DLX)
                .deadLetterRoutingKey(MAIL_DEAD_ROUTING_KEY)
                .ttl(600_000)
                .maxLength(10_000)
                .build();
    }

    /**
     * 死信队列。TTL 1 小时后自灭——<b>取证明不等于留证据</b>。
     *
     * <p>它与工作队列的 {@code x-message-ttl} 是两个独立的上限：消息在队列里最多待 10 分钟，
     * 进了死信队列再最多待 1 小时。加起来就是「一封发不出去的邮件，连同它正文里的明文令牌，
     * 在 Broker 里最多存在 70 分钟」。这个数字要写进 §8.3。
     *
     * <p>不给死信队列再挂死信：那会变成一条没有出口的链。到点自灭是它的终点。
     */
    @Bean
    public Queue passwordResetDeadLetterQueue() {
        return QueueBuilder.durable(PASSWORD_RESET_DLQ)
                .ttl(3_600_000)
                .build();
    }

    @Bean
    public Binding passwordResetBinding(Queue passwordResetQueue, DirectExchange mailExchange) {
        return BindingBuilder.bind(passwordResetQueue).to(mailExchange).with(MAIL_ROUTING_KEY);
    }

    @Bean
    public Binding passwordResetDeadLetterBinding(Queue passwordResetDeadLetterQueue,
                                                  DirectExchange mailDeadLetterExchange) {
        return BindingBuilder.bind(passwordResetDeadLetterQueue)
                .to(mailDeadLetterExchange)
                .with(MAIL_DEAD_ROUTING_KEY);
    }

    /**
     * 消息格式：JSON，用 <b>Jackson 3</b>。
     *
     * <p>类名是 {@code JacksonJsonMessageConverter}，<b>不是</b>网上到处写的
     * {@code Jackson2JsonMessageConverter}——后者吃的是 Jackson 2 的 {@code ObjectMapper}
     * （{@code com.fasterxml.jackson}），而本项目主栈是 Boot 4 的 Jackson 3
     * （{@code tools.jackson}）。理由与 {@code RedisConfig} 选
     * {@code GenericJacksonJsonRedisSerializer} 逐字相同：Jackson 2 在这里只是
     * {@code jjwt-jackson} 捎进来的、{@code runtime} 作用域，编译期根本不可见。
     *
     * <h2>为什么这里不开 default typing，而 Redis 那边开了</h2>
     *
     * <p>两套规则<b>各自成立，不要强行统一</b>。
     *
     * <p>{@code RedisConfig} 开着 default typing（JSON 里带 {@code @class}），是因为
     * {@code RedisTemplate} 的值类型是 {@code Object}，反序列化时拿不到目标类型——不开就只会
     * 读回 {@code LinkedHashMap}。开了就等于让<b>数据自己声明要变成哪个类</b>，所以那边
     * 必须配一个收紧的类型校验器。
     *
     * <p>MQ 这边<b>恰恰相反</b>：每个队列要反序列化成什么是<b>代码里写死</b>的
     * （监听方法的参数类型），不需要、也不该让消息来声明。所以这里把类型优先级设成
     * {@link JacksonJavaTypeMapper.TypePrecedence#INFERRED}：<b>目标类型只从监听方法签名推断，
     * 消息头里的 {@code __TypeId__} 一概不看</b>。这样即使有人能往队列里塞一条消息，
     * 也无法让它构造出监听方法参数类型之外的任何对象——难点从「配置一份可信包名单」
     * 降级成「结构上不可能」。
     *
     * <p>声明成一个 {@code MessageConverter} Bean 就够了：Boot 会把它同时挂到自动配置的
     * {@code RabbitTemplate} 与监听容器工厂上，不必自己 new 工厂。
     */
    @Bean
    public MessageConverter mailMessageConverter() {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
        converter.setTypePrecedence(JacksonJavaTypeMapper.TypePrecedence.INFERRED);
        return converter;
    }

    /**
     * 消息路由不到任何队列时记一条错误日志。
     *
     * <p>配合 {@code spring.rabbitmq.template.mandatory=true}：开了 mandatory，
     * Broker 会把无法路由的消息退回而不是静默丢弃；没有这个回调，退回的消息只会在
     * DEBUG 级别被提一句，等于还是没看见。
     *
     * <p>本项目的路由关系是代码里写死的（队列与绑定都在本类声明），所以走到这里只可能是
     * 有人手工动过 Broker，或者声明失败过。它出现的频率应当恒为 0。
     *
     * <p><b>不记正文</b>：正文里的链接含明文令牌。
     */
    @Bean
    public RabbitTemplateCustomizer mailReturnsLogger() {
        return template -> template.setReturnsCallback(returned ->
                log.error("邮件消息无法路由，已被 Broker 退回：exchange={} routingKey={} reply={}",
                        returned.getExchange(), returned.getRoutingKey(), returned.getReplyText()));
    }
}
