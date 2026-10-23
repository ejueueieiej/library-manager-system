package org.libaray_manager_system.interceptor;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.libaray_manager_system.annotation.RequireAdmin;
import org.libaray_manager_system.mapper.LoginMapper;
import org.libaray_manager_system.pojo.entity.LoginUser;
import org.libaray_manager_system.pojo.entity.sysUser;
import org.libaray_manager_system.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录 + 权限拦截器（2.0）
 *
 * <p><b>它在请求链路里的位置：</b>
 * <pre>
 *   浏览器 ──► Tomcat ──► DispatcherServlet ──► 【preHandle】 ──► Controller
 *                                                    ↑ token 只在这里解析一次
 * </pre>
 *
 * <p><b>一次请求做 4 件事：</b>
 * <ol>
 *   <li>从 {@code Authorization: Bearer xxx} 头里取出 token（剥掉前缀）</li>
 *   <li>验签 + 验过期，失败 → 401</li>
 *   <li>回库核对账号还在、没被禁用，失败 → 401</li>
 *   <li>方法上有 {@code @RequireAdmin} 就查角色，不是 ADMIN → 403</li>
 * </ol>
 *
 * <p><b>为什么第 3 步要回库？</b>
 * token 签发后 2 小时内一直有效。如果这期间账号被管理员禁用或删除了，
 * 光看 token 是发现不了的 —— 必须回库核对一次状态。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    /**
     * 塞进 request 的属性名。
     * Controller 用 {@code @RequestAttribute("loginUser")} 取，两边必须一致，
     * 所以抽成常量 —— 字符串写错是这类代码最常见的低级错误。
     */
    public static final String LOGIN_USER_ATTR = "loginUser";

    @Autowired
    private LoginMapper loginMapper;

    // 在请求处理前执行 - 返回 true 放行，false 拦截
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        // ---------- 0. 跨域预检请求（OPTIONS）直接放行 ----------
        // 浏览器在发真正的跨域请求前，会先发一个不带 Authorization 的 OPTIONS 探路。
        // 这个探测请求本来就没有 token，要是被拦成 401，浏览器会直接判定「跨域失败」，
        // 真正的请求根本发不出来 —— 而且报错信息会把你往 CORS 配置上带，很难查。
        // （本项目走 Vite / nginx 同源代理，正常不触发；但兜住它零成本。）
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // ---------- 1. 非 Controller 方法直接放行 ----------
        // ⚠️ 这几行是【承重墙】，千万别删：
        //   封面图 /uploads/xxx.jpg、favicon、index.html 走的都是「静态资源处理器」，
        //   它们的 handler 不是 HandlerMethod。如果对它们也要求 token，
        //   整个网站的图片和静态文件会全部 401。
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        // ---------- 2. 接收 Authorization 头，剥掉 "Bearer " 前缀 ----------
        // 头里收到的是："Bearer eyJhbGciOiJIUzM4NCJ9..."
        //                     ↑ 这 7 个字符（B-e-a-r-e-r + 空格）不是 token 的一部分
        String headerToken = request.getHeader("Authorization");
        String token = (headerToken != null && headerToken.startsWith("Bearer "))
                ? headerToken.substring(7) : headerToken;

        // ---------- 3. 验签 + 验过期 ----------
        // JwtUtil 把「签名不对 / 已过期 / 格式乱 / 空串」统一成返回 null，
        // 所以这里只判一次 null 就够了，不用 try/catch。
        Claims claims = JwtUtil.parseAndVerify(token);
        if (claims == null) {
            return reject(response, 401, "未登录或登录已过期");
        }

        // ---------- 4. 回库核对账号状态 ----------
        // ⚠️ null 判断必须写在最前面！|| 是从左往右算的，靠它短路：
        //   写成 sysuser.getStatus() == 0 || sysuser == null 的话，
        //   sysuser 为 null 时会先执行前半句 → NullPointerException。
        // ⚠️ 第二个 null 判断也不能省：status 是 Integer，
        //   和 int 字面量 0 比较会【自动拆箱】，数据库里为 NULL 时照样 NPE。
        sysUser sysuser = loginMapper.GetByUsername(JwtUtil.getUsername(claims));
        if (sysuser == null || sysuser.getStatus() == null || sysuser.getStatus() == 0) {
            return reject(response, 401, "账号不存在或已被禁用");
        }

        // ---------- 5. 把当前用户塞进loginUser，供 Controller 使用 ----------
        // 只装 4 个字段，【不带 password】——
        // 密码哈希没必要在 request 作用域里到处流转，能不带就不带。
        LoginUser loginUser = new LoginUser(
                sysuser.getId(),
                sysuser.getUsername(),
                sysuser.getRealName(),
                sysuser.getRole());
        request.setAttribute(LOGIN_USER_ATTR, loginUser);

        // ---------- 6. 角色校验 ----------
        // 只有标了 @RequireAdmin 的方法才需要管理员；
        // 没标的接口（查列表、看详情）登录即可，普通读者也能用。
        if (handlerMethod.getMethodAnnotation(RequireAdmin.class) != null && !loginUser.isAdmin()) {
            // 403 =「我知道你是谁，但你没权限干这个」，区别于 401「我不知道你是谁」。
            // 前端对这两个的处理完全不同：
            //   401 → 清登录态、跳登录页；403 → 只弹提示，绝不能跳转。
            return reject(response, 403, "没有操作权限");
        }

        return true;
    }

    private boolean reject(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        // ⚠️ charset 必须写，否则中文提示语到浏览器会变乱码
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":0,\"msg\":\"" + message + "\",\"data\":null}");
        return false;
    }
}
