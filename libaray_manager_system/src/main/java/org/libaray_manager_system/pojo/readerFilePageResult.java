// ========== ⑨ pojo/readerFilePageResult.java ==========
package org.libaray_manager_system.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.libaray_manager_system.pojo.vo.ReaderFileVo;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class readerFilePageResult {
    private Integer total;
    private List<ReaderFileVo> list;
}