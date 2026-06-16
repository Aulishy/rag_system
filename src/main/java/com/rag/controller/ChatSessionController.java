package com.rag.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.rag.entity.ChatSession;
import com.rag.mapper.ChatSessionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/session")
@CrossOrigin(origins = "http://localhost:5173")
public class ChatSessionController {

    @Autowired
    private ChatSessionMapper chatSessionMapper;

    /**
     * 1. 获取当前登录用户的所有历史会话
     */
    @GetMapping("/list")
    public Map<String, Object> getMySessions() {
        Map<String, Object> result = new HashMap<>();

        // 从 Sa-Token 中直接获取当前登录用户的 ID！极其方便！
        long userId = StpUtil.getLoginIdAsLong();

        QueryWrapper<ChatSession> query = new QueryWrapper<>();
        query.eq("user_id", userId);
        query.orderByDesc("update_time"); // 按最后聊天时间倒序排列（最近的在最上面）

        List<ChatSession> list = chatSessionMapper.selectList(query);

        result.put("code", 200);
        result.put("data", list);
        return result;
    }

    /**
     * 2. 新建一个会话 (点击前端的新建对话按钮触发)
     */
    @PostMapping("/create")
    public Map<String, Object> createSession() {
        Map<String, Object> result = new HashMap<>();
        long userId = StpUtil.getLoginIdAsLong();

        ChatSession session = new ChatSession();
        // 生成一个全局唯一的 Session ID 传给大模型做记忆隔离
        session.setSessionId(UUID.randomUUID().toString().replace("-", ""));
        session.setUserId(userId);
        session.setTitle("新会话"); // 未来可以让大模型根据第一句话自动改名
        session.setCreateTime(new Date());
        session.setUpdateTime(new Date());

        chatSessionMapper.insert(session);

        result.put("code", 200);
        result.put("message", "创建成功");
        result.put("data", session);
        return result;
    }
}