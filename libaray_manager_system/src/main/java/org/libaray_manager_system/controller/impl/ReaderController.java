package org.libaray_manager_system.controller.impl;

import org.libaray_manager_system.annotation.RequireAdmin;
import org.libaray_manager_system.pojo.Result;
import org.libaray_manager_system.pojo.dto.ReaderFileSelectDto;
import org.libaray_manager_system.pojo.dto.ReaderProgressDto;
import org.libaray_manager_system.pojo.entity.LoginUser;
import org.libaray_manager_system.service.ReaderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 在线阅读（4.0）—— 13 个端点
 *
 * 两组：
 *   /reader/book/**  管理员上传的书籍正文，读者看 readable 开关
 *   /reader/file/**  用户自己上传的 txt，只有本人能读
 *
 * ⚠️ 所有路径都在 /reader/** 下，被 AuthInterceptor 的 /** 规则覆盖，必须带 token。
 *    只有 1、2、3 三个额外加了 @RequireAdmin。
 */
@RestController
public class ReaderController {

    @Autowired
    private ReaderService readerService;

    /* ============================================================
       一、书籍正文 —— 管理员
       ============================================================ */

    // 1. 打开 / 关闭某本书的在线阅读     body: { "readable": 1 }
    @RequireAdmin
    @PutMapping("/books/{id}/readable")
    public Result SetBookReadable(@PathVariable Integer id,
                                  @RequestBody Map<String, Integer> body) {
        readerService.SetBookReadable(id, body == null ? null : body.get("readable"));
        return new Result().success("操作成功", null);
    }

    // 2. 上传 / 替换正文。★ 必须 multipart，前端传【原始字节】，不要在浏览器里先解码
    @RequireAdmin
    @PostMapping("/reader/book/{bookId}/content")
    public Result UploadBookContent(@PathVariable Integer bookId,
                                    @RequestParam("file") MultipartFile file) {
        readerService.UploadBookContent(bookId, file);
        return new Result().success("正文已上传", null);
    }

    // 3. 删除正文（连带清掉所有人的进度）
    @RequireAdmin
    @DeleteMapping("/reader/book/{bookId}/content")
    public Result DeleteBookContent(@PathVariable Integer bookId) {
        readerService.DeleteBookContent(bookId);
        return new Result().success("正文已删除", null);
    }

    /* ============================================================
       二、书籍阅读 —— 所有登录用户
       ============================================================ */

    // 4. 目录 + 能不能读（不含正文）
    @GetMapping("/reader/book/{bookId}/meta")
    public Result GetBookMeta(@PathVariable Integer bookId,
                              @RequestAttribute("loginUser") LoginUser loginUser) {
        return new Result().success(readerService.GetBookMeta(bookId, loginUser));
    }

    // 5. 单章正文：一次只给一章，别把整本塞进 DOM
    @GetMapping("/reader/book/{bookId}/chapter/{index}")
    public Result GetBookChapter(@PathVariable Integer bookId,
                                 @PathVariable Integer index) {
        return new Result().success(readerService.GetBookChapter(bookId, index));
    }

    // 6. 保存书籍阅读进度
    @PutMapping("/reader/book/{bookId}/progress")
    public Result SaveBookProgress(@PathVariable Integer bookId,
                                   @RequestBody ReaderProgressDto dto,
                                   @RequestAttribute("loginUser") LoginUser loginUser) {
        readerService.SaveBookProgress(bookId, dto, loginUser);
        return new Result().success("进度已保存", null);
    }

    /* ============================================================
       三、我的 txt 文件
       ============================================================ */

    // 7. 上传我的 txt：file 必填，title 可选（不传就用文件名）
    //    返回里带 id，前端拿到后要立刻跳 /read/file/{id}
    @PostMapping("/reader/file")
    public Result UploadFile(@RequestParam("file") MultipartFile file,
                             @RequestParam(value = "title", required = false) String title,
                             @RequestAttribute("loginUser") LoginUser loginUser) {
        return new Result().success(readerService.UploadFile(file, title, loginUser));
    }

    // 8. 我的文件列表（分页）：查谁由 token 决定，不接受 userId 参数
    @GetMapping("/reader/file")
    public Result GetMyFiles(ReaderFileSelectDto dto,
                             @RequestAttribute("loginUser") LoginUser loginUser) {
        return new Result().success(readerService.GetMyFiles(dto, loginUser));
    }

    // 9. 单个文件的基本信息
    @GetMapping("/reader/file/{id}")
    public Result GetFileInfo(@PathVariable Long id,
                              @RequestAttribute("loginUser") LoginUser loginUser) {
        return new Result().success(readerService.GetFileInfo(id, loginUser));
    }

    // 10. 我的文件目录
    @GetMapping("/reader/file/{id}/meta")
    public Result GetFileMeta(@PathVariable Long id,
                              @RequestAttribute("loginUser") LoginUser loginUser) {
        return new Result().success(readerService.GetFileMeta(id, loginUser));
    }

    // 11. 我的文件某一章
    @GetMapping("/reader/file/{id}/chapter/{index}")
    public Result GetFileChapter(@PathVariable Long id,
                                 @PathVariable Integer index,
                                 @RequestAttribute("loginUser") LoginUser loginUser) {
        return new Result().success(readerService.GetFileChapter(id, index, loginUser));
    }

    // 12. 删除我的文件（连带清掉进度）
    @DeleteMapping("/reader/file/{id}")
    public Result DeleteFile(@PathVariable Long id,
                             @RequestAttribute("loginUser") LoginUser loginUser) {
        readerService.DeleteFile(id, loginUser);
        return new Result().success("已删除", null);
    }

    // 13. 保存文件阅读进度
    @PutMapping("/reader/file/{id}/progress")
    public Result SaveFileProgress(@PathVariable Long id,
                                   @RequestBody ReaderProgressDto dto,
                                   @RequestAttribute("loginUser") LoginUser loginUser) {
        readerService.SaveFileProgress(id, dto, loginUser);
        return new Result().success("进度已保存", null);
    }

    // 14. 重命名我的文件     body: { "title": "新名字" }
    @PutMapping("/reader/file/{id}")
    public Result UpdateFileName(@PathVariable Long id,
                                 @RequestBody Map<String, String> body,
                                 @RequestAttribute("loginUser") LoginUser loginUser) {
        String title = body == null ? null : body.get("title");
        return new Result().success(readerService.UpdateFileTitle(id, title, loginUser));
    }
}
