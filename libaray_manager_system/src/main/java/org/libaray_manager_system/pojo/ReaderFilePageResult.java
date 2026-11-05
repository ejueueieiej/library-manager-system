// ========== ⑨ pojo/ReaderFilePageResult.java ==========
package org.libaray_manager_system.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.libaray_manager_system.pojo.vo.ReaderFileVo;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReaderFilePageResult {
    private Integer total;
    private List<ReaderFileVo> list;
}