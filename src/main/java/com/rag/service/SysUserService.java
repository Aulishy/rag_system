package com.rag.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.rag.entity.SysUser;

public interface SysUserService extends IService<SysUser> {
    // 继承 IService 后，这里自动拥有了 save, remove, update, getById 等几十个方法
    // 如果有复杂的自定义业务逻辑，再在这里声明，比如：
    SysUser login(String username, String password);
}