package com.rag.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.rag.ai.agent.CustomerSupportAgent;
import com.rag.model.dto.ChatReqDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatController {

    @Autowired
    private CustomerSupportAgent agent;

    /**
     * 流式聊天接口
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody ChatReqDTO request) {

        // 1. 参数与权限校验
        if (!StpUtil.isLogin()) {
            throw new RuntimeException("请先登录");
        }
        if (request.getSessionId() == null || request.getSessionId().trim().isEmpty()) {
            throw new IllegalArgumentException("sessionId 不能为空");
        }

        System.out.println("收到流式对话请求, sessionId: " + request.getSessionId() + ", message: " + request.getMessage());

        // 2. 初始化 SSE (超时时间3分钟)
        SseEmitter emitter = new SseEmitter(180000L);
        emitter.onTimeout(emitter::complete);

        // 3. 调用 Agent
        agent.chatStream(request.getSessionId(), request.getMessage())
                .onNext(token -> {
                    try {
                        emitter.send(token);
                    } catch (Exception e) {
                        emitter.completeWithError(e);
                    }
                })
                .onComplete(response -> {
                    try {
                        emitter.send("[DONE]");
                    } catch (Exception e) {
                        // 忽略发送结束标识时的异常
                    }
                    emitter.complete();
                })
                .onError(error -> {
                    error.printStackTrace();
                    emitter.completeWithError(error);
                })
                .start();

        return emitter;
    }
}