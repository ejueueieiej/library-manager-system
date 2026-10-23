package org.libaray_manager_system.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 系统用户（对应 sys_user 表）
 *
 * 注意几个类型必须和数据库列对齐，否则 MyBatis 映射会静默失效（不报错，但值一直是 null）：
 *   status  -> 数据库是 TINYINT，所以用 Integer，不能用 String
 *   avatar  -> 列名是 avatar（原来写成 avater 是拼错了，永远查不出值）
 *
 * 另外这里【不应该有 token 字段】：token 不是数据库里的列，它是登录时现签出来的，
 * 放在 LoginVo 里返回就够了。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class sysUser {
    private Integer id;
    private String username;
    /** BCrypt 哈希，不是明文 */
    private String password;
    private String realName;
    private String phone;
    private String email;
    /** ADMIN 管理员 / USER 普通用户 */
    private String role;
    /** 1 正常 / 0 禁用 */
    private Integer status;
    private String avatar;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
