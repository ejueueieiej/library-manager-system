package org.libaray_manager_system.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类（2.0）—— 基于 <b>jjwt 0.12.6</b>
 * 关于依赖：0.12.x 必须引三个 artifact —— jjwt-api / jjwt-impl / jjwt-jackson。
 */
public class JwtUtil {

    /**
     * 签名密钥。
     * ⚠️ 两个提醒：
     *   1) 长度必须 >= 32 字节（HS256 要求），这个字符串有 53 字节，够用；
     *      你要是改成短字符串，启动就会抛 WeakKeyException。
     *   2) 生产环境要挪到 application.yml 或环境变量里，不要写死在代码中 ——
     *      这份代码进了 Git，凡是有仓库权限的人都能伪造 token。
     *      token的结构为：header+payload(内含claim)+signature
     */
    private static final String SECRET = "library-manager-2.0-secret-key-change-me-in-production";
    // 生成密钥对，hmac-sha256 算法
    private static final SecretKey KEY =
            Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    /** 有效期：2 小时 */
    private static final long EXPIRE_MILLIS = 2 * 60 * 60 * 1000L;

    public static String createToken(Long userId, String username, String role) {
        return Jwts.builder()
                .claim("userId", userId)
                .claim("username", username)
                .claim("role", role)
                .issuedAt(new Date())
                // 有效期：2 小时，currentTimeMillis 是当前时间戳
                .expiration(new Date(System.currentTimeMillis() + EXPIRE_MILLIS))
                .signWith(KEY)
                .compact();
    }
    /**
     * 解析并校验 token（验签 + 验过期，一次做完）
     *
     * @return 解析出来的 Claims；任何问题（格式错 / 签名被改 / 已过期）都返回 null，
     *         由调用方（拦截器）统一处理成 HTTP 401
     *
     * 下面这个 catch 覆盖了 jjwt 会抛的全部异常，它们都继承自 JwtException：
     *   ExpiredJwtException    已过期        ← 就是 hutool 需要我们手工校验的那个
     *   SignatureException     签名不匹配（token 被改过）
     *   MalformedJwtException  格式不对（比如根本不是三段式）
     *   UnsupportedJwtException 算法不支持
     * 另外 IllegalArgumentException 是"传了个空串"这类参数问题，也一并当无效处理。
     */
    public static Claims parseAndVerify(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            return Jwts.parser()
                    // 0.12.x 用 verifyWith(SecretKey)；
                    // 0.11 及以前叫 setSigningKey(...)
                    //为解析令牌提供密钥
                    .verifyWith(KEY)
                    .build()//构建解析器
                    //对token解析
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 取 userId
     *
     * 这里刻意用 Number 而不是 Long：JSON 里的整数可能被反序列化成 Integer 或 Long，
     * 拿 Number 再 longValue() 出来，两种情况都不会出错。
     * （jjwt 内部其实也做了 Integer→Long 的兼容，但显式写出来更稳、意图也更清楚）
     */
    public static Long getUserId(Claims claims) {
        Number n = claims.get("userId", Number.class);
        return n == null ? null : n.longValue();
    }

    public static String getUsername(Claims claims) {
        return claims.get("username", String.class);
    }

    public static String getRole(Claims claims) {
        return claims.get("role", String.class);
    }
}
