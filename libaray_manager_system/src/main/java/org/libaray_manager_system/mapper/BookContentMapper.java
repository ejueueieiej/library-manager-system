package org.libaray_manager_system.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.libaray_manager_system.pojo.entity.BookContent;

@Mapper
public interface BookContentMapper {
    BookContent GetByBookId(@Param("bookId") Integer bookId);
    int Save(BookContent content);
    void DeleteByBookId(@Param("bookId") Integer bookId);
}
