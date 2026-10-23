package org.libaray_manager_system.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.libaray_manager_system.pojo.entity.Book;
import org.libaray_manager_system.pojo.vo.UserVo;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class bookpageResult {
    private Integer total;
    private List<Book> list;
}
