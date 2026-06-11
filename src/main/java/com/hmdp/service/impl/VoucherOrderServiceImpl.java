package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
@Resource
private ISeckillVoucherService seckillVoucherService;



    @Resource
private RedisIdWorker redisIdWorker;
    @Override
    @Transactional
    public Result skillVoucher(Long voucherId) {
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        /*
        秒杀时间判断
         */
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            // 秒杀尚未开始
            throw new RuntimeException("秒杀尚未开始");
        }
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            // 秒杀已经结束
            throw new RuntimeException("秒杀已经结束");
        }
        if (voucher.getStock() < 1) {
            // 库存不足
            throw new RuntimeException("库存不足");
        }
        Long userId = UserHolder.getUser().getId();
        synchronized (userId.toString().intern()) {
            return creatVoucher(voucherId);//把秒杀优惠劵的过程封装成下面的方法
        }

    }


    @Transactional
    public Result creatVoucher(Long voucherId) {

        Long userId = UserHolder.getUser().getId();//获取用户id
        /*
        一人一单判断
         */
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            return Result.fail("该用户已购买过一次！");
        }

        /*
        库存扣减（同时防止超卖）
         */
        seckillVoucherService.update()
                .setSql("stock = stock - 1")// 库存减一
                .eq("voucher_Id", voucherId).eq("stock", 0)//设置乐观锁，确保库存不超卖
                // 如果库存大于零，多线程访问时放行
                .update();
        /*
        订单保存
         */
        VoucherOrder order = new VoucherOrder();
        long orderId = redisIdWorker.nextId("order");//调用事先在utils中定义的redisIdWorker方法生成订单id
        order.setId(orderId);//设置订单id
        order.setUserId(userId);
        order.setVoucherId(voucherId);
        save(order);//保存订单

        return Result.ok(orderId);
    }
}
