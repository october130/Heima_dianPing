package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
@Resource
private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryById(Long id) {
        //缓存穿透
        //Shop shop = queryPassThrough(id);
        Shop shop = queryWithMutex(id);
        return Result.ok(shop);
    }
    private boolean tryLock(String  key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue( flag);
    }
    private  void unLock(String key){
        stringRedisTemplate.delete(key);
    }

    //缓存击穿,这个方法主要用于封装缓存击穿
    public Shop queryWithMutex(Long id){
        String key = "cache:shop:" + id;
        //先根据id去查询Redis
        String ShopJson = stringRedisTemplate.opsForValue().get(key);
        // 如果存在，直接返回数据
        if (StrUtil.isNotBlank(ShopJson)) {
            Shop shop = JSONUtil.toBean(ShopJson, Shop.class);
            return shop;//命中，返回数据
        }
        if (ShopJson != null){
            return  null;//在Redis中查询数据如果没查出来，在即将查数据库之前
            //需要判断一下数据是不是空（有可能是恶意攻击）
        }
        
        
             String lockKey = "lock:shop:" + id;//拼接锁的key
             boolean GetLock = tryLock(lockKey);   //尝试获取锁

        try {
            while(!GetLock){
                Thread.sleep(50);//休眠50毫秒
                return queryWithMutex(id);//递归继续去获取锁，直到获取锁成功
            }

            //查数据库
            Shop shop = getById(id);
            //数据库中如果没有数据，没数据给用户直接返回404
            if (shop == null) {
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);//缓存空值,有效降低了缓存穿透
                return null;
            }
            //有数据的话，先存到Redis中，最后在返回
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
            //这个cache_shop_ttl是Redis中缓存的过期时间，这个过期时间是30分钟，设置的常量
            return shop;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            unLock(lockKey);//释放锁
        }
    }


    //缓存穿透，这个方法封装缓存穿透
    public Shop queryPassThrough(Long id) {
        String key = "cache:shop:" + id;
        //先根据id去查询Redis
        String ShopJson = stringRedisTemplate.opsForValue().get(key);
        // 如果存在，直接返回数据
        if (StrUtil.isNotBlank(ShopJson)) {
            Shop shop = JSONUtil.toBean(ShopJson, Shop.class);
            return shop;
        }
        if (ShopJson != null) {
            return null;//在Redis中查询数据如果没查出来，在即将查数据库之前
            //需要判断一下数据是不是空（有可能是恶意攻击）
        }
        //如果不存在需要去查数据库
        Shop shop = getById(id);
        //数据库中如果没有数据，没数据给用户直接返回404
        if (shop == null) {
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);//缓存空值,有效降低了缓存穿透
            return null;
        }
        //有数据的话，先存到Redis中，最后在返回
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //这个cache_shop_ttl是Redis中缓存的过期时间，这个过期时间是30分钟，设置的常量
        return   shop;
    }

    @Override
    @Transactional
    public Result updatebyRedis(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
      updateById(shop);
      stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
      return Result.ok();
    }
}




















//这个是原本用来缓存的代码，上述代码在是处理缓存击穿采用互斥锁
//String key = "cache:shop:" + id;
////先根据id去查询Redis
//String ShopJson = stringRedisTemplate.opsForValue().get(key);
//// 如果存在，直接返回数据
//        if (StrUtil.isNotBlank(ShopJson)) {
//Shop shop = JSONUtil.toBean(ShopJson, Shop.class);
//            return Result.ok(shop);
//        }
//                if (ShopJson != null){
//        return Result.fail("店铺不存在404请稍后再试！");//在Redis中查询数据如果没查出来，在即将查数据库之前
////需要判断一下数据是不是空（有可能是恶意攻击）
//        }
////如果不存在需要去查数据库
//Shop shop = getById(id);
////数据库中如果没有数据，没数据给用户直接返回404
//        if (shop == null) {
//        stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);//缓存空值,有效降低了缓存穿透
//            return Result.fail("店铺不存在404请稍后再试！");
//        }
//                //有数据的话，先存到Redis中，最后在返回
//                stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
////这个cache_shop_ttl是Redis中缓存的过期时间，这个过期时间是30分钟，设置的常量