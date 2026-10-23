package org.libaray_manager_system.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookQuerySelectDto {
    private Integer pageNum=1;
    private Integer pageSize=10;
    private String name;
    private String author;
    private Integer categoryId;
    private Integer status;
    private String isbn;
}
