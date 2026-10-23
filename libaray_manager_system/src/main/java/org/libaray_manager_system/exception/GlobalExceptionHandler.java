package org.libaray_manager_system.exception;

import lombok.extern.slf4j.Slf4j;
import org.libaray_manager_system.pojo.Result;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理器
 *
 * <p>它解决一个很实际的问题：Controller 里 {@code throw} 出来的异常，
 * Spring 默认会变成 500 错误页，前端拦截器只看到「服务器异常」，
 * 拿不到我们想告诉用户的那句提示语。
 *
 * <p>这里把三类异常统一转成项目约定的 {@code {code, msg, data}} 返回体：
 *
 * <table border="1">
 *   <tr><th>异常</th><th>HTTP</th><th>返回</th></tr>
 *   <tr><td>{@link BusinessException}（自己抛的业务失败）</td><td>200</td><td>{@code code:0} + 具体提示</td></tr>
 *   <tr><td>{@link DuplicateKeyException}（唯一索引冲突）</td><td>200</td><td>{@code code:0} + 「该 ISBN 已存在」</td></tr>
 *   <tr><td>请求体格式不对（如日期写成了乱码）</td><td>200</td><td>{@code code:0} + 「请求数据格式不正确」</td></tr>
 *   <tr><td>其它一切（真正的程序 bug）</td><td>500</td><td>{@code code:0} + 「服务器异常」</td></tr>
 * </table>
 *
 * <p>⚠️ 业务失败必须返回 HTTP 200，只有真正的服务器异常才用 500 ——
 * 否则前端的 axios 拦截器会走错分支（见接口文档 0.2 节）。
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    /**
     * 业务异常：可以预期的失败，HTTP 200 + code 0，msg 直接给用户看。
     */
    @ExceptionHandler(BusinessException.class)
    public Result handleBusinessException(BusinessException e) {
        log.warn("业务校验未通过：{}", e.getMessage());
        return new Result().error(e.getMessage());
    }

    /**
     * 唯一索引冲突兜底。
     *
     * <p>Service 里已经先查过一次 ISBN 了，但「先查再插」在并发下是有缝隙的
     * （两个请求同时查、都说没重复、然后一起插）。
     * 数据库的 uk_book_isbn 唯一索引是最后一道防线，这里负责把它翻译成人话。
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result handleDuplicateKeyException(DuplicateKeyException e) {
        log.warn("唯一索引冲突：{}", e.getMessage());
        return new Result().error("该 ISBN 已存在");
    }

    /**
     * 请求体无法反序列化（比如 publishDate 传了非法格式）。
     *
     * <p>这属于「客户端传错了」，不是服务器炸了，所以给 200 + 明确提示，
     * 免得用户看到的是一句没头没脑的「服务器异常」。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体格式错误：{}", e.getMessage());
        return new Result().error("请求数据格式不正确");
    }

    /**
     * 请求的接口不存在（404）
     *
     * ⚠️ 这个处理器【必须加】，否则会被坑很久：
     *
     * Spring Boot 3.2 以后，访问一个不存在的路径会抛 NoResourceFoundException，
     * 而它本身是个 Exception —— 会被下面那个 @ExceptionHandler(Exception.class)
     * 兜底接住，于是【任何写错的 URL 都返回 500「服务器异常」】。
     *
     * 排查时的表现是：前端报"服务器异常"，去后端看日志也只有"服务器异常"，
     * 完全想不到真实原因是「路径打错了 / 接口还没写」。
     * （我们真踩过：前端点「退出登录」，后端还没有 /auth/logout，返回的就是这句）
     *
     * 加上它之后，同样的情况会老老实实返回 404 +「接口不存在」，一眼看懂。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result handleNotFound(NoResourceFoundException e) {
        log.warn("请求的接口不存在：{}", e.getMessage());
        return new Result().error("接口不存在，请检查请求路径");
    }

    /**
     * 兜底：没预料到的异常。
     *
     * <p>日志里打完整堆栈方便排查，但返回给用户的只有一句通用提示 ——
     * 不要把堆栈细节暴露给前端。
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result handleException(Exception e) {
        log.error("服务器异常", e);
        return new Result().error("服务器异常，请联系管理员");
    }
}
