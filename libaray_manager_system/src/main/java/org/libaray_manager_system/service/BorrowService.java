package org.libaray_manager_system.service;

import org.libaray_manager_system.pojo.BorrowPageResult;
import org.libaray_manager_system.pojo.dto.BorrowCreateDto;
import org.libaray_manager_system.pojo.dto.BorrowSelectDto;
import org.libaray_manager_system.pojo.entity.LoginUser;
import org.libaray_manager_system.pojo.vo.BorrowSummaryVo;

public interface BorrowService {

    void BorrowBook(BorrowCreateDto dto, LoginUser loginUser);

    BorrowPageResult GetMyBorrows(BorrowSelectDto dto, LoginUser loginUser);

    BorrowSummaryVo GetMySummary(LoginUser loginUser);

    void ReturnBook(Long id, String returnTime);

    void RenewBook(Long id, LoginUser loginUser);

    BorrowPageResult GetAllBorrows(BorrowSelectDto dto);

    void CancelBorrow(Long id);

    void DeleteBorrow(Long id);
}
