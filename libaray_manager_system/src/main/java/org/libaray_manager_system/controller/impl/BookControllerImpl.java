package org.libaray_manager_system.controller.impl;

import lombok.extern.slf4j.Slf4j;
import org.libaray_manager_system.annotation.RequireAdmin;
import org.libaray_manager_system.controller.BookController;
import org.libaray_manager_system.pojo.Result;
import org.libaray_manager_system.pojo.dto.BookQuerySelectDto;
import org.libaray_manager_system.pojo.dto.BookSaveDto;
import org.libaray_manager_system.service.BookService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 图书接口（1.0 的 8 个接口，2.0 加了权限）
 *
 * <p><b>2.0 的权限规则（接口文档 5 节）：</b>
 * <table border="1">
 *   <tr><th>接口</th><th>谁能用</th><th>怎么实现</th></tr>
 *   <tr><td>GET /categories、GET /books、GET /books/{id}</td>
 *       <td>登录就能用（含普通读者）</td><td>什么都不用加</td></tr>
 *   <tr><td>POST /books、PUT /books/{id}、DELETE /books/{id}、PATCH /books/{id}/status</td>
 *       <td>只有 ADMIN</td><td>方法上加 {@code @RequireAdmin}</td></tr>
 * </table>
 *
 * <p>⚠️ <b>为什么不用路径通配符拦管理接口？</b>
 * 因为 Spring 的 {@code addPathPatterns("/books/*")} <b>不带 HTTP 方法</b>，
 * 它会同时匹配：
 * <ul>
 *   <li>{@code PUT /books/1}（要 ADMIN ✅）</li>
 *   <li>{@code GET /books/1}（普通读者看详情，<b>绝对不能拦</b> ❌）</li>
 * </ul>
 * 一旦那么配，普通读者点「详情」就被 403，而且报错信息看起来毫无道理，极难排查。
 * 用 {@code @RequireAdmin} 注解按方法精确控制，就没有这个歧义 ——
 * 毕竟注解是标在"哪个方法"上的，而路径模式只认 URL。
 */
@RestController
@Slf4j
public class BookControllerImpl implements BookController {

    @Autowired
    private BookService bookService;

    /* ==================== 只读接口：登录即可 ==================== */

    @GetMapping("/categories")
    public Result GetAllCategories() {
        Result result = new Result();
        return result.success(bookService.GetAllCategories());
    }

    @GetMapping("/books")
    public Result GetAllBooks(BookQuerySelectDto bookQuerySelectDto) {
        Result result = new Result();
        return result.success(bookService.GetAllBooks(bookQuerySelectDto));
    }

    /** 查详情 —— 普通读者的核心功能，只要求登录，不加 @RequireAdmin */
    @GetMapping("/books/{id}")
    public Result GetBookById(@PathVariable Integer id) {
        Result result = new Result();
        return result.success(bookService.GetBookById(id));
    }

    /* ==================== 写接口：要 ADMIN ==================== */

    /**
     * 新增图书（接口 4）—— 要 ADMIN
     *
     * <p>路径是复数的 {@code /books}，和「查询列表」共用同一个 URL，
     * 靠 HTTP 方法区分：GET 查列表，POST 新增（RESTful 的标准做法）。
     *
     * <p>{@code @RequestBody} 表示参数从 JSON 请求体里读，不是从 URL 参数里读。
     *
     * <p>这里看不到 try/catch，是因为校验和业务规则都在 Service 里，
     * 失败时抛 BusinessException，由 GlobalExceptionHandler 统一转成
     * {@code {code:0, msg:"该 ISBN 已存在"}}。Controller 只管收和转交。
     */
    @RequireAdmin
    @PostMapping("/books")
    public Result AddBook(@RequestBody BookSaveDto bookSaveDto) {
        bookService.AddBook(bookSaveDto);
        return new Result().success("新增成功", null);
    }

    @RequireAdmin
    @PutMapping("/books/{id}")
    public Result UpdateBook(@PathVariable Integer id, @RequestBody BookSaveDto bookSaveDto) {
        Result result = new Result();
        bookService.UpdateBook(id, bookSaveDto);
        return result.success("修改成功", null);
    }

    /** 删除图书 —— 要 ADMIN */
    @RequireAdmin
    @DeleteMapping("/books/{id}")
    public Result DeleteBook(@PathVariable Integer id) {
        Result result = new Result();
        bookService.DeleteBook(id);
        return result.success("删除成功", null);
    }

    /** 上架 / 下架图书 —— 要 ADMIN */
    @RequireAdmin
    @PatchMapping("/books/{id}/status")
    public Result UpdateBookStatus(@PathVariable Integer id, @RequestParam Integer status) {
        return new Result().success(bookService.UpdateBookStatus(id, status), null);
    }
}
