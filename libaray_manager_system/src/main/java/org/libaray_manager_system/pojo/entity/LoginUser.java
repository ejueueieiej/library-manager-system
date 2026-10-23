package org.libaray_manager_system.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 当前登录用户（2.0）
 *
 * <p>⚠️ 它<b>不是数据库实体</b>，不对应任何一张表。
 * 它是 {@link org.libaray_manager_system.interceptor.AuthInterceptor} 解析 token 之后，
 * 塞进 request 的「当前用户凭证」，Controller 通过
 * {@code @RequestAttribute("loginUser")} 取出来。
 *
 * <p>⚠️ 故意<b>不带 password</b>：密码哈希没必要在 request 作用域里到处流转，
 * 能不带就不带。真需要完整用户信息（手机号、邮箱、头像…）时，
 * 拿 id 回库查一次，见 {@code LoginService.GetVOById(Integer)}。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginUser {

    /** 用户主键 —— 有了它才能按 id 回库查完整信息 */
    private Integer id;

    private String username;

    private String realName;

    /** ADMIN 管理员 / USER 普通读者 */
    private String role;

    /**
     * 是不是管理员
     *
     * <p>⚠️ 常量写在前面<b>不是笔误</b>：
     * {@code "ADMIN".equals(role)} 在 role 为 null 时安全返回 false，
     * 而 {@code role.equals("ADMIN")} 会直接抛 NullPointerException。
     */
    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
