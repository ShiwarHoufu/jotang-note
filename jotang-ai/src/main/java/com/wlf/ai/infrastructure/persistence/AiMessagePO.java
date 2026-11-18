package com.wlf.ai.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** ai_message 表映射。 */
@Getter
@Setter
@TableName("ai_message")
public class AiMessagePO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String conversationId;

    /** {@code USER} / {@code ASSISTANT} */
    private String role;

    private String content;

    /** 由建表语句的 {@code DEFAULT CURRENT_TIMESTAMP} 兜底，Java 侧不赋值 */
    private LocalDateTime createdAt;
}
