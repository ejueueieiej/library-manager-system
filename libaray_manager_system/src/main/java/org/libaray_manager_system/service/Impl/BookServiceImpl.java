package org.libaray_manager_system.service.Impl;

import lombok.extern.slf4j.Slf4j;
import org.libaray_manager_system.exception.BusinessException;
import org.libaray_manager_system.mapper.BookMapper;
import org.libaray_manager_system.pojo.dto.BookQuerySelectDto;
import org.libaray_manager_system.pojo.dto.BookSaveDto;
import org.libaray_manager_system.pojo.entity.Book;
import org.libaray_manager_system.pojo.entity.Category;
import org.libaray_manager_system.pojo.bookpageResult;
import org.libaray_manager_system.service.BookService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@Slf4j
public class BookServiceImpl implements BookService {
    @Autowired
    private BookMapper bookMapper;

    @Override
    public List<Category> GetAllCategories() {
        return bookMapper.GetAllCategories();
    }

    @Override
    public bookpageResult GetAllBooks(BookQuerySelectDto bookQuerySelectDto) {
        int total = bookMapper.GetTotal(bookQuerySelectDto);
        Integer BeginIndex=(bookQuerySelectDto.getPageNum()-1)*bookQuerySelectDto.getPageSize();
        List<Book> books = bookMapper.GetAllBooks(bookQuerySelectDto,BeginIndex);
        bookpageResult pageresult = new bookpageResult(total, books);
        return pageresult;
    }

    @Override
    public Book GetBookById(Integer id) {
        return bookMapper.GetBookById(id);
    }

    /* ==================== 新增图书 ==================== */

    @Override
    public void AddBook(BookSaveDto bookSaveDto) {

        // 第 1 步：参数校验（必填 / 长度 / 数值范围）
        // 校验不过直接抛 BusinessException，由 GlobalExceptionHandler 转成
        // {code:0, msg:"书名不能为空"} 这种返回体，HTTP 仍然是 200
        validate(bookSaveDto);

        // 第 2 步：ISBN 唯一性预查 —— 给用户一句友好的提示
        // excludeId 传 null：新增时不存在"排除自己"的问题（那个是修改图书才需要的）
        if (bookMapper.CountByIsbn(bookSaveDto.getIsbn(), null) > 0) {
            throw new BusinessException("该 ISBN 已存在");
        }

        // 第 3 步：分类必须真实存在，否则会插出一条挂在野分类上的书
        if (bookMapper.CountCategoryById(bookSaveDto.getCategoryId()) == 0) {
            throw new BusinessException("分类不存在");
        }

        // 第 4 步：补默认值
        // 为什么必须补？因为 book 表的 price / status 是 NOT NULL，
        // 严格模式下往 NOT NULL 列插 null 会直接报 1048 错误。
        if (bookSaveDto.getPrice() == null) {
            bookSaveDto.setPrice(BigDecimal.ZERO);
        }
        if (bookSaveDto.getStatus() == null) {
            bookSaveDto.setStatus(1);
        }

        // 第 5 步：入库
        // available_num 不在这里赋值 —— XML 里直接写的 #{totalNum}，
        // 让「可借册数 = 总册数」这件事在 SQL 层面就定死，前端传什么都改不动它。
        bookMapper.InsertBook(bookSaveDto);
    }

    /**
     * 新增 / 修改图书共用的参数校验
     *
     * <p>提示语和接口文档 4 节的「可能的错误返回」表保持一致。
     * 校验按「先必填、再长度、最后数值」的顺序来，保证用户一次只看到最该先修的那条。
     *
     * <p>两个接口的字段完全一样（文档 5 节：请求体字段与新增完全一样），
     * 所以这里抽出来共用，改一处两边都生效。
     */
    private void validate(BookSaveDto dto) {
        // 必填项
        if (isBlank(dto.getIsbn())) {
            throw new BusinessException("ISBN 不能为空");
        }
        if (isBlank(dto.getName())) {
            throw new BusinessException("书名不能为空");
        }
        if (isBlank(dto.getAuthor())) {
            throw new BusinessException("作者不能为空");
        }
        if (dto.getCategoryId() == null) {
            throw new BusinessException("请选择分类");
        }
        if (dto.getTotalNum() == null) {
            throw new BusinessException("总册数不能为空");
        }

        // 长度限制：超了数据库那一列会截断或直接报错，提前挡住
        if (dto.getIsbn().length() > 20) {
            throw new BusinessException("ISBN 最多 20 个字符");
        }
        if (dto.getName().length() > 100) {
            throw new BusinessException("书名最多 100 个字符");
        }
        if (dto.getAuthor().length() > 50) {
            throw new BusinessException("作者最多 50 个字符");
        }
        if (dto.getPublisher() != null && dto.getPublisher().length() > 100) {
            throw new BusinessException("出版社最多 100 个字符");
        }
        if (dto.getCoverUrl() != null && dto.getCoverUrl().length() > 255) {
            throw new BusinessException("封面地址最多 255 个字符");
        }

        // 数值范围
        if (dto.getTotalNum() < 1) {
            throw new BusinessException("总册数至少为 1");
        }
        if (dto.getPrice() != null && dto.getPrice().signum() < 0) {
            throw new BusinessException("定价不能为负");
        }
    }

    /** null 或 全是空白 都算空（"   " 这种前端偶尔会传） */
    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

//    修改图书
    @Override
    public void UpdateBook(Integer id, BookSaveDto bookSaveDto) {

        // 第 1 步：先确认这本书真的存在
        // 放在参数校验前面：改一个不存在的 id 时，应该直接告诉用户「图书不存在」，
        // 而不是先挑他表单里的毛病（那样提示会跑偏）
        Book old = bookMapper.GetBookById(id);
        if (old == null) {
            throw new BusinessException("图书不存在");
        }

        // 第 2 步：参数校验（和新增共用同一套规则）
        validate(bookSaveDto);

        // 第 3 步：ISBN 唯一性 —— 关键是把「自己」排除掉（excludeId 传 id）
        // 不排除的话，用户「只改价格、压根没动 ISBN」也会被判成重复，
        // 这是修图接口必踩的一个坑。
        if (bookMapper.CountByIsbn(bookSaveDto.getIsbn(), id) > 0) {
            throw new BusinessException("该 ISBN 已存在");
        }

        // 第 4 步：分类必须真实存在
        if (bookMapper.CountCategoryById(bookSaveDto.getCategoryId()) == 0) {
            throw new BusinessException("分类不存在");
        }

        // 第 5 步：补默认值（price / status 是 NOT NULL 列，和新增同理）
        if (bookSaveDto.getPrice() == null) {
            bookSaveDto.setPrice(BigDecimal.ZERO);
        }
        if (bookSaveDto.getStatus() == null) {
            bookSaveDto.setStatus(1);
        }

        // 第 6 步：算新的可借册数 —— 本接口最容易写错的地方
        //   口径：已借出的册数不变
        //     已借出 = 旧总册数 - 旧可用数
        //     新可用 = 新总册数 - 已借出
        //   例子：原来 3 册、借出 1 册（可用 2），管理员把总册数改成 5
        //         → 新可用 = 5 - 1 = 4
        //   如果偷懒写成 availableNum = totalNum，就会把借出去的那本凭空变出来。
        //   （Book.availableNum 是 BigDecimal，数据库列却是 INT，这里取 intValue 对齐）
        int oldTotal = old.getTotalNum() == null ? 0 : old.getTotalNum();
        int oldAvailable = old.getAvailableNum() == null ? 0 : old.getAvailableNum().intValue();
        int borrowed = oldTotal - oldAvailable;
        int newAvailable = bookSaveDto.getTotalNum() - borrowed;

        // 防御：总册数不能比已借出的还少，否则可用数会算成负数
        // （文档没写这条，但负的可借册数是个不可能状态，提前挡掉）
        if (newAvailable < 0) {
            throw new BusinessException("总册数不能小于已借出的 " + borrowed + " 册");
        }

        // 第 7 步：落库
        int rows = bookMapper.UpdateBook(id, bookSaveDto, newAvailable);
        if (rows == 0) {
            // 理论上走不到（上面已经查过存在性了）。但如果刚好有并发请求在这两步之间
            // 把这本书删了，这里能兜住，避免明明没改成功却返回「修改成功」骗人。
            throw new BusinessException("图书不存在");
        }
    }

    //    删除图书
    @Override
    public void DeleteBook(Integer id) {
        // 第 1 步：先确认这本书真的存在
        // 放在参数校验前面：删一个不存在的 id 时，应该直接告诉用户「图书不存在」，
        // 而不是先挑他表单里的毛病（那样提示会跑偏）
        Book old = bookMapper.GetBookById(id);
        if (old == null) {
            throw new BusinessException("图书不存在");
        }
        if(old.getTotalNum().intValue()-old.getAvailableNum().intValue()>0){
            throw new BusinessException("该图书已被借出，不能删除");
        }
        bookMapper.DeleteBook(id);
    }

    //上架/下架图书
    @Override
    public String UpdateBookStatus(Integer id, Integer status) {
        // 状态只能是 0 或 1，挡住 ?status=9 这种乱传
        if (status == null || (status != 1 && status != 0)) {
            throw new BusinessException("状态只能是 1（上架）或 0（下架）");
        }
        Book old = bookMapper.GetBookById(id);
        if (old == null) {
            throw new BusinessException("图书不存在");
        }
        // 直接用前端传的目标状态，不做翻转！
        // 前端 el-switch 已经把开关拨到目标值了，传过来的就是"我想要的状态"
        bookMapper.UpdateBookStatus(id, status);
        return status == 1 ? "已上架" : "已下架";
    }
}
