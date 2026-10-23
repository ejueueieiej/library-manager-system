package org.libaray_manager_system.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Book {
    private Integer id;
    private String isbn;
    private String name;
    private String author;
    private String publisher;
    private LocalDate publishDate;
    private Integer categoryId;
    private String categoryName;
    private BigDecimal price;
    private Integer totalNum;
    private BigDecimal availableNum;
    private String coverUrl;
    private String intro;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}
