package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public Result queryById(Long id)
    {
        Shop shop = queryByIdMutxLock(id);
        if (shop == null) {
            return Result.fail("店铺信息不存在");
        }
        return Result.ok(shop);
    }

    /**
     * 防止缓存穿透 利用空值的方式
     * @param id
     * @return
     */
    public Shop queryPassThrough(Long id)
    {
        String key = RedisConstants.CACHE_SHOP_KEY  + id;
        log.info("shop key：{}",key);
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        log.info("shop：{}",shopJson);
        if (StrUtil.isNotBlank(shopJson)) {
            // 缓存中存在这个数据
            Shop shop = JSONUtil.toBean(shopJson,Shop.class);
            log.info("缓存中存在的shop数据：{}",shop);
            return shop;
        }
        if(shopJson!=null)
        {
            // 防止穿透
            return null;
        }
        // 缓存中不存在这个数据 去数据库中进行查询
        Shop shop = getById(id);
        if(shop == null)
        {
            stringRedisTemplate.opsForValue().set(key,"");
            stringRedisTemplate.expire(key,RedisConstants.CACHE_SHOP_TTL, TimeUnit.SECONDS);
            return null;
        }
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop));
        return shop;
    }

    /**
     * 基于互斥锁方式来解决缓存击穿问题
     * @param id
     * @return
     */
    public Shop queryByIdMutxLock(Long id)
    {
        //1.先从Redis中查询对应的店铺缓存信息，这里的常量值是固定的店铺前缀+查询店铺的Id
        String key = RedisConstants.CACHE_SHOP_KEY  + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        //2.如果在Redis中查询到了店铺信息,并且店铺的信息不是空字符串则转为Shop类型直接返回
        if (StrUtil.isNotBlank(shopJson)) {
            // 缓存中存在这个数据
            Shop shop = JSONUtil.toBean(shopJson,Shop.class);
            return shop;
        }
        //3.如果命中的是空字符串即我们缓存的空数据返回null
        if(shopJson!=null)
        {
            // 防止穿透
            return null;
        }

        // 4.没有命中则尝试根据锁的Id(锁前缀+店铺Id)获取互斥锁(本质是插入key),实现缓存重建
        Shop shop = null;
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        try{
            // 4.1 获取互斥锁
            boolean isLocked = tryLock(lockKey);
            // 4.2 判断是否获取锁成功
            if(!isLocked)
            {
                // 没有抢到锁
                Thread.sleep(200);
                return queryByIdMutxLock(id);
            }
            // 抢到锁了
            shop = getById(id);
            if(shop == null)
            {
                stringRedisTemplate.opsForValue().set(key,"");
                stringRedisTemplate.expire(key,RedisConstants.CACHE_SHOP_TTL, TimeUnit.SECONDS);
                return null;
            }
            log.info("来数据库了");
            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            unlock(lockKey);
        }
        return shop;
    }

    private boolean tryLock(String lockKey) {
        return stringRedisTemplate.opsForValue().setIfAbsent(lockKey,"1",10, TimeUnit.SECONDS);
    }

    private void unlock(String lockKey)
    {
        stringRedisTemplate.delete(lockKey);
    }

}
