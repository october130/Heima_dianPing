package com.hmdp.service.impl;


import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

@Autowired
private StringRedisTemplate redisTemplate;
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //校检手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        //手机号符合生成验证码
        String code = RandomUtil.randomNumbers(6);



        //将生成的验证码存入redis
        redisTemplate.opsForValue().set( LOGIN_CODE_KEY + phone, code,LOGIN_CODE_TTL , TimeUnit.MINUTES);

        //发送验证码
        log.debug("发送短信验证码成功，验证码：{}",code);
        return Result.ok("发送成功");
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //校检手机号
        if (RegexUtils.isPhoneInvalid(loginForm.getPhone())) {
            return Result.fail("手机号格式错误");
        }
        String code =  redisTemplate.opsForValue().get(LOGIN_CODE_KEY + loginForm.getPhone());//从Redis获取验证码
        if (code == null || !code.equals(loginForm.getCode())) {
            return Result.fail("验证码错误");
        }
        User user = query().eq("phone", loginForm.getPhone()).one();//判断用户是否存在,不存在则创建一个新的用户
        if (user == null) {
            user = createUserWithPhone(loginForm.getPhone());
        }
        String token = UUID.randomUUID().toString(true);//生成token
        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user, userDTO);
        // 手动构建 userMap，只存非空字段（StringRedisTemplate 要求值必须是 String）
        Map<String, String> userMap = new HashMap<>();
        if (userDTO.getId() != null) userMap.put("id", userDTO.getId().toString());
        if (userDTO.getNickName() != null) userMap.put("nickName", userDTO.getNickName());
        if (userDTO.getIcon() != null) userMap.put("icon", userDTO.getIcon());


        redisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, userMap);//存储用户信息,用户信息进行hash存储
        redisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);//设置token有效期
        return Result.ok(token);
    }
    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(5));
        save(user);
        return user;
    }
}
