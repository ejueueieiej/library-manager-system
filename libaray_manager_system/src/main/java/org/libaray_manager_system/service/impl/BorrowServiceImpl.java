package org.libaray_manager_system.service.impl;

import org.libaray_manager_system.exception.BusinessException;
import org.libaray_manager_system.mapper.BookMapper;
import org.libaray_manager_system.mapper.BorrowMapper;
import org.libaray_manager_system.pojo.BorrowPageResult;
import org.libaray_manager_system.pojo.dto.BorrowCreateDto;
import org.libaray_manager_system.pojo.dto.BorrowSelectDto;
import org.libaray_manager_system.pojo.entity.Book;
import org.libaray_manager_system.pojo.entity.BorrowRecord;
import org.libaray_manager_system.pojo.entity.LoginUser;
import org.libaray_manager_system.pojo.vo.BorrowSummaryVo;
import org.libaray_manager_system.pojo.vo.BorrowVo;
import org.libaray_manager_system.service.BorrowService;
import org.libaray_manager_system.utils.CacheUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class BorrowServiceImpl implements BorrowService {

    /** 借期 30 天 */
    private static final int BORROW_DAYS = 30;
    /** 每人同时最多借 5 本 */
    private static final int MAX_BORROW = 5;
    /** 每本最多续借 1 次 */
    private static final int MAX_RENEW = 1;
    /** 每次续借延长 30 天 */
    private static final int RENEW_DAYS = 30;

    @Autowired
    private BorrowMapper borrowMapper;
    @Autowired
    private BookMapper bookMapper;
    @Autowired
    private CacheUtil cacheUtil;

    @Override
    @Transactional
    public void BorrowBook(BorrowCreateDto dto, LoginUser loginUser) {
        if (dto.getBookId() == null) {
            throw new BusinessException("请选择要借阅的图书");
        }

        Book book = bookMapper.GetBookById(dto.getBookId());
        if (book == null) {
            throw new BusinessException("图书不存在");
        }
        if (book.getStatus() != null && book.getStatus() == 0) {
            throw new BusinessException("该图书已下架，暂不可借阅");
        }

        if (borrowMapper.CountBorrowingByUserAndBook(loginUser.getId(), dto.getBookId()) > 0) {
            throw new BusinessException("你已借阅该书且尚未归还");
        }
        if (borrowMapper.CountBorrowingByUser(loginUser.getId()) >= MAX_BORROW) {
            throw new BusinessException("最多同时借阅 " + MAX_BORROW + " 本，请先归还部分图书");
        }

        // 抢不到库存时返回 0 行，事务回滚
        if (borrowMapper.DecreaseAvailable(dto.getBookId()) == 0) {
            throw new BusinessException("该图书暂无可借库存");
        }

        LocalDateTime now = LocalDateTime.now();
        borrowMapper.InsertBorrow(loginUser.getId(), dto.getBookId(), now, now.plusDays(BORROW_DAYS));

        // 库存变了，详情缓存必须失效，否则详情页还显示旧的可借数
        cacheUtil.deleteKey(CacheUtil.CACHE_BOOK_KEY + dto.getBookId());
    }

    @Override
    public BorrowPageResult GetMyBorrows(BorrowSelectDto dto, LoginUser loginUser) {
        Integer startIndex = (dto.getPageNum() - 1) * dto.getPageSize();
        List<BorrowVo> list = borrowMapper.GetMyBorrows(dto, loginUser.getId(), startIndex);
        list.forEach(this::FillComputed);

        Integer total = borrowMapper.GetMyBorrowCount(dto, loginUser.getId());
        return new BorrowPageResult(total, list);
    }

    @Override
    public BorrowSummaryVo GetMySummary(LoginUser loginUser) {
        return borrowMapper.GetMySummary(loginUser.getId());
    }

    @Override
    public BorrowPageResult GetAllBorrows(BorrowSelectDto dto) {
        Integer startIndex = (dto.getPageNum() - 1) * dto.getPageSize();
        List<BorrowVo> list = borrowMapper.GetAllBorrows(dto, startIndex);
        list.forEach(this::FillComputed);

        Integer total = borrowMapper.GetAllBorrowCount(dto);
        return new BorrowPageResult(total, list);
    }

    @Override
    @Transactional
    public void ReturnBook(Long id, String returnTime) {
        BorrowRecord record = borrowMapper.GetById(id);
        if (record == null) {
            throw new BusinessException("借阅记录不存在");
        }
        if (record.getStatus() == 2) {
            throw new BusinessException("该借阅记录已归还，请勿重复操作");
        }
        if (record.getStatus() == 3) {
            throw new BusinessException("该借阅已被取消，无需归还");
        }

        LocalDateTime actual = ParseReturnTime(returnTime);

        if (actual.toLocalDate().isAfter(LocalDate.now())) {
            throw new BusinessException("归还日期不能晚于今天");
        }
        if (actual.toLocalDate().isBefore(record.getBorrowTime().toLocalDate())) {
            throw new BusinessException("归还日期不能早于借出日期");
        }

        // 带 status 条件，0 行说明已被别的请求改过（并发兜底）
        if (borrowMapper.ReturnBook(id, actual) == 0) {
            throw new BusinessException("该借阅记录已归还，请勿重复操作");
        }
        borrowMapper.IncreaseAvailable(record.getBookId());
        cacheUtil.deleteKey(CacheUtil.CACHE_BOOK_KEY + record.getBookId());
    }

    @Override
    public void RenewBook(Long id, LoginUser loginUser) {
        BorrowRecord record = GetOwnedRecord(id, loginUser);

        if (record.getStatus() == 2) {
            throw new BusinessException("该借阅已归还，无需续借");
        }
        if (record.getStatus() == 3) {
            throw new BusinessException("该借阅已取消，无需续借");
        }
        if (record.getRenewCount() >= MAX_RENEW) {
            throw new BusinessException("每本图书只能续借 " + MAX_RENEW + " 次");
        }
        // 和 OverdueDays / SQL 的 `due_time < NOW()` 同一口径，否则会出现"按钮亮着、点了却说逾期"
        if (record.getDueTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException("图书已逾期，请先归还后再重新借阅");
        }

        if (borrowMapper.RenewBook(id, RENEW_DAYS) == 0) {
            throw new BusinessException("续借失败，请刷新后重试");
        }
    }

    @Override
    @Transactional
    public void CancelBorrow(Long id) {
        BorrowRecord record = borrowMapper.GetById(id);
        if (record == null) {
            throw new BusinessException("借阅记录不存在");
        }
        if (record.getStatus() == 2) {
            throw new BusinessException("该借阅已归还，不能撤销");
        }
        if (record.getStatus() == 3) {
            throw new BusinessException("该借阅已被取消，请勿重复操作");
        }

        // 和归还同一个幂等门，0 行说明已被别的请求改过
        if (borrowMapper.CancelBorrow(id) == 0) {
            throw new BusinessException("该借阅已被取消，请勿重复操作");
        }
        borrowMapper.IncreaseAvailable(record.getBookId());
        cacheUtil.deleteKey(CacheUtil.CACHE_BOOK_KEY + record.getBookId());
    }

    @Override
    public void DeleteBorrow(Long id) {
        BorrowRecord record = borrowMapper.GetById(id);
        if (record == null) {
            throw new BusinessException("借阅记录不存在");
        }
        if (record.getStatus() == 1) {
            throw new BusinessException("借出中的记录不能删除，请先确认归还或撤销借阅");
        }

        // status != 1 由 SQL 再兜一次：SQL 层不让删，才是最后一道闸
        if (borrowMapper.DeleteById(id) == 0) {
            throw new BusinessException("删除失败，请刷新后重试");
        }
    }

    /** overdueDays / canRenew 不是数据库列，查出来是 null，统一在这里补 */
    private void FillComputed(BorrowVo vo) {
        // 借出中：和"现在"比（逾期还在持续）
        // 已归还：和"实际归还时间"比（历史事实）——拿今天算的话，8 月还的记录到 12 月会显示"逾期 100 多天"
        LocalDateTime end = vo.getStatus() == 1 ? LocalDateTime.now()
                : vo.getStatus() == 2 ? vo.getReturnTime()
                : null;

        vo.setOverdueDays(end == null ? 0 : OverdueDays(vo.getDueTime(), end));
        // 可续借：没逾期 + 还没续借过
        vo.setCanRenew(vo.getStatus() == 1 && vo.getOverdueDays() == 0 && vo.getRenewCount() < MAX_RENEW ? 1 : 0);
    }

    /** 逾期天数：判定口径和 SQL 里的 `due_time < NOW()` 完全一致；不满一天也计 1 天 */
    private int OverdueDays(LocalDateTime dueTime, LocalDateTime end) {
        if (dueTime == null || !dueTime.isBefore(end)) {
            return 0;
        }
        long days = ChronoUnit.DAYS.between(dueTime.toLocalDate(), end.toLocalDate());
        return days > 0 ? (int) days : 1;
    }

    /** 只接受 yyyy-MM-dd；不传按现在 */
    private LocalDateTime ParseReturnTime(String returnTime) {
        if (returnTime == null || returnTime.trim().isEmpty()) {
            return LocalDateTime.now();
        }
        try {
            // 日期是权威的；时间部分取录入时刻，只为了页面显示不像 00:00:00
            return LocalDate.parse(returnTime.trim()).atTime(LocalTime.now());
        } catch (DateTimeParseException e) {
            throw new BusinessException("归还日期格式不正确，应为 yyyy-MM-dd");
        }
    }

    /** 本人或管理员才能动这条记录 */
    private BorrowRecord GetOwnedRecord(Long id, LoginUser loginUser) {
        BorrowRecord record = borrowMapper.GetById(id);
        if (record == null) {
            throw new BusinessException("借阅记录不存在");
        }
        if (!loginUser.isAdmin() && !record.getUserId().equals(loginUser.getId())) {
            throw new BusinessException("无权操作他人的借阅记录");
        }
        return record;
    }
}
