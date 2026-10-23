package org.libaray_manager_system.service;

import org.libaray_manager_system.pojo.borrowPageResult;
import org.libaray_manager_system.pojo.dto.BorrowCreateDto;
import org.libaray_manager_system.pojo.dto.BorrowSelectDto;
import org.libaray_manager_system.pojo.entity.LoginUser;
import org.libaray_manager_system.pojo.vo.BorrowSummaryVo;

public interface BorrowService {

    void BorrowBook(BorrowCreateDto dto, LoginUser loginUser);

    borrowPageResult GetMyBorrows(BorrowSelectDto dto, LoginUser loginUser);

    BorrowSummaryVo GetMySummary(LoginUser loginUser);

    void ReturnBook(Long id, String returnTime);

    void RenewBook(Long id, LoginUser loginUser);

    borrowPageResult GetAllBorrows(BorrowSelectDto dto);

    void CancelBorrow(Long id);

    void DeleteBorrow(Long id);
}
