package org.libaray_manager_system.service;

import org.libaray_manager_system.pojo.dto.BookQuerySelectDto;
import org.libaray_manager_system.pojo.dto.BookSaveDto;
import org.libaray_manager_system.pojo.entity.Category;
import org.libaray_manager_system.pojo.BookPageResult;
import org.libaray_manager_system.pojo.entity.Book;


import java.util.List;

public interface BookService {
    List<Category> GetAllCategories();


    BookPageResult GetAllBooks(BookQuerySelectDto bookQuerySelectDto);

    Book GetBookById(Integer id);

    /** 新增图书。校验不通过时抛 BusinessException（如「该 ISBN 已存在」） */
    void AddBook(BookSaveDto bookSaveDto);

    void UpdateBook(Integer id, BookSaveDto bookSaveDto);

    void DeleteBook(Integer id);

    String UpdateBookStatus(Integer id, Integer status);

}
