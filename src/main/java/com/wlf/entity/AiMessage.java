package com.wlf.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * ai_message 表。一轮问答两行（用户一条、助手一条）。
 *
 * <p>历史按 {@code id} 正序读出来就是模型要的顺序，不做裁剪。
 */
@Getter
@Setter
@TableName("ai_message")
public class AiMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 消息归属者。冗余在这里，让「取自己的会话」是一次带 {@code user_id} 的等值查询，不必 JOIN */
    private Long userId;

    /** 服务端生成的 UUID，也就是对外暴露的 conversationId */
    private String conversationId;

    /** {@code USER} / {@code ASSISTANT}，取值见 {@link com.wlf.ai.AiMessageRole} */
    private String role;

    private String content;

    /** 由建表语句的 {@code DEFAULT CURRENT_TIMESTAMP} 兜底，Java 侧不赋值 */
    private LocalDateTime createdAt;
}
