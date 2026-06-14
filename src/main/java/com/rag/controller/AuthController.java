package com.rag.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.rag.entity.SysUser;
import com.rag.mapper.SysUserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @Autowired
    private SysUserMapper sysUserMapper;

    /**
     * 登录接口
     */
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody SysUser loginUser) {
        Map<String, Object> result = new HashMap<>();

        // 1. 根据用户名查询数据库
        QueryWrapper<SysUser> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", loginUser.getUsername());
        SysUser user = sysUserMapper.selectOne(queryWrapper);

        // 2. 校验账号密码 (这里为了演示简单对比明文，实际商业项目需使用 BCrypt 等加密比对)
        if (user == null || !user.getPassword().equals(loginUser.getPassword())) {
            result.put("code", 400);
            result.put("message", "用户名或密码错误");
            return result;
        }

        // 3. 密码正确，使用 Sa-Token 登录，传入用户主键 ID
        StpUtil.login(user.getId());

        // 4. 获取生成的 Token 返回给前端
        result.put("code", 200);
        result.put("message", "登录成功");

        Map<String, Object> data = new HashMap<>();
        data.put("tokenName", StpUtil.getTokenName());
        data.put("tokenValue", StpUtil.getTokenValue());
        data.put("userId", user.getId());
        data.put("username", user.getUsername());
        result.put("data", data);

        return result;
    }

    /**
     * 退出登录接口
     */
    @PostMapping("/logout")
    public Map<String, Object> logout() {
        StpUtil.logout();
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "退出成功");
        return result;
    }
}