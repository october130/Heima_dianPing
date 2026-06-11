package com.hmdp.service.impl;

import ch.qos.logback.core.util.TimeUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
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
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
@Resource
private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryList() {
        String key = RedisConstants.CACHE_SHOP_TYPE_KEY;
       //先查询缓存，如果有直接返回
        String shopType = stringRedisTemplate.opsForValue().get(key);
       if (StrUtil.isNotBlank(shopType)){
           List<ShopType> shopTypes = JSONUtil.toList(shopType, ShopType.class);
           return Result.ok(shopTypes);
       }
       //没有的话，查询数据库A
        List<ShopType> typeList = query().orderByAsc("sort").list();
       if (typeList.isEmpty()){
           return Result.fail("没有数据,店铺类型不存在");
       }
       stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(typeList),
               RedisConstants.CACHE_SHOP_TYPE_TTL, TimeUnit.MINUTES);
       return Result.ok(typeList);


    }
}
