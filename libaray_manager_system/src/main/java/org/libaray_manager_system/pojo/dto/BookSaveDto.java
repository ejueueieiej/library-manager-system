package org.libaray_manager_system.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookSaveDto {
    /** 只在新增时由 MyBatis 回填（useGeneratedKeys），请求体里传了也不会被使用 */
    private Integer id;
    private String isbn;
    private String name;
    private String author;
    private String publisher;
    private LocalDate publishDate;
    private Integer categoryId;
    private BigDecimal price;
    private Integer totalNum;
    private String coverUrl;
    private String intro;
    private Integer status;
}
