package com.rag.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.rag.common.Result;
import com.rag.entity.SysUser;
import com.rag.service.SysUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "http://localhost:5173")
public class AuthController {

    @Autowired
    private SysUserService sysUserService;

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody SysUser loginUser) {
        SysUser user = sysUserService.login(loginUser.getUsername(), loginUser.getPassword());

        if (user == null) {
            return Result.error(400, "用户名或密码错误");
        }

        StpUtil.login(user.getId());

        Map<String, Object> data = new HashMap<>();
        data.put("tokenName", StpUtil.getTokenName());
        data.put("tokenValue", StpUtil.getTokenValue());
        data.put("userId", user.getId());
        data.put("username", user.getUsername());

        return Result.success(data);
    }

    @PostMapping("/logout")
    public Result<String> logout() {
        StpUtil.logout();
        return Result.success("退出成功");
    }
}