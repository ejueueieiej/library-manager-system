package org.libaray_manager_system.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BorrowSummaryVo {
    private Integer borrowingCount;// 在借（status=1）
    private Integer overdueCount;// 其中逾期
    private Integer totalCount;// 累计（status in 1,2，不含已取消）
}
