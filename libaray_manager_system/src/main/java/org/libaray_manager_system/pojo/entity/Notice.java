package org.libaray_manager_system.pojo.entity;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Notice {
    private Long id;
    private String title;
    private String content;
    private Integer type;
    private Integer isTop;
    private Integer status;//1-已发布 0-已下架
    private Long publisherId;
    private LocalDateTime publishTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
