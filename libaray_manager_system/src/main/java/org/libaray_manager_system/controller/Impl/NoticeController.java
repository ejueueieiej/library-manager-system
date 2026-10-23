package org.libaray_manager_system.controller.Impl;

import org.libaray_manager_system.annotation.RequireAdmin;
import org.libaray_manager_system.pojo.Result;
import org.libaray_manager_system.pojo.dto.NoticeSaveDto;
import org.libaray_manager_system.pojo.dto.NoticeSelectDto;
import org.libaray_manager_system.pojo.entity.LoginUser;
import org.libaray_manager_system.service.NoticeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NoticeController {

    @Autowired
    private NoticeService noticeService;

    // 通知分页列表：管理员看全部，普通读者只看已发布的
    @GetMapping("/notices")
    public Result GetNotices(NoticeSelectDto dto,
                             @RequestAttribute("loginUser") LoginUser loginUser) {
        return new Result().success(noticeService.GetNotices(dto, loginUser));
    }

    // 通知详情：普通读者打开时会顺带标记已读
    @GetMapping("/notices/{id}")
    public Result GetNotice(@PathVariable Long id,
                            @RequestAttribute("loginUser") LoginUser loginUser) {
        return new Result().success(noticeService.GetNoticeById(id, loginUser));
    }

    // 路径与 /notices/{id} 形似，Spring 的路径匹配优先命中字面量段
    @GetMapping("/notices/unread-count")
    public Result GetUnreadCount(@RequestAttribute("loginUser") LoginUser loginUser) {
        return new Result().success(noticeService.GetUnreadCount(loginUser));
    }

    // 发布通知（仅管理员）：请求体里没有 publisherId / publishTime
    @RequireAdmin
    @PostMapping("/notices")
    public Result AddNotice(@RequestBody NoticeSaveDto dto,
                            @RequestAttribute("loginUser") LoginUser loginUser) {
        noticeService.AddNotice(dto, loginUser);
        return new Result().success("发布成功", null);
    }

    // 编辑通知（仅管理员）：不改发布人、不改首次发布时间、不清已读记录
    @RequireAdmin
    @PutMapping("/notices/{id}")
    public Result UpdateNotice(@PathVariable Long id, @RequestBody NoticeSaveDto dto) {
        noticeService.UpdateNotice(id, dto);
        return new Result().success("修改成功", null);
    }

    // 置顶 / 取消置顶（仅管理员）
    @RequireAdmin
    @PatchMapping("/notices/{id}/top")
    public Result UpdateTop(@PathVariable Long id, @RequestParam Integer isTop) {
        noticeService.UpdateTop(id, isTop);
        return new Result().success(isTop == 1 ? "已置顶" : "已取消置顶", null);
    }

    // 删除通知（仅管理员）：Service 里带事务，连带删掉已读记录
    @RequireAdmin
    @DeleteMapping("/notices/{id}")
    public Result DeleteNotice(@PathVariable Long id) {
        noticeService.DeleteNotice(id);
        return new Result().success("删除成功", null);
    }
}
