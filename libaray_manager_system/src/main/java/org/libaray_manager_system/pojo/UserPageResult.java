package org.libaray_manager_system.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.libaray_manager_system.pojo.vo.UserVo;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserPageResult {
    private Integer total;
    private List<UserVo> list;
}
