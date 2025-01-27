package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    public Result queryTypeList()
    {
        // 查询缓存中是否存在数据
        String key = RedisConstants.SHOP_TYPE_KEY;
        String shopTypesString = stringRedisTemplate.opsForValue().get(key);
        if (shopTypesString != null) {
            List<ShopType> shopTypes = JSONUtil.toList(shopTypesString,ShopType.class);
            return Result.ok(shopTypes);
        }
        // 如果不存在 访问数据库获取相应数据
        List<ShopType> shopTypes = query().orderByAsc("sort").list();
        if (shopTypes == null) {
            return Result.fail("商品种类信息不存在");
        }
        String shopTypeJson = JSONUtil.toJsonStr(shopTypes);
        stringRedisTemplate.opsForValue().set(key,shopTypeJson);
        return  Result.ok(shopTypes);
    }
}
