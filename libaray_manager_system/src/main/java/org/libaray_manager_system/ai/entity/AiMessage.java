package org.libaray_manager_system.ai.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiMessage {
    private Long id;
    private Long conversationId;
    private Integer userId;
    private String role;
    private String content;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer status;
    private LocalDateTime createTime;
}