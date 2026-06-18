package com.rag.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.rag.ai.agent.CustomerSupportAgent;
import com.rag.ai.store.PersistentChatMemoryStore;
import com.rag.model.dto.ChatReqDTO;
import dev.langchain4j.data.message.ChatMessage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "http://localhost:5173")
public class ChatController {
    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);

    @Autowired
    private CustomerSupportAgent agent;

    @Autowired
    private ObjectProvider<CustomerSupportAgent> agentProvider;
    @Autowired
    private PersistentChatMemoryStore persistentChatMemoryStore;

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

        logger.info("收到流式对话请求, sessionId: {}， message: {}", request.getSessionId(), request.getMessage());

        // 2. 初始化 SSE (超时时间3分钟)
        SseEmitter emitter = new SseEmitter(180000L);
        emitter.onTimeout(emitter::complete);

        // 标记客户端是否已断开连接
        boolean[] isClientClosed = {false};
        // 2. 【关键修复】每次请求到来时，现场向 Spring 索要一个全新的 Prototype 实例
        CustomerSupportAgent agent = agentProvider.getObject();
        // 3. 调用 Agent
        agent.chatStream(request.getSessionId(), request.getMessage())
                .onNext(token -> {
                    // 如果前端已断开连接，不再往外发送，但【不要】抛出异常中断大模型！
                    if (isClientClosed[0]) return; 
                    try {
                        emitter.send(token);
                    } catch (Exception e) {
                        // 发生 IO 异常（客户端强行断开连接）
                        isClientClosed[0] = true;
                        emitter.complete(); 
                    }
                })
                .onComplete(response -> {
                    if (!isClientClosed[0]) {
                        try {
                            emitter.send("[DONE]");
                        } catch (Exception ignore) {}
                        emitter.complete();
                    }
                })
                .onError(error -> {
                    error.printStackTrace();
                    if (!isClientClosed[0]) {
                        emitter.completeWithError(error);
                    }
                })
                .start();

        return emitter;
    }
    @GetMapping("/history")
    public ResponseEntity<?> getChatHistory(@RequestParam String sessionId) {
        if (!StpUtil.isLogin()) {
            throw new RuntimeException("请先登录");
        }
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("sessionId 不能为空");
        }

        // 从数据库中获取历史
        List<ChatMessage> messages = persistentChatMemoryStore.getMessages(sessionId);

        // 转换为前端友好的格式
        List<Map<String, Object>> history = messages.stream()
                .filter(msg -> !(msg instanceof dev.langchain4j.data.message.SystemMessage)) // 过滤系统消息
                .map(msg -> {
                    Map<String, Object> item = new HashMap<>();
                    if (msg instanceof dev.langchain4j.data.message.UserMessage) {
                        item.put("role", "user");
                        item.put("content", ((dev.langchain4j.data.message.UserMessage) msg).contents().get(0).toString());
                    } else if (msg instanceof dev.langchain4j.data.message.AiMessage) {
                        item.put("role", "assistant");
                        item.put("content", ((dev.langchain4j.data.message.AiMessage) msg).text());
                    }
                    return item;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "code", 200,
                "data", history
        ));
    }
    /**
     * 清空当前会话的历史记录
     * 当前端开始新对话，或遇到历史记录错乱时可调用此接口清理脏数据
     */
    @DeleteMapping("/memory")
    public ResponseEntity<?> clearMemory(@RequestParam String sessionId) {
        if (!StpUtil.isLogin()) {
            throw new RuntimeException("请先登录");
        }
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("sessionId 不能为空");
        }
        
        // 从持久化存储中彻底删除该 sessionId 的所有聊天记录
        persistentChatMemoryStore.deleteMessages(sessionId);
        return ResponseEntity.ok(Map.of("message", "会话记录已清空"));
    }
}