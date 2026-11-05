package org.libaray_manager_system.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.libaray_manager_system.pojo.vo.NoticeVo;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoticePageResult {
    private Integer total;
    private List<NoticeVo> list;
}
