package org.libaray_manager_system.service;

import org.libaray_manager_system.pojo.dto.NoticeSaveDto;
import org.libaray_manager_system.pojo.dto.NoticeSelectDto;
import org.libaray_manager_system.pojo.entity.LoginUser;
import org.libaray_manager_system.pojo.NoticePageResult;
import org.libaray_manager_system.pojo.vo.NoticeDetailVo;

public interface NoticeService {

    /** 通知分页列表：管理员看全部，普通读者只看已发布的 */
    NoticePageResult GetNotices(NoticeSelectDto dto, LoginUser loginUser);

    /** 通知详情：普通读者打开时顺带标记已读 */
    NoticeDetailVo GetNoticeById(Long id, LoginUser loginUser);

    /** 我的未读数：管理员恒为 0 */
    Integer GetUnreadCount(LoginUser loginUser);

    /** 发布通知（仅管理员）：发布人与发布时间由后端定 */
    void AddNotice(NoticeSaveDto dto, LoginUser loginUser);

    /** 编辑通知（仅管理员）：不改发布人与首次发布时间，也不清空已读记录 */
    void UpdateNotice(Long id, NoticeSaveDto dto);

    /** 置顶 / 取消置顶（仅管理员） */
    void UpdateTop(Long id, Integer isTop);

    /** 删除通知（仅管理员）：连带删掉它的已读记录 */
    void DeleteNotice(Long id);
}
