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
        ChatHistory history = chatHistoryService.getById((String) memoryId);
        if (history != null && history.getHistoryJson() != null) {
            return ChatMessageDeserializer.messagesFromJson(history.getHistoryJson());
        }
        return new ArrayList<>();
    }//从数据库中获取会话历史

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        logger.info("插入会话，会话ID: {}", memoryId);
        logger.info("会话信息: {}", messages);
        ChatHistory history = new ChatHistory();
        history.setSessionId((String) memoryId);
        history.setHistoryJson(ChatMessageSerializer.messagesToJson(messages));
        // MyBatis-Plus 神器：如果有这个 ID 就更新，没有就插入！一句代码替代以前的一大段 if-else！
        chatHistoryService.saveOrUpdate(history);
    }//更新会话历史到数据库

    @Override
    public void deleteMessages(Object memoryId) {
        logger.info("删除会话，会话ID: {}", memoryId);
        chatHistoryService.removeById((String) memoryId);
    }//从数据库中删除会话历史
}