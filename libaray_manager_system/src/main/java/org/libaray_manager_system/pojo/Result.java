package org.libaray_manager_system.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result {
    private Integer code;
    private String msg;
    private Object data;

    public Result success(Object data) {
        Result result = new Result();
        result.code=1;
        result.msg="success";
        result.data=data;
        return result;
    }
    public Result success() {
        Result result = new Result();
        result.code=1;
        result.msg="success";
        return result;
    }

    /** 成功，但自定义提示语（如「新增成功」） */
    public Result success(String msg, Object data) {
        Result result = new Result();
        result.code=1;
        result.msg=msg;
        result.data=data;
        return result;
    }
    public Result error(String msg) {
        Result result = new Result();
        result.code=0;
        result.msg=msg;
        return result;
    }
}
