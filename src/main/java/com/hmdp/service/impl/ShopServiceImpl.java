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
        String key = RedisConstants.CACHE_SHOP_KEY  + id;
        log.info("shop key：{}",key);
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        log.info("shop：{}",shopJson);
        if (StrUtil.isNotBlank(shopJson)) {
            // 缓存中存在这个数据
            Shop shop = JSONUtil.toBean(shopJson,Shop.class);
            log.info("缓存中存在的shop数据：{}",shop);
            return Result.ok(shop);
        }
        // 缓存中不存在这个数据 去数据库中进行查询
        Shop shop = getById(id);
        if(shop == null)
        {
            return Result.fail("店铺不存在");
        }
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop));
        return Result.ok(shop);
    }
}
