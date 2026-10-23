package org.libaray_manager_system.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 返回给前端的用户信息
 *
 * ⚠️ 这个类【故意没有 password 字段】——
 *    这样就不可能"忘了 setNull"把密码哈希泄露给前端，是从结构上堵住，而不是靠记性。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserVo {
    private Integer id;
    private String username;
    private String realName;
    private String phone;
    private String email;
    /** ADMIN / USER —— 前端靠它决定显示哪些按钮 */
    private String role;
    /** 1 正常 / 0 禁用 */
    private Integer status;
    private String avatar;
    private LocalDateTime createTime;
}
