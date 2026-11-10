package org.libaray_manager_system.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "library.ai")
@Data
@Component
public class AiProperties {
    /** 总开关：关掉后接口直接抛「AI 功能未开启」 */
    private boolean enabled = true;
    /** 演示模式：不调真实模型，返回固定话术 */
    private boolean mock = false;
    /** 只用于展示和落库；真正的模型由 spring.ai.deepseek.chat.model 决定 */
    private String model = "deepseek-flash";
    /** 带多少条历史 */
    private int maxContextMessages = 20;
    /** 历史总字符预算 */
    private int maxContextChars = 6000;
    /** 每人每天次数 */
    private int dailyQuota = 100;
    /** 单次 SSE 最长毫秒数 */
    private long streamTimeoutMs = 180_000L;
    /** 关掉后就不再注册 @Tool（模型不支持工具调用时用） */
    private boolean toolsEnabled = true;
}
