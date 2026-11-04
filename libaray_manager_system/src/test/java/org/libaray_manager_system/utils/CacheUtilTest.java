package org.libaray_manager_system.utils;

import cn.hutool.json.JSONUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.libaray_manager_system.pojo.entity.Book;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CacheUtil 的纯单元测试。
 *
 * <p>特点：<b>不需要 Redis，也不需要 MySQL，更不用启动 Spring</b>。
 * 用 Mockito 假装一个 StringRedisTemplate，把"缓存里有什么"直接喂进去，
 * 再把"数据库被调了几次"数出来 —— 这正是判断缓存是否真的生效的唯一硬证据。
 *
 * <p>跑法：
 * <pre>
 *   mvn -Dtest=CacheUtilTest test
 * </pre>
 * 或者在 IDEA 里点类名左边的绿色三角。
 */
class CacheUtilTest {

    /** 和 CacheUtil 里拼 key 的规则一致：Cache:Book:1 */
    private static final String KEY = CacheUtil.CACHE_BOOK_KEY + 1;
    private static final Integer BOOK_ID = 1;

    private StringRedisTemplate template;
    private ValueOperations<String, String> ops;
    private CacheUtil cacheUtil;

    @BeforeEach
    void setUp() {
        template = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> mockOps = mock(ValueOperations.class);
        ops = mockOps;
        when(template.opsForValue()).thenReturn(ops);
        cacheUtil = new CacheUtil(template);
    }

    private Book sampleBook() {
        Book book = new Book();
        book.setId(BOOK_ID);
        book.setIsbn("9787111544937");
        book.setName("深入理解计算机系统");
        book.setAuthor("Randal E. Bryant");
        book.setPublisher("机械工业出版社");
        book.setPublishDate(LocalDate.of(2016, 11, 1));
        book.setCategoryId(3);
        book.setCategoryName("计算机");
        book.setPrice(new BigDecimal("99.50"));
        book.setTotalNum(5);
        book.setAvailableNum(new BigDecimal("4"));
        book.setStatus(1);
        book.setCreateTime(LocalDateTime.of(2026, 10, 1, 8, 30, 45));
        book.setUpdateTime(LocalDateTime.of(2026, 10, 2, 9, 0, 0));
        return book;
    }

    /* ==================== 1. 缓存命中 ==================== */

    @Test
    @DisplayName("命中缓存：不查库，且能原样还原 Book（含 LocalDate / LocalDateTime / BigDecimal）")
    void cacheHit() {
        Book cached = sampleBook();
        // 假装 Redis 里已经有这本书的 JSON
        when(ops.get((Object) KEY)).thenReturn(JSONUtil.toJsonStr(cached));

        AtomicInteger dbCalls = new AtomicInteger();
        Book result = cacheUtil.queryWithPassThrough(
                CacheUtil.CACHE_BOOK_KEY, BOOK_ID, Book.class,
                id -> {
                    dbCalls.incrementAndGet();
                    return null;   // 一旦回源就会被抓到
                },
                CacheUtil.CACHE_BOOK_TIME, TimeUnit.MINUTES);

        assertEquals(0, dbCalls.get(), "命中缓存时绝不能再查数据库");
        assertNotNull(result);
        assertEquals("深入理解计算机系统", result.getName());
        assertEquals(0, new BigDecimal("99.50").compareTo(result.getPrice()));
        assertEquals(LocalDate.of(2016, 11, 1), result.getPublishDate());
        assertEquals(LocalDateTime.of(2026, 10, 1, 8, 30, 45), result.getCreateTime());
        assertEquals(new BigDecimal("4"), result.getAvailableNum());
        // 命中时不应该再写缓存
        verify(ops, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
    }

    /* ==================== 2. 命中"空值缓存" ==================== */

    @Test
    @DisplayName("命中的是空值缓存（value 为空串）：直接返回 null，不查库 —— 这就是穿透防护的核心")
    void cacheHitNullValue() {
        when(ops.get((Object) KEY)).thenReturn("");

        AtomicInteger dbCalls = new AtomicInteger();
        Book result = cacheUtil.queryWithPassThrough(
                CacheUtil.CACHE_BOOK_KEY, BOOK_ID, Book.class,
                id -> {
                    dbCalls.incrementAndGet();
                    return null;
                },
                CacheUtil.CACHE_BOOK_TIME, TimeUnit.MINUTES);

        assertNull(result);
        assertEquals(0, dbCalls.get(), "空值缓存命中时，那个不存在的 id 也不该再打数据库");
        verify(ops, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
    }

    /* ==================== 3. 未命中 + 库里有 ==================== */

    @Test
    @DisplayName("未命中且库里有：回源一次，并把 JSON 回填缓存")
    void cacheMissAndDbHit() {
        when(ops.get((Object) KEY)).thenReturn(null);
        Book dbBook = sampleBook();
        AtomicInteger dbCalls = new AtomicInteger();

        Book result = cacheUtil.queryWithPassThrough(
                CacheUtil.CACHE_BOOK_KEY, BOOK_ID, Book.class,
                id -> {
                    dbCalls.incrementAndGet();
                    return dbBook;
                },
                CacheUtil.CACHE_BOOK_TIME, TimeUnit.MINUTES);

        assertEquals(1, dbCalls.get());
        assertEquals("深入理解计算机系统", result.getName());

        // 断言回填的内容和 TTL：10 分钟
        org.mockito.ArgumentCaptor<String> captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(ops).set(eq(KEY), captor.capture(), eq(CacheUtil.CACHE_BOOK_TIME), eq(TimeUnit.MINUTES));

        Book written = JSONUtil.toBean(captor.getValue(), Book.class);
        assertEquals(dbBook.getName(), written.getName());
        assertEquals(dbBook.getCreateTime(), written.getCreateTime());
    }

    /* ==================== 4. 未命中 + 库里也没有（穿透场景） ==================== */

    @Test
    @DisplayName("未命中且库里也没有：写入空串缓存，且 TTL 必须是“短 TTL”（不是 10 分钟）")
    void cacheMissAndDbMiss() {
        when(ops.get((Object) KEY)).thenReturn(null);
        AtomicInteger dbCalls = new AtomicInteger();

        Book result = cacheUtil.queryWithPassThrough(
                CacheUtil.CACHE_BOOK_KEY, BOOK_ID, Book.class,
                id -> {
                    dbCalls.incrementAndGet();
                    return null;
                },
                CacheUtil.CACHE_BOOK_TIME, TimeUnit.MINUTES);

        assertNull(result);
        assertEquals(1, dbCalls.get());

        org.mockito.ArgumentCaptor<String> captor = org.mockito.ArgumentCaptor.forClass(String.class);
        // 关键断言：空值缓存用的是"短 TTL"（当前约定 30 秒），绝不是业务缓存的 10 分钟
        verify(ops).set(eq(KEY), captor.capture(), eq(CacheUtil.CACHE_NULL_TIME), eq(TimeUnit.SECONDS));
        assertEquals("", captor.getValue());
    }
}
