package org.libaray_manager_system.service.impl;

import org.libaray_manager_system.exception.BusinessException;
import org.libaray_manager_system.mapper.NoticeMapper;
import org.libaray_manager_system.pojo.dto.NoticeSaveDto;
import org.libaray_manager_system.pojo.dto.NoticeSelectDto;
import org.libaray_manager_system.pojo.entity.LoginUser;
import org.libaray_manager_system.pojo.entity.Notice;
import org.libaray_manager_system.pojo.NoticePageResult;
import org.libaray_manager_system.pojo.vo.NoticeDetailVo;
import org.libaray_manager_system.pojo.vo.NoticeVo;
import org.libaray_manager_system.service.NoticeService;
import org.libaray_manager_system.utils.CacheUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class NoticeServiceImpl implements NoticeService {

    @Autowired
    private NoticeMapper noticeMapper;
    @Autowired
    private CacheUtil cacheUtil;

    @Override
    public NoticePageResult GetNotices(NoticeSelectDto dto, LoginUser loginUser) {
        if (!loginUser.isAdmin()) {
            dto.setStatus(1);
        }

        Integer startIndex = (dto.getPageNum() - 1) * dto.getPageSize();
        List<NoticeVo> list = noticeMapper.GetNotices(dto, loginUser.getId(), startIndex);
        Integer total = noticeMapper.GetNoticeCount(dto);
        return new NoticePageResult(total, list);
    }

    @Override
    public NoticeDetailVo GetNoticeById(Long id, LoginUser loginUser) {
//        Notice notice = noticeMapper.GetNoticeEntityById(id);
//        if (notice == null) {
//            throw new BusinessException("通知不存在");
//        }
//        if (notice.getStatus() == 0 && !loginUser.isAdmin()) {
//            throw new BusinessException("通知不存在或已下架");
//        }
//
//        // 标记已读只对普通读者做，并且必须在"通知可见"校验之后
//        if (!loginUser.isAdmin()) {
//            noticeMapper.InsertRead(id, loginUser.getId());
//        }
//        return noticeMapper.GetNoticeById(id, loginUser.getId());
        Notice notice =cacheUtil.queryWithPassThrough(
                cacheUtil.CACHE_NOTICE_KEY,id,Notice.class,noticeMapper::GetNoticeEntityById,cacheUtil.CACHE_NOTICE_TIME, TimeUnit.MINUTES);
        if (notice == null) {
            throw new BusinessException("通知不存在");
        }
        if (notice.getStatus() == 0 && !loginUser.isAdmin()) {
            throw new BusinessException("通知不存在或已下架");
        }
        if (!loginUser.isAdmin()) {
            noticeMapper.InsertRead(id, loginUser.getId());   // 仍然实时写
        }
        return noticeMapper.GetNoticeById(id, loginUser.getId());  // is_read 仍然实时查
    }


    @Override
    public Integer GetUnreadCount(LoginUser loginUser) {
        if (loginUser.isAdmin()) {
            return 0;
        }
        return noticeMapper.GetUnreadCount(loginUser.getId());
    }

    @Override
    public void AddNotice(NoticeSaveDto dto, LoginUser loginUser) {
        CheckSaveDto(dto);

        Notice notice = new Notice();
        notice.setTitle(dto.getTitle().trim());
        notice.setContent(dto.getContent());
        notice.setType(dto.getType());
        notice.setIsTop(dto.getIsTop() == null ? 0 : dto.getIsTop());
        notice.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        notice.setPublisherId(loginUser.getId().longValue());
        notice.setPublishTime(LocalDateTime.now());
        noticeMapper.Insert(notice);
        cacheUtil.deleteKey(CacheUtil.CACHE_NOTICE_KEY + notice.getId());
    }

    @Override
    public void UpdateNotice(Long id, NoticeSaveDto dto) {
        CheckSaveDto(dto);
        if (noticeMapper.GetNoticeEntityById(id) == null) {
            throw new BusinessException("通知不存在");
        }

        Notice notice = new Notice();
        notice.setId(id);
        notice.setTitle(dto.getTitle().trim());
        notice.setContent(dto.getContent());
        notice.setType(dto.getType());
        notice.setIsTop(dto.getIsTop() == null ? 0 : dto.getIsTop());
        notice.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        noticeMapper.UpdateById(notice);
        cacheUtil.deleteKey(CacheUtil.CACHE_NOTICE_KEY+id);
    }

    @Override
    public void UpdateTop(Long id, Integer isTop) {
        if (isTop == null || (isTop != 0 && isTop != 1)) {
            throw new BusinessException("置顶标记只能是 0 或 1");
        }
        if (noticeMapper.GetNoticeEntityById(id) == null) {
            throw new BusinessException("通知不存在");
        }
        noticeMapper.UpdateTop(id, isTop);
        cacheUtil.deleteKey(CacheUtil.CACHE_NOTICE_KEY+id);
    }

    @Override
    @Transactional// 事务：确保删除已读记录与通知本身是原子操作
    public void DeleteNotice(Long id) {
        if (noticeMapper.GetNoticeEntityById(id) == null) {
            throw new BusinessException("通知不存在");
        }
        // 两步必须同生共死：只删成功一半会留下孤儿已读记录，或让通知"复活"成全未读
        noticeMapper.DeleteReadByNoticeId(id);
        noticeMapper.DeleteById(id);
        cacheUtil.deleteKey(CacheUtil.CACHE_NOTICE_KEY+id);
    }

    /** 发布与编辑共用的字段校验 */
    private void CheckSaveDto(NoticeSaveDto dto) {
        if (dto.getTitle() == null || dto.getTitle().trim().isEmpty()) {
            throw new BusinessException("标题不能为空");
        }
        if (dto.getTitle().trim().length() > 100) {
            throw new BusinessException("标题长度不能超过 100 个字符");
        }
        if (dto.getContent() == null || dto.getContent().trim().isEmpty()) {
            throw new BusinessException("正文不能为空");
        }
        if (dto.getType() == null || dto.getType() < 1 || dto.getType() > 3) {
            throw new BusinessException("通知类型不正确");
        }
        if (dto.getIsTop() != null && dto.getIsTop() != 0 && dto.getIsTop() != 1) {
            throw new BusinessException("置顶标记只能是 0 或 1");
        }
        if (dto.getStatus() != null && dto.getStatus() != 0 && dto.getStatus() != 1) {
            throw new BusinessException("状态只能是 0 或 1");
        }
    }
}
