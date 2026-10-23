package org.libaray_manager_system.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)      // 只能标在方法上
@Retention(RetentionPolicy.RUNTIME)  // 运行时必须能读到，否则拦截器看不到
public @interface RequireAdmin {
}