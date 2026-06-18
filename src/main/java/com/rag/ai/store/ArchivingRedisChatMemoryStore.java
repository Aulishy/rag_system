package com.rag.ai.store;

import com.rag.service.ChatHistoryService;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.util.List;

public class ArchivingRedisChatMemoryStore implements ChatMemoryStore {

    private final StringRedisTemplate redisTemplate;
    private final ChatHistoryService chatHistoryService;
    private final ThreadPoolTaskExecutor taskExecutor;
    private final Duration sessionTTL;

    private static final String KEY_PREFIX = "chat:memory:";

    public ArchivingRedisChatMemoryStore(StringRedisTemplate redisTemplate,
                                         ChatHistoryService chatHistoryService,
                                         ThreadPoolTaskExecutor taskExecutor,
                                         Duration sessionTTL) {
        this.redisTemplate = redisTemplate;
        this.chatHistoryService = chatHistoryService;
        this.taskExecutor = taskExecutor;
        this.sessionTTL = sessionTTL;
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String key = KEY_PREFIX + memoryId;
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            return List.of();
        }
        return ChatMessageDeserializer.messagesFromJson(json);
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String key = KEY_PREFIX + memoryId;
        String json = ChatMessageSerializer.messagesToJson(messages);
        redisTemplate.opsForValue().set(key, json, sessionTTL);

        taskExecutor.submit(() -> {
            try {
                com.rag.entity.ChatHistory history = new com.rag.entity.ChatHistory();
                history.setSessionId((String) memoryId);
                history.setHistoryJson(json);

                chatHistoryService.saveOrUpdate(history);
                System.out.println("【异步归档】会话 " + memoryId + " 已成功保存到 MySQL。");
            } catch (Exception e) {
                System.err.println("【异步归档失败】会话 " + memoryId + " 保存到 MySQL 时出错: " + e.getMessage());
            }
        });
    }

    @Override
    public void deleteMessages(Object memoryId) {
        String key = KEY_PREFIX + memoryId;
        redisTemplate.delete(key);
    }
}
