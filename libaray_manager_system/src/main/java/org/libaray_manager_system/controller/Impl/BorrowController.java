package org.libaray_manager_system.controller.Impl;

import org.libaray_manager_system.annotation.RequireAdmin;
import org.libaray_manager_system.pojo.Result;
import org.libaray_manager_system.pojo.dto.BorrowCreateDto;
import org.libaray_manager_system.pojo.dto.BorrowSelectDto;
import org.libaray_manager_system.pojo.entity.LoginUser;
import org.libaray_manager_system.service.BorrowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BorrowController {

    @Autowired
    private BorrowService borrowService;

    // 借书（用户自助）
    @PostMapping("/borrows")
    public Result Borrow(@RequestBody BorrowCreateDto dto,
                         @RequestAttribute("loginUser") LoginUser loginUser) {
        borrowService.BorrowBook(dto, loginUser);
        return new Result().success("借阅成功", null);
    }

    // 我的借阅（分页）：查谁由 token 决定，不接受 userId 参数
    @GetMapping("/borrows/my")
    public Result GetMyBorrows(BorrowSelectDto dto,
                               @RequestAttribute("loginUser") LoginUser loginUser) {
        return new Result().success(borrowService.GetMyBorrows(dto, loginUser));
    }

    // 我的借阅统计（页头那三个数）
    @GetMapping("/borrows/my/summary")
    public Result GetMySummary(@RequestAttribute("loginUser") LoginUser loginUser) {
        return new Result().success(borrowService.GetMySummary(loginUser));
    }

    // 全部借阅记录（仅管理员）
    @RequireAdmin
    @GetMapping("/borrows")
    public Result GetAllBorrows(BorrowSelectDto dto) {
        return new Result().success(borrowService.GetAllBorrows(dto));
    }

    // 确认归还（仅管理员）：returnTime 是"实际归还日期"，不传按现在
    // 用 String 接而不是 LocalDate，否则格式不对会变成 500
    @RequireAdmin
    @PatchMapping("/borrows/{id}/return")
    public Result ReturnBook(@PathVariable Long id,
                             @RequestParam(required = false) String returnTime) {
        borrowService.ReturnBook(id, returnTime);
        return new Result().success("归还成功", null);
    }

    // 续借（本人或管理员）
    @PatchMapping("/borrows/{id}/renew")
    public Result RenewBook(@PathVariable Long id,
                            @RequestAttribute("loginUser") LoginUser loginUser) {
        borrowService.RenewBook(id, loginUser);
        return new Result().success("续借成功", null);
    }

    // 撤销借阅（仅管理员）：书回库存，记录置为"已取消"，不删记录
    @RequireAdmin
    @PatchMapping("/borrows/{id}/cancel")
    public Result CancelBorrow(@PathVariable Long id) {
        borrowService.CancelBorrow(id);
        return new Result().success("已撤销该笔借阅", null);
    }

    // 删除记录（仅管理员，备用通道）：只用于清理测试数据 / 订正误操作
    // 借出中的记录会被拒绝 —— 那是"这本书在谁手上"的唯一凭证
    @RequireAdmin
    @DeleteMapping("/borrows/{id}")
    public Result DeleteBorrow(@PathVariable Long id) {
        borrowService.DeleteBorrow(id);
        return new Result().success("删除成功", null);
    }
}
