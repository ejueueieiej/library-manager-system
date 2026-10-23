package org.libaray_manager_system.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.libaray_manager_system.pojo.dto.BookQuerySelectDto;
import org.libaray_manager_system.pojo.dto.BookSaveDto;
import org.libaray_manager_system.pojo.entity.Book;
import org.libaray_manager_system.pojo.entity.Category;

import java.util.List;

@Mapper
public interface BookMapper {
    //@Select("select * from book_category order by sort")
    List<Category> GetAllCategories();

    Integer GetTotal(BookQuerySelectDto bookQuerySelectDto);

    List<Book> GetAllBooks(BookQuerySelectDto bookQuerySelectDto,Integer BeginIndex);

    Book GetBookById(Integer id);

    /**
     * ISBN 是否已存在（返回条数，0 表示没重复）
     *
     * @param excludeId 「修改图书」时传自己的 id，把自己排除掉；新增时传 null
     */
    int CountByIsbn(@Param("isbn") String isbn, @Param("excludeId") Integer excludeId);

    /** 分类是否存在（返回条数，0 表示不存在） */
    int CountCategoryById(@Param("id") Integer id);

    /** 新增图书，返回受影响行数 */
    int InsertBook(BookSaveDto bookSaveDto);

    /**
     * 修改图书，返回受影响行数（0 表示这个 id 的图书不存在）
     *
     * <p>⚠️ 三个参数必须都加 {@code @Param} 起名字。原因是：
     * MyBatis 只有在「方法只有一个参数」时才会把对象的属性摊开来用；
     * 一旦有两个以上参数，它会把它们装进一个 Map，
     * 此时 XML 里必须写 {@code #{dto.isbn}} 这种带前缀的形式。
     * 直接写 {@code #{isbn}} 会抛（实测 MyBatis 3.5.19 的原话）：
     * <pre>
     * org.apache.ibatis.binding.BindingException: Parameter 'isbn' not found.
     * Available parameters are [id, availableNum, param3, param1, dto, param2]
     * </pre>
     *
     * @param availableNum 由 Service 按「已借出册数不变」算好后传进来，
     *                     不能直接用 dto.totalNum（详见 BookMapper.xml 里的注释）
     */
    int UpdateBook(@Param("id") Integer id,
                   @Param("dto") BookSaveDto dto,
                   @Param("availableNum") Integer availableNum);

    void DeleteBook(Integer id);

    void UpdateBookStatus(Integer id, Integer status);
}
