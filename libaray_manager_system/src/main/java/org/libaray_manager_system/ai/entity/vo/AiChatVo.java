package org.libaray_manager_system.ai.entity.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiChatVo {
    @NonNull private String content;
    @NonNull private String model;
    private Integer promptToken;//输入token
    private Integer completionToken;//输出token
    private Long elapsedMs;//服务耗时
    private boolean mock;
}
