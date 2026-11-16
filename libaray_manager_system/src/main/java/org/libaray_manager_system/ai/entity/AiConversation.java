package org.libaray_manager_system.ai.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiConversation {
    private Long id;
    private Integer userId;
    private String title;
    private String model;
    private Integer messageCount;
    private LocalDateTime lastMessageTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
