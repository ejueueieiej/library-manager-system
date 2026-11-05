package org.libaray_manager_system.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.libaray_manager_system.pojo.vo.BorrowVo;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BorrowPageResult {
    private Integer total;
    private List<BorrowVo> list;
}
