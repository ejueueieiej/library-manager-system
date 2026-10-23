package org.libaray_manager_system.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
// 通知保存DTO
public class NoticeSaveDto {
    private String title;
    private String content;
    private Integer type;
    private Integer isTop;
    private Integer status;
}
