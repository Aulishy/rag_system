package com.rag.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.rag.knowledge.agent.CustomerSupportAgent;
import com.rag.knowledge.service.DocumentIngestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/rag")
@CrossOrigin(origins = "*")
public class RagTestController {

    @Autowired
    private CustomerSupportAgent agent;

    @Autowired
    private DocumentIngestionService ingestionService;

    /**
     * 1. 聊天接口（默认使用流式输出 SSE）
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody ChatRequest request) {

        // 校验用户是否登录
        if (!StpUtil.isLogin()) {
            throw new RuntimeException("请先登录");
        }

        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.isEmpty()) {
            throw new RuntimeException("sessionId 不能为空");
        }

        System.out.println("收到流式对话请求, sessionId: " + sessionId + ", message: " + request.getMessage());

        // 设置超时时间，大模型回复可能较慢，这里设置为 3 分钟 (180000ms)
        SseEmitter emitter = new SseEmitter(180000L);
        // 超时自动完成，防止一直挂起
        emitter.onTimeout(emitter::complete);

        // 调用 Agent 的流式方法 (请确保你的 CustomerSupportAgent 接口中是 chatStream 方法)
        agent.chatStream(sessionId, request.getMessage())
                .onNext(token -> {
                    try {
                        // 每当大模型吐出一个字（token），立刻通过 SSE 发给前端
                        emitter.send(token);
                    } catch (Exception e) {
                        emitter.completeWithError(e);
                    }
                })
                .onComplete(response -> {
                    // 大模型回答完毕，发个结束标识给前端并关闭连接
                    try {
                        emitter.send("[DONE]");
                    } catch (Exception e) {
                        // 忽略异常
                    }
                    emitter.complete();
                })
                .onError(error -> {
                    error.printStackTrace();
                    emitter.completeWithError(error);
                })
                .start(); // 必须调用 start() 才能触发流式请求

        return emitter;
    }

    /**
     * 2. 文件上传与知识库构建接口
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "tenantId", defaultValue = "default_tenant") String tenantId) {

        Map<String, Object> response = new HashMap<>();

        // 校验用户是否登录 (可选，为了数据安全建议保留)
        if (!StpUtil.isLogin()) {
            response.put("success", false);
            response.put("message", "请先登录后再上传文件");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        if (file.isEmpty()) {
            response.put("success", false);
            response.put("message", "上传的文件为空");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            // 创建一个临时文件来保存上传的内容
            String originalFilename = file.getOriginalFilename();
            String prefix = "upload_";
            String suffix = originalFilename != null ? "_" + originalFilename : ".tmp";

            Path tempFilePath = Files.createTempFile(prefix, suffix);

            // 将上传的 MultipartFile 内容传输到临时文件中
            file.transferTo(tempFilePath.toFile());

            System.out.println("收到文件上传请求: " + originalFilename + ", 临时保存至: " + tempFilePath);

            // 调用业务层服务处理该文档，进行解析、切片、向量化并存入 Qdrant
            ingestionService.ingestDocument(tempFilePath, tenantId);

            // 处理完成后，删除临时文件以释放磁盘空间
            Files.deleteIfExists(tempFilePath);

            response.put("success", true);
            response.put("message", "文件 '" + originalFilename + "' 上传并处理成功");
            return ResponseEntity.ok(response);

        } catch (IOException e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "文件保存或处理失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "处理文档时发生错误: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 请求体参数封装类
     */
    public static class ChatRequest {
        private String message;
        private String sessionId;

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getSessionId() {
            return sessionId;
        }

        public void setSessionId(String sessionId) {
            this.sessionId = sessionId;
        }
    }
}