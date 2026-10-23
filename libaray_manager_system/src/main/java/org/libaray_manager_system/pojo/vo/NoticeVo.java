package org.libaray_manager_system.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
// 通知返回VO
public class NoticeVo {
    private Long id;
    private String title;
    private String summary;// 通知摘要
    private Integer type;
    private Integer isTop;
    private Integer status;
    private String publisherName;
    private LocalDateTime publishTime;
    private Integer isRead;// 是否已读
}
