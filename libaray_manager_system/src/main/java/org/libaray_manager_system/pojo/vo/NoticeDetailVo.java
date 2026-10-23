package org.libaray_manager_system.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
// 通知详情返回VO
public class NoticeDetailVo {
    private Long id;
    private String title;
    private String content;// 通知内容
    private Integer type;
    private Integer isTop;
    private Integer status;
    private String publisherName;
    private LocalDateTime publishTime;
    private LocalDateTime updateTime;// 更新时间
    private Integer isRead;// 是否已读
}
