package org.libaray_manager_system.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.libaray_manager_system.pojo.entity.Book;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * queryWithLogicalExpire（逻辑过期 + 互斥锁）的行为测试。
 *
 * <p>不需要 Redis：用一个内存版 StringRedisTemplate 顶替，逻辑完全一样（get / set / setIfAbsent / delete）。
 * 需要 MySQL 吗？也不需要，dbFallback 是调用方传进来的 Lambda，测试里自己控制。
 *
 * <p>跑法：mvn -Dtest=CacheUtilLogicalExpireTest test
 */
class CacheUtilLogicalExpireTest {

    private static final Integer ID = 1;
    private static final String KEY = CacheUtil.CACHE_BOOK_KEY + ID;
    /** 期望的锁 key：
     *  
     *  Cache:Book:Lock:1 —— 既要跟着业务前缀走，也要带 id，两本书不能共用一把锁 */
    private static final String BOOK_LOCK = CacheUtil.CACHE_BOOK_KEY + "Lock:" + ID;

    private FakeRedisTemplate redis;
    private CacheUtil cacheUtil;
    private ExecutorService rebuildExecutor;

    @BeforeEach
    void setUp() throws Exception {
        redis = new FakeRedisTemplate();
        cacheUtil = new CacheUtil(redis);
        // 这个线程池是 CacheUtil 的私有字段，测试结束要关掉，否则 JVM 里会一直挂着 10 个线程
        Field field = CacheUtil.class.getDeclaredField("CACHE_REBUILD_EXECUTOR");
        field.setAccessible(true);
        rebuildExecutor = (ExecutorService) field.get(cacheUtil);
    }

    @AfterEach
    void tearDown() {
        if (rebuildExecutor != null) {
            rebuildExecutor.shutdownNow();
        }
        redis.store.clear();
    }

    /* ==================== 用例 1：没到期 ==================== */

    @Test
    @DisplayName("逻辑未过期：直接返回缓存，不回源、不抢锁")
    void notExpired() {
        cacheUtil.setWithLogicalExpire(KEY, book("旧书名"), 10L, TimeUnit.MINUTES);

        AtomicInteger db = new AtomicInteger();
        Book got = cacheUtil.queryWithLogicalExpire(CacheUtil.CACHE_BOOK_KEY, ID, Book.class,
                id -> {
                    db.incrementAndGet();
                    return book("新书名");
                }, 10L, TimeUnit.MINUTES);

        assertNotNull(got);
        assertEquals("旧书名", got.getName());
        assertEquals(0, db.get(), "没过期还查库，说明判断写反了");
        assertFalse(redis.store.containsKey(BOOK_LOCK));
    }

    /* ==================== 用例 2：过期了，异步重建 ==================== */

    @Test
    @DisplayName("逻辑已过期：请求立刻拿到旧数据，后台异步重建，结束后释放锁")
    void expiredReturnsOldThenRebuildsAsync() throws Exception {
        cacheUtil.setWithLogicalExpire(KEY, book("旧书名"), 1L, TimeUnit.SECONDS);
        Thread.sleep(1200);   // 让逻辑时间过去，但物理 key 还在（逻辑过期的核心）

        AtomicInteger db = new AtomicInteger();
        long start = System.currentTimeMillis();
        Book got = cacheUtil.queryWithLogicalExpire(CacheUtil.CACHE_BOOK_KEY, ID, Book.class,
                id -> {
                    db.incrementAndGet();
                    sleep(300);           // 人为让"查库"变慢，用来证明它没有阻塞当前请求
                    return book("新书名");
                }, 10L, TimeUnit.MINUTES);
        long cost = System.currentTimeMillis() - start;

        assertEquals("旧书名", got.getName(), "过期后应该先返回旧数据");
        assertTrue(cost < 200, "重建必须异步，不能阻塞请求；实际耗时 " + cost + "ms");

        awaitCacheContains(KEY, "新书名", 3000);
        assertEquals(1, db.get());
        assertFalse(redis.store.containsKey(BOOK_LOCK), "重建完成后锁必须被释放");
    }

    /* ==================== 用例 3：并发打过期 key ==================== */

    @Test
    @DisplayName("20 个并发请求同时撞上过期：数据库只能被查 1 次")
    void concurrentOnlyOneRebuild() throws Exception {
        cacheUtil.setWithLogicalExpire(KEY, book("旧书名"), 1L, TimeUnit.SECONDS);
        Thread.sleep(1200);

        AtomicInteger db = new AtomicInteger();
        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    cacheUtil.queryWithLogicalExpire(CacheUtil.CACHE_BOOK_KEY, ID, Book.class,
                            id -> {
                                db.incrementAndGet();
                                sleep(200);
                                return book("新书名");
                            }, 10L, TimeUnit.MINUTES);
                } catch (Exception ignored) {
                } finally {
                    done.countDown();
                }
            });
        }

        assertTrue(ready.await(5, TimeUnit.SECONDS));
        go.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS), "并发请求没有全部返回");
        pool.shutdownNow();

        awaitCacheContains(KEY, "新书名", 3000);
        assertEquals(1, db.get(), "有互斥锁时，20 个并发请求只允许回源 1 次");
    }

    /* ==================== 用例 4：缓存根本不存在 ==================== */

    @Test
    @DisplayName("缓存不存在（首次访问/被删/被淘汰）：应该回源并重建，而不是直接返回 null")
    void cacheMissShouldFallbackToDb() {
        AtomicInteger db = new AtomicInteger();

        Book got = cacheUtil.queryWithLogicalExpire(CacheUtil.CACHE_BOOK_KEY, ID, Book.class,
                id -> {
                    db.incrementAndGet();
                    return book("库里的书");
                }, 10L, TimeUnit.MINUTES);

        assertNotNull(got, "逻辑过期方案必须处理'缓存未命中'，否则新书/被删的缓存永远查不到");
        assertEquals("库里的书", got.getName());
        assertEquals(1, db.get());
    }

    @Test
    @DisplayName("未命中且库里也没有：空值标记要写成普通空串，且第二次请求不能炸")
    void missWithNoDbRowWritesPlainEmptyString() {
        Book first = cacheUtil.queryWithLogicalExpire(CacheUtil.CACHE_BOOK_KEY, ID, Book.class,
                id -> null, 10L, TimeUnit.MINUTES);
        assertNull(first);

        assertEquals("", redis.store.get(KEY),
                "空值标记应该直接是空串（和 queryWithPassThrough 一致），不能包成 RedisData；现在写进去的是："
                        + redis.store.get(KEY));

        assertDoesNotThrow(
                () -> cacheUtil.queryWithLogicalExpire(CacheUtil.CACHE_BOOK_KEY, ID, Book.class, id -> null, 10L, TimeUnit.MINUTES),
                "第二次读取空值标记时不能抛异常");
    }

    /* ==================== 用例 5：重建时库里已经没有 ==================== */

    @Test
    @DisplayName("重建时数据库已删书（db 返回 null）：不应把 null 塞进缓存，也不应让后续请求 500")
    void rebuildWithNullFromDb() throws Exception {
        cacheUtil.setWithLogicalExpire(KEY, book("旧书名"), 1L, TimeUnit.SECONDS);
        Thread.sleep(1200);

        cacheUtil.queryWithLogicalExpire(CacheUtil.CACHE_BOOK_KEY, ID, Book.class, id -> null, 10L, TimeUnit.MINUTES);
        Thread.sleep(600);   // 等异步重建写进去

        assertEquals("", redis.store.get(KEY),
                "重建时库里没书，也应该写普通空串，不能写成 {\"data\":null}；现在是：" + redis.store.get(KEY));

        assertDoesNotThrow(
                () -> cacheUtil.queryWithLogicalExpire(CacheUtil.CACHE_BOOK_KEY, ID, Book.class, id -> null, 10L, TimeUnit.MINUTES),
                "缓存里 data 为 null 时再读，会抛 ClassCastException / NPE");
    }

    /* ==================== 用例 6：锁的 key 前缀 ==================== */

    @Test
    @DisplayName("锁的 key 应该跟着传入的 keyPrefix 走，不能写死成 Cache:Book:Lock:")
    void lockKeyShouldFollowKeyPrefix() throws Exception {
        String noticePrefix = "Cache:Notice:";
        String noticeKey = noticePrefix + 7;
        cacheUtil.setWithLogicalExpire(noticeKey, book("旧通知"), 1L, TimeUnit.SECONDS);
        Thread.sleep(1200);

        AtomicReference<Set<String>> keysDuringRebuild = new AtomicReference<>();
        cacheUtil.queryWithLogicalExpire(noticePrefix, 7, Book.class,
                id -> {
                    keysDuringRebuild.set(new HashSet<>(redis.store.keySet()));   // 重建线程里看一眼 Redis 有什么 key
                    return book("新通知");
                }, 10L, TimeUnit.MINUTES);

        awaitCacheContains(noticeKey, "新通知", 3000);

        assertTrue(keysDuringRebuild.get().contains("Cache:Notice:Lock:7"),
                "重建期间应该看到 Cache:Notice:Lock:7，实际看到：" + keysDuringRebuild.get());
    }

    /* ==================== 工具 ==================== */

    private Book book(String name) {
        Book b = new Book();
        b.setId(ID);
        b.setIsbn("9787111544937");
        b.setName(name);
        b.setAuthor("Randal E. Bryant");
        b.setPublisher("机械工业出版社");
        b.setPublishDate(LocalDate.of(2016, 11, 1));
        b.setCategoryId(3);
        b.setCategoryName("计算机");
        b.setPrice(new BigDecimal("99.50"));
        b.setTotalNum(5);
        b.setAvailableNum(new BigDecimal("4"));
        b.setStatus(1);
        b.setCreateTime(LocalDateTime.of(2026, 10, 1, 8, 30, 45));
        return b;
    }

    private void awaitCacheContains(String key, String needle, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            String value = redis.store.get(key);
            if (value != null && value.contains(needle)) {
                return;
            }
            Thread.sleep(20);
        }
        fail("等待缓存重建超时，当前缓存内容：" + redis.store.get(key));
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 内存版 Redis，只实现 CacheUtil 用到的那几个命令。
     * 这样测试不需要真 Redis，也能真并发地跑。
     */
    static class FakeRedisTemplate extends StringRedisTemplate {

        final Map<String, String> store = new ConcurrentHashMap<>();

        @Override
        @SuppressWarnings("unchecked")
        public ValueOperations<String, String> opsForValue() {
            return (ValueOperations<String, String>) Proxy.newProxyInstance(
                    FakeRedisTemplate.class.getClassLoader(),
                    new Class<?>[]{ValueOperations.class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "get":
                                return store.get((String) args[0]);
                            case "set":
                                store.put((String) args[0], (String) args[1]);
                                return null;
                            case "setIfAbsent":
                                return store.putIfAbsent((String) args[0], (String) args[1]) == null;
                            default:
                                throw new UnsupportedOperationException("测试替身没有实现：" + method.getName());
                        }
                    });
        }

        @Override
        public Boolean delete(String key) {
            return store.remove(key) != null;
        }
    }
}
