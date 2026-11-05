package org.libaray_manager_system.service;

import org.libaray_manager_system.pojo.dto.ReaderFileSelectDto;
import org.libaray_manager_system.pojo.dto.ReaderProgressDto;
import org.libaray_manager_system.pojo.entity.LoginUser;
import org.libaray_manager_system.pojo.readerFilePageResult;
import org.libaray_manager_system.pojo.vo.ReaderChapterVo;
import org.libaray_manager_system.pojo.vo.ReaderFileVo;
import org.libaray_manager_system.pojo.vo.ReaderMetaVo;
import org.springframework.web.multipart.MultipartFile;

/**
 * 在线阅读（4.0）
 *
 * 14 个方法对 14 个接口，一一对应。
 *   书籍正文 6 个 —— 管理员上传，读者看开关
 *   用户文件 8 个 —— 自己上传，只有自己能读
 * 两组不共用实现：权限模型完全不同（一个看公共开关，一个看归属）。
 */
public interface ReaderService {

    /* ==================== 一、书籍正文 ==================== */

    /** 打开 / 关闭某本书的在线阅读（仅管理员） */
    void SetBookReadable(Integer bookId, Integer readable);

    /** 上传 / 替换书籍正文（仅管理员） */
    void UploadBookContent(Integer bookId, MultipartFile file);

    /** 删除书籍正文，连带清掉所有人对它的进度（仅管理员） */
    void DeleteBookContent(Integer bookId);

    /** 目录 + 能不能读（不含正文） */
    ReaderMetaVo GetBookMeta(Integer bookId, LoginUser loginUser);

    /** 单章正文 */
    ReaderChapterVo GetBookChapter(Integer bookId, Integer index);

    /** 保存书籍阅读进度 */
    void SaveBookProgress(Integer bookId, ReaderProgressDto dto, LoginUser loginUser);

    /* ==================== 二、用户上传的文件 ==================== */

    /** 上传我的 txt，返回新文件信息（前端要拿 id 直接跳阅读页） */
    ReaderFileVo UploadFile(MultipartFile file, String title, LoginUser loginUser);

    /** 我的文件列表（分页，只返回自己的） */
    readerFilePageResult GetMyFiles(ReaderFileSelectDto dto, LoginUser loginUser);

    /** 单个文件的基本信息 */
    ReaderFileVo GetFileInfo(Long fileId, LoginUser loginUser);

    /** 我的文件目录 */
    ReaderMetaVo GetFileMeta(Long fileId, LoginUser loginUser);

    /** 我的文件某一章 */
    ReaderChapterVo GetFileChapter(Long fileId, Integer index, LoginUser loginUser);

    /** 删除我的文件，连带清掉进度 */
    void DeleteFile(Long fileId, LoginUser loginUser);

    /** 保存文件阅读进度 */
    void SaveFileProgress(Long fileId, ReaderProgressDto dto, LoginUser loginUser);

    /** 重命名我的文件（只改标题，正文不动） */
    ReaderFileVo UpdateFileTitle(Long fileId, String title, LoginUser loginUser);
}
