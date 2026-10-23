package org.libaray_manager_system.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
// 通知查询参数
public class NoticeSelectDto {
    private Integer pageNum  = 1;    // ★ 给默认值，别让 null 溜进 (pageNum-1)*pageSize
    private Integer pageSize = 10;   // ★ 同上，否则 NPE → 500
    private String  keyword = "";
    private Integer type;
    private Integer onlyTop;
    private Integer status;
}
