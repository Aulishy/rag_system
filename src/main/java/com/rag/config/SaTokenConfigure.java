package com.rag.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SaTokenConfigure implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册 Sa-Token 拦截器，打开注解式鉴权功能
        registry.addInterceptor(new SaInterceptor(handle -> {
            // 指定一条 match 规则
            SaRouter
                    .match("/api/**")    // 拦截所有 /api 开头的请求
                    .notMatch("/api/auth/login") // 排除登录接口（不然怎么登录呢）
                    .notMatch("/api/auth/register") // 如果未来有注册接口也排除
                    .check(r -> StpUtil.checkLogin()); // 剩下的统统校验是否登录
        })).addPathPatterns("/**");
    }
}