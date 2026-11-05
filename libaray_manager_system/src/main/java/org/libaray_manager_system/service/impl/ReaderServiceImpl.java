package org.libaray_manager_system.service.impl;

import org.libaray_manager_system.exception.BusinessException;
import org.libaray_manager_system.mapper.BookContentMapper;
import org.libaray_manager_system.mapper.BookMapper;
import org.libaray_manager_system.mapper.ReaderFileMapper;
import org.libaray_manager_system.mapper.ReaderProgressMapper;
import org.libaray_manager_system.pojo.dto.ReaderFileSelectDto;
import org.libaray_manager_system.pojo.dto.ReaderProgressDto;
import org.libaray_manager_system.pojo.entity.Book;
import org.libaray_manager_system.pojo.entity.BookContent;
import org.libaray_manager_system.pojo.entity.LoginUser;
import org.libaray_manager_system.pojo.entity.ReaderFile;
import org.libaray_manager_system.pojo.entity.ReaderProgress;
import org.libaray_manager_system.pojo.ReaderFilePageResult;
import org.libaray_manager_system.pojo.vo.ChapterItemVo;
import org.libaray_manager_system.pojo.vo.ReaderChapterVo;
import org.libaray_manager_system.pojo.vo.ReaderFileVo;
import org.libaray_manager_system.pojo.vo.ReaderMetaVo;
import org.libaray_manager_system.service.ReaderService;
import org.libaray_manager_system.utils.ChapterSplitUtils;
import org.libaray_manager_system.utils.TextDecodeUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class ReaderServiceImpl implements ReaderService {

    /** 阅读目标类型：1=书籍正文 2=用户上传的文件 */
    private static final int TARGET_BOOK = 1;//管理员上传
    private static final int TARGET_FILE = 2;//用户个人上传

    /** 单个 txt 上限，要和 application.yml 里的 multipart 一致 */
    private static final long MAX_BYTES = 10L * 1024 * 1024;

    /** title 列是 VARCHAR(200) */
    private static final int MAX_TITLE_LEN = 200;

    @Autowired private BookMapper bookMapper;
    @Autowired private BookContentMapper bookContentMapper;
    @Autowired private ReaderFileMapper readerFileMapper;
    @Autowired private ReaderProgressMapper readerProgressMapper;

    //校验可读性+提取正文
    private String GetReadableText(Integer id){
        Book book = bookMapper.GetBookById(id);
        if(book==null) throw new BusinessException("图书不存在");

        if (!Integer.valueOf(1).equals(book.getReadable())) {
            throw new BusinessException("这本书还没有开启在线阅读");
        }

        BookContent bc = bookContentMapper.GetByBookId(id);
        if(bc == null || bc.getContent()==null || bc.getContent().isBlank()) throw new BusinessException("这本书暂无正文阅读");
        return bc.getContent();
    }

    /* ============================================================
       一、书籍正文
       ============================================================ */

    @Override
    public void SetBookReadable(Integer bookId, Integer readable) {
        if (readable == null || (readable != 0 && readable != 1)) {
            throw new BusinessException("开关值只能是 0 或 1");
        }
        // 先查一次：UpdateReadable 返回 0 有两种可能（书不存在 / 值没变），分不清
        if (bookMapper.GetBookById(bookId) == null) {
            throw new BusinessException("图书不存在");
        }
        bookMapper.UpdateReadable(bookId, readable);
    }

    @Override
    @Transactional
    public void UploadBookContent(Integer bookId, MultipartFile file) {
        CheckTxt(file);
        if (bookMapper.GetBookById(bookId) == null) {
            throw new BusinessException("图书不存在");
        }

        TextDecodeUtils.Decoded decoded = Decode(file);

        BookContent content = new BookContent();
        content.setBookId(bookId);
        content.setContent(decoded.text());
        content.setWordCount(CountWords(decoded.text()));
        content.setEncoding(decoded.charset());
        bookContentMapper.Save(content);
    }

    @Override
    @Transactional
    public void DeleteBookContent(Integer bookId) {
        bookContentMapper.DeleteByBookId(bookId);
        // 正文没了，所有人对这本书的进度一起清掉
        readerProgressMapper.DeleteByTarget(TARGET_BOOK, bookId.longValue());
    }

    @Override
    public ReaderMetaVo GetBookMeta(Integer bookId, LoginUser loginUser) {
        Book book = bookMapper.GetBookById(bookId);
        if (book == null) {
            throw new BusinessException("图书不存在");
        }

        ReaderMetaVo vo = new ReaderMetaVo();
        vo.setTitle(book.getName());
        vo.setReadable(false);
        vo.setHasContent(false);
        vo.setLastChapter(0);
        vo.setChapterCount(0);
        vo.setWordCount(0);
        vo.setChapters(new ArrayList<>());

        BookContent content = bookContentMapper.GetByBookId(bookId);
        boolean hasContent = content != null
                && content.getContent() != null
                && !content.getContent().isBlank();
        vo.setHasContent(hasContent);

        // 从外到内依次校验（文档 7.2）：先开关、再正文
        if (!Integer.valueOf(1).equals(book.getReadable())) {
            vo.setDenyReason("这本书还没有开启在线阅读");
            // 章数字数照常给（管理端要显示"已上传 N 章 / M 字"），但目录不给
            if (hasContent) {
                vo.setWordCount(content.getWordCount());
                vo.setEncoding(content.getEncoding());
                vo.setChapterCount(SplitChapters(content.getContent()).size());
            }
            return vo;
        }
        if (!hasContent) {
            vo.setDenyReason("这本书暂无正文阅读");
            return vo;
        }

        List<ChapterItemVo> items = SplitChapters(content.getContent());
        vo.setReadable(true);
        vo.setWordCount(content.getWordCount());
        vo.setEncoding(content.getEncoding());
        vo.setChapterCount(items.size());
        vo.setChapters(items);

        ReaderProgress progress = readerProgressMapper.Get(loginUser.getId(), TARGET_BOOK, bookId.longValue());
        if (progress != null && progress.getChapterIndex() != null) {
            // 正文换过之后章节数可能变少，夹一下防越界
            vo.setLastChapter(Math.min(progress.getChapterIndex(), items.size() - 1));
            // 章内位置：这条进度记录的就是"上次读的那一章"，percent 一定对得上
            vo.setLastPercent(progress.getPercent() == null ? BigDecimal.ZERO : progress.getPercent());
        }
        return vo;
    }

    @Override
    public ReaderChapterVo GetBookChapter(Integer bookId, Integer index) {
        // 可读性校验直接复用上面那个方法
        return ReadChapter(GetReadableText(bookId), index);
    }

    @Override
    public void SaveBookProgress(Integer bookId, ReaderProgressDto dto, LoginUser loginUser) {
        SaveProgress(TARGET_BOOK, bookId.longValue(), dto, loginUser);
    }

    /* ============================================================
       二、用户上传的文件
       ============================================================ */

    @Override
    @Transactional
    public ReaderFileVo UploadFile(MultipartFile file, String title, LoginUser loginUser) {
        CheckTxt(file);
        TextDecodeUtils.Decoded decoded = Decode(file);

        ReaderFile entity = new ReaderFile();
        entity.setUserId(loginUser.getId());
        entity.setTitle(ResolveTitle(title, file.getOriginalFilename()));
        entity.setContent(decoded.text());
        entity.setWordCount(CountWords(decoded.text()));
        // 章数存下来：列表页要显示"42 章"，不存就得为每一行文件切一遍正文
        entity.setChapterCount(ChapterSplitUtils.split(decoded.text()).size());
        entity.setEncoding(decoded.charset());
        readerFileMapper.Save(entity);   // 自增 id 会回填到 entity.id

        return ToFileVo(entity);
    }

    @Override
    public ReaderFilePageResult GetMyFiles(ReaderFileSelectDto dto, LoginUser loginUser) {
        ReaderFileSelectDto query = dto == null ? new ReaderFileSelectDto() : dto;

        Integer total = readerFileMapper.GetMyFileCount(query, loginUser.getId());
        if (total == null || total == 0) {
            return new ReaderFilePageResult(0, new ArrayList<>());
        }

        List<ReaderFile> rows = readerFileMapper.GetMyFiles(query, loginUser.getId(), StartIndex(query));
        List<ReaderFileVo> list = new ArrayList<>(rows.size());
        for (ReaderFile row : rows) {
            list.add(ToFileVo(row));
        }
        return new ReaderFilePageResult(total, list);
    }

    @Override
    public ReaderFileVo GetFileInfo(Long fileId, LoginUser loginUser) {
        return ToFileVo(GetOwnFile(fileId, loginUser));
    }

    @Override
    public ReaderMetaVo GetFileMeta(Long fileId, LoginUser loginUser) {
        ReaderFile file = GetOwnFile(fileId, loginUser);
        List<ChapterItemVo> items = SplitChapters(file.getContent());

        ReaderMetaVo vo = new ReaderMetaVo();
        vo.setTitle(file.getTitle());
        // 自己传的文件永远能读 —— 没有"开关"这一说
        vo.setReadable(true);
        vo.setHasContent(true);
        vo.setChapterCount(items.size());
        vo.setWordCount(file.getWordCount());
        vo.setEncoding(file.getEncoding());
        vo.setChapters(items);

        ReaderProgress progress = readerProgressMapper.Get(loginUser.getId(), TARGET_FILE, fileId);
        if (progress != null && progress.getChapterIndex() != null) {
            vo.setLastChapter(Math.min(progress.getChapterIndex(), items.size() - 1));
            vo.setLastPercent(progress.getPercent() == null ? BigDecimal.ZERO : progress.getPercent());
        } else {
            vo.setLastChapter(0);
        }
        return vo;
    }

    @Override
    public ReaderChapterVo GetFileChapter(Long fileId, Integer index, LoginUser loginUser) {
        return ReadChapter(GetOwnFile(fileId, loginUser).getContent(), index);
    }

    @Override
    public ReaderFileVo UpdateFileTitle(Long fileId, String title, LoginUser loginUser) {
        ReaderFile file = GetOwnFile(fileId, loginUser);      // 归属校验

        String value = title == null ? "" : title.trim();
        if (value.isEmpty()) {
            throw new BusinessException("文件名不能为空");
        }
        if (value.length() > MAX_TITLE_LEN) {
            throw new BusinessException("文件名不能超过 " + MAX_TITLE_LEN + " 个字");
        }

        readerFileMapper.UpdateTitle(fileId, loginUser.getId(), value);
        file.setTitle(value);
        return ToFileVo(file);
    }

    @Override
    @Transactional
    public void DeleteFile(Long fileId, LoginUser loginUser) {
        GetOwnFile(fileId, loginUser);          // 先确认归属
        readerFileMapper.DeleteByIdAndUser(fileId, loginUser.getId());
        readerProgressMapper.DeleteByTarget(TARGET_FILE, fileId);
    }

    @Override
    public void SaveFileProgress(Long fileId, ReaderProgressDto dto, LoginUser loginUser) {
        GetOwnFile(fileId, loginUser);          // 同上
        SaveProgress(TARGET_FILE, fileId, dto, loginUser);
    }

    /* ============================================================
       私有方法：两条路共用的部分
       ============================================================ */

    /**
     * 取用户自己的文件 —— 文件那条路唯一的一道门。
     * SQL 里带了 user_id，查不到就抛"文件不存在"（故意不区分"不存在"和"不是你的"）。
     */
    private ReaderFile GetOwnFile(Long fileId, LoginUser loginUser) {
        if (fileId == null) {
            throw new BusinessException("文件不存在");
        }
        ReaderFile file = readerFileMapper.GetByIdAndUser(fileId, loginUser.getId());
        if (file == null) {
            throw new BusinessException("文件不存在");
        }
        return file;
    }

    /** 从整篇正文里截出第 index 章 */
    private ReaderChapterVo ReadChapter(String text, Integer index) {
        List<ChapterSplitUtils.Chapter> chapters = ChapterSplitUtils.split(text);
        if (index == null || index < 0 || index >= chapters.size()) {
            throw new BusinessException("章节不存在");
        }

        ChapterSplitUtils.Chapter chapter = chapters.get(index);
        ReaderChapterVo vo = new ReaderChapterVo();
        vo.setIndex(index);
        vo.setTitle(chapter.title());
        // 正文区间不含标题行 —— 标题已单独返回，否则页面上会显示两遍
        vo.setContent(text.substring(chapter.contentstart(), chapter.contentend()).strip());
        vo.setPrevIndex(index > 0 ? index - 1 : null);
        vo.setNextIndex(index + 1 < chapters.size() ? index + 1 : null);
        return vo;
    }

    /** 切章并转成目录 VO（书籍 meta 和文件 meta 共用） */
    private List<ChapterItemVo> SplitChapters(String text) {
        List<ChapterSplitUtils.Chapter> chapters = ChapterSplitUtils.split(text);
        List<ChapterItemVo> items = new ArrayList<>(chapters.size());
        for (int i = 0; i < chapters.size(); i++) {
            ChapterSplitUtils.Chapter chapter = chapters.get(i);
            items.add(new ChapterItemVo(
                    i,
                    chapter.title(),
                    Math.max(0, chapter.contentend() - chapter.contentstart())));
        }
        return items;
    }

    /** 写进度（书籍和文件共用）；userId 只从 token 来，绝不信前端 */
    private void SaveProgress(int targetType, Long targetId, ReaderProgressDto dto, LoginUser loginUser) {
        if (dto == null || dto.getChapterIndex() == null || dto.getChapterIndex() < 0) {
            throw new BusinessException("章节序号不合法");
        }

        ReaderProgress progress = new ReaderProgress();
        progress.setUserId(loginUser.getId());
        progress.setTargetType(targetType);
        progress.setTargetId(targetId);
        progress.setChapterIndex(dto.getChapterIndex());
        progress.setChapterTitle(dto.getChapterTitle());
        progress.setPercent(dto.getPercent() == null ? BigDecimal.ZERO : dto.getPercent());
        readerProgressMapper.Save(progress);
    }

    /* ---------------- 上传相关的几个小工具 ---------------- */

    /** 文件本身合不合法：空文件 / 扩展名 / 大小 */
    private void CheckTxt(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的 txt 文件");
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase().endsWith(".txt")) {
            throw new BusinessException("只支持 .txt 文件");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException("文件不能超过 10MB");
        }
    }

    /** 拿字节 → 检测编码 → 统一换行（Windows 的 txt 全是 \r\n） */
    private TextDecodeUtils.Decoded Decode(MultipartFile file) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException("读取上传文件失败");
        }

        TextDecodeUtils.Decoded decoded = TextDecodeUtils.decode(bytes);
        String text = decoded.text().replace("\r\n", "\n").replace('\r', '\n');
        if (text.isBlank()) {
            throw new BusinessException("文件内容为空");
        }
        return new TextDecodeUtils.Decoded(text, decoded.charset());
    }

    /** 标题：用户填了用用户的，否则取文件名（去 .txt）；VARCHAR(200) 要截断 */
    private String ResolveTitle(String title, String originalFilename) {
        String value = title;
        if (value == null || value.isBlank()) {
            if (originalFilename == null || originalFilename.isBlank()) {
                value = "未命名";
            } else {
                int dot = originalFilename.lastIndexOf('.');
                value = dot > 0 ? originalFilename.substring(0, dot) : originalFilename;
            }
        }
        value = value.trim();
        if (value.isEmpty()) {
            value = "未命名";
        }
        return value.length() > MAX_TITLE_LEN ? value.substring(0, MAX_TITLE_LEN) : value;
    }

    /** 字数 = 去掉所有空白字符后的字符数 */
    private Integer CountWords(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isWhitespace(text.charAt(i))) {
                count++;
            }
        }
        return count;
    }

    /** 分页偏移量：顺手兜住 null 和非法值，不让 (pageNum-1)*pageSize 炸掉 */
    private Integer StartIndex(ReaderFileSelectDto dto) {
        int pageNum = (dto.getPageNum() == null || dto.getPageNum() < 1) ? 1 : dto.getPageNum();
        int pageSize = (dto.getPageSize() == null || dto.getPageSize() < 1) ? 10 : dto.getPageSize();
        return (pageNum - 1) * pageSize;
    }

    /** 实体 → 列表项 VO */
    private ReaderFileVo ToFileVo(ReaderFile file) {
        ReaderFileVo vo = new ReaderFileVo();
        vo.setId(file.getId());
        vo.setTitle(file.getTitle());
        vo.setWordCount(file.getWordCount());
        vo.setChapterCount(file.getChapterCount());
        vo.setCreateTime(file.getCreateTime());
        return vo;
    }
}
