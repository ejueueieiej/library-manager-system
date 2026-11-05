package org.libaray_manager_system.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReaderFileSelectDto {
    private Integer pageNum = 1;    // ★ 默认值：别让 null 溜进 (pageNum-1)*pageSize
    private Integer pageSize = 10;  // ★ 同上，否则 NPE → 500
    private String title = "";
}