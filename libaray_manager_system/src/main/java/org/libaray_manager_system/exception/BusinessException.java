package org.libaray_manager_system.exception;

/**
 * 业务异常
 *
 * <p>专门用来表达「用户操作不合法」这类可预期的失败，例如：
 * 「该 ISBN 已存在」「分类不存在」「书名不能为空」。
 *
 * <p>它和普通异常的区别在于：这类失败是<b>业务规则</b>决定的，不是程序 bug，
 * 所以应该返回 HTTP 200 + {@code code:0}，让前端拦截器统一弹窗提示用户，
 * 而不是甩一个 500 吓人。
 *
 * <p>统一由 {@link GlobalExceptionHandler} 接住并转成 {@code Result.error(msg)}。
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
