package org.libaray_manager_system.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.libaray_manager_system.pojo.Cache.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

@Component
@Slf4j
public class CacheUtil {
    //cache前缀定义
    //默认缓存前缀
    private static final String DEFAULT_CACHE_KEY = "Cache:";
    //空值缓存时长（秒）
    public static final Long CACHE_NULL_TIME = 30L;
    //书本缓存时长
    public static final Long CACHE_BOOK_TIME = 10L;//10分钟
    //互斥锁时长（秒）
    public static final Long CACHE_LOCK_TIME = 10L;
    //书本种类缓存时长(分钟)
    public static final Long CACHE_CATEGORY_TIME = 30L;
    //通知缓存时长（分钟）
    public static final Long CACHE_NOTICE_TIME = 10L;
    //书本缓存前缀
    public static final String CACHE_BOOK_KEY = DEFAULT_CACHE_KEY + "Book:";
    //互斥锁前缀
    public static final String CACHE_LOCK_KEY = DEFAULT_CACHE_KEY + "Lock:";
    //书本种类缓存前缀
    public static final String CACHE_CATEGORY_KEY = DEFAULT_CACHE_KEY + "Category:all";
    //通知缓存前缀
    public static final String CACHE_NOTICE_KEY = DEFAULT_CACHE_KEY + "Notice:";



    private final StringRedisTemplate stringRedisTemplate;//字符串模板
    //线程池
    private final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public CacheUtil(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }


    //设置普通过期时间
    public void Set(String key, Object value, Long time, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, timeUnit);
    }

    //设置逻辑过期时间
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit timeUnit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        //2
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 解决缓存穿透：缓存空值。
     */
    public <R, ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long expireTime, TimeUnit timeUnit) {
        String key = keyPrefix + id;
        //1.从缓存查数据
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if (StrUtil.isNotBlank(json)) {
            //存在->反序列化
            log.info("[缓存] 命中 key={}", key);
            return JSONUtil.toBean(json, type);
        }
        //3.缓存里是空串 -> 说明数据库里确定没有，直接返回 null，不再打数据库
        if (json != null) {
            log.info("[缓存] 命中空值 key={}", key);
            return null;
        }
        //4.未命中 -> 回源数据库
        log.info("[缓存] 未命中，回源数据库 key={}", key);
        R r = dbFallback.apply(id);
        //5.数据库也查不到？->设置空值缓存，并【立刻返回】
        if (r == null) {
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TIME, TimeUnit.SECONDS);
            log.info("[缓存] 数据库也没有，写入空值缓存 key={} ttl={} {}", key, CACHE_NULL_TIME, TimeUnit.SECONDS);
            return null;
        }
        //6.数据库查到了->设置缓存
        Set(key, r, expireTime, timeUnit);
        log.info("[缓存] 回填成功 key={} ttl={} {}", key, expireTime, timeUnit);
        //7.返回数据
        return r;
    }

    public <T> List<T> queryListWithPassThrough(String key, Class<T> elementType, Supplier<List<T>> dbFallback, Long expireTime, TimeUnit timeUnit) {
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toList(json, elementType);
        }
        List<T> list = dbFallback.get();
        if (list == null || list.isEmpty()) {
            // 空列表也要缓存，别让"空表"把库打穿
            stringRedisTemplate.opsForValue().set(key, "[]", CACHE_NULL_TIME, TimeUnit.SECONDS);
            return list == null ? Collections.emptyList() : list;
        }
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(list), expireTime, timeUnit);
        return list;
    }

    /*
    * 解决缓存击穿：缓存热点数据。
    * */
    //获取互斥锁
    public boolean islock (String lockKey){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(lockKey,"",CACHE_LOCK_TIME, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    //释放互斥锁
    public void unlock(String lockKey){
        stringRedisTemplate.delete(lockKey);
    }
    //运用逻辑过期时间+互斥锁解决缓存击穿问题
    public <R, ID> R queryWithLogicalExpire(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long expireTime, TimeUnit timeUnit) {
        String key = keyPrefix + id;
        //1.从缓存查数据
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json == null) {
            R dbValue = dbFallback.apply(id);
            if (dbValue == null) {
                this.Set(key, "", CACHE_NULL_TIME, TimeUnit.SECONDS);
                return null;
            }
            this.setWithLogicalExpire(key, dbValue, expireTime, timeUnit);
            return dbValue;
        }
        if (StrUtil.isBlank(json)) {
            //缓存里是空串 -> 说明数据库里确定没有，直接返回 null，不再打数据库
                log.info("[缓存] 命中空值 key={}", key);
                return null;
        }
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime1 = redisData.getExpireTime();
        if(expireTime1.isAfter(LocalDateTime.now())){
            return r;
        }
        String lockKey = keyPrefix+ "Lock:"+id;
        //2.判断是否存在互斥锁
        if(islock(lockKey)){
            //存在->开启独立线程查数据库
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                //重建缓存
                try {
                    R r1 = dbFallback.apply(id);
                    if (r1 == null) {
                        this.Set(key, "", CACHE_NULL_TIME, TimeUnit.SECONDS);
                    } else {
                        this.setWithLogicalExpire(key, r1, expireTime, timeUnit);
                    }
                }
                catch (Exception e) {
                    log.error("[缓存] 重建缓存失败 key={}", key, e);
                    throw new RuntimeException(e);
                }
                finally {
                    unlock(lockKey);
                }
            });
        }
        return r;
    }
    //删除缓存
    public void deleteKey(String key) {
        stringRedisTemplate.delete(key);
    }
}
