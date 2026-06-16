package com.rag.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.rag.entity.SysUser;
import com.rag.mapper.SysUserMapper;
import com.rag.service.SysUserService;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {
    private static final Logger logger = LoggerFactory.getLogger(SysUserServiceImpl.class);
    @Override
    public SysUser login(String username, String password) {
        logger.info("用户登录：{}", username);
        QueryWrapper<SysUser> queryWrapper = new QueryWrapper<>();// 创建查询条件
        queryWrapper.eq("username", username);// 设置查询条件,根据用户名查询用户
        SysUser user = this.getOne(queryWrapper); // this.getOne 是 ServiceImpl 自带的方法

        if (user != null && user.getPassword().equals(password)) {
            return user;
        }
        return null;
    }
}
