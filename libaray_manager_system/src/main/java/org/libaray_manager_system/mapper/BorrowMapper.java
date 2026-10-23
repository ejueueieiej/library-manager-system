package org.libaray_manager_system.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.libaray_manager_system.pojo.dto.BorrowSelectDto;
import org.libaray_manager_system.pojo.entity.BorrowRecord;
import org.libaray_manager_system.pojo.vo.BorrowSummaryVo;
import org.libaray_manager_system.pojo.vo.BorrowVo;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface BorrowMapper {

    Integer CountBorrowingByUserAndBook(@Param("userId") Integer userId, @Param("bookId") Integer bookId);

    Integer CountBorrowingByUser(@Param("userId") Integer userId);

    int DecreaseAvailable(@Param("bookId") Integer bookId);

    void InsertBorrow(@Param("userId") Integer userId,
                      @Param("bookId") Integer bookId,
                      @Param("borrowTime") LocalDateTime borrowTime,
                      @Param("dueTime") LocalDateTime dueTime);

    List<BorrowVo> GetMyBorrows(@Param("dto") BorrowSelectDto dto,
                                @Param("userId") Integer userId,
                                @Param("startIndex") Integer startIndex);

    Integer GetMyBorrowCount(@Param("dto") BorrowSelectDto dto, @Param("userId") Integer userId);

    BorrowSummaryVo GetMySummary(@Param("userId") Integer userId);

    BorrowRecord GetById(@Param("id") Long id);

    int ReturnBook(@Param("id") Long id, @Param("returnTime") LocalDateTime returnTime);

    int IncreaseAvailable(@Param("bookId") Integer bookId);

    int RenewBook(@Param("id") Long id, @Param("renewDays") Integer renewDays);

    List<BorrowVo> GetAllBorrows(@Param("dto") BorrowSelectDto dto,
                                 @Param("startIndex") Integer startIndex);

    Integer GetAllBorrowCount(@Param("dto") BorrowSelectDto dto);

    int CancelBorrow(@Param("id") Long id);

    int DeleteById(@Param("id") Long id);
}
