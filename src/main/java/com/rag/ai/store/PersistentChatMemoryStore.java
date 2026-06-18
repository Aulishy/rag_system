package com.rag.ai.store;

import com.rag.entity.ChatHistory;
import com.rag.service.ChatHistoryService;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;

@Component
public class PersistentChatMemoryStore implements ChatMemoryStore {
    private static final Logger logger = LoggerFactory.getLogger(PersistentChatMemoryStore.class);
    @Autowired
    private ChatHistoryService chatHistoryService;

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        logger.info("获取会话，会话ID: {}", memoryId);

        try {
            ChatHistory history = chatHistoryService.getById((String) memoryId);
            if (history != null && history.getHistoryJson() != null) {
                logger.info("找到历史数据，JSON长度: {} 字符", history.getHistoryJson().length());
                return ChatMessageDeserializer.messagesFromJson(history.getHistoryJson());
            } else {
                logger.info("未找到历史数据，返回空列表");
            }
        } catch (Exception e) {
            logger.error("获取会话历史时发生异常，sessionId: {}", memoryId, e);
        }

        return new ArrayList<>();
    }//从数据库中获取会话历史

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        logger.info("插入会话，会话ID: {}", memoryId);
        logger.info("消息数量: {}", messages.size());

        try {
            ChatHistory history = new ChatHistory();
            history.setSessionId((String) memoryId);
            String json = ChatMessageSerializer.messagesToJson(messages);
            history.setHistoryJson(json);

            logger.debug("准备保存的JSON长度: {} 字符", json.length());

            boolean success = chatHistoryService.saveOrUpdate(history);

            if (success) {
                logger.info("✅ 会话历史保存成功，sessionId: {}", memoryId);
            } else {
                logger.error("❌ 会话历史保存失败，sessionId: {}", memoryId);
            }
        } catch (Exception e) {
            logger.error("❌ 保存会话历史时发生异常，sessionId: {}", memoryId, e);
            throw e;
        }
    }//更新会话历史到数据库

    @Override
    public void deleteMessages(Object memoryId) {
        logger.info("删除会话，会话ID: {}", memoryId);
        chatHistoryService.removeById((String) memoryId);
    }//从数据库中删除会话历史
}