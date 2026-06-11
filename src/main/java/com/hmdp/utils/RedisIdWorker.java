package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {
    private static final long BEGIN_TIMESTAMP = 1640995200L;//开始的时间戳
    public  static  final  int COUNT_BITS = 32;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public long nextId(String keyPrefix) {

        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;//生成时间戳，用当前时间减去开始时间戳

        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));//生成日期按天

        Long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);//获取当前日期，生成日期作为key


        return timestamp << COUNT_BITS | count;

    }

    public static void main(String[] args) {
        LocalDateTime  time = LocalDateTime.of(2022, 1, 1, 0, 0, 0);
        long second = time.toEpochSecond(ZoneOffset.UTC);
        System.out.println(second);

    }
}
