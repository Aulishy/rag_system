package com.rag.knowledge.store;

import com.rag.entity.ChatHistory;
import com.rag.mapper.ChatHistoryMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class PersistentChatMemoryStore implements ChatMemoryStore {

    @Autowired
    private ChatHistoryMapper chatHistoryMapper;

    // 1. AI 聊天前，自动调用此方法从数据库读取之前的对话
    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String sessionId = (String) memoryId;
        ChatHistory history = chatHistoryMapper.selectById(sessionId);
        if (history != null && history.getHistoryJson() != null) {
            // 将 JSON 字符串反序列化为 LangChain4j 认识的 Message 对象
            return ChatMessageDeserializer.messagesFromJson(history.getHistoryJson());
        }
        return new ArrayList<>();
    }

    // 2. AI 回复后，自动调用此方法把最新的对话保存到数据库
    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String sessionId = (String) memoryId;
        // 将对话序列化为 JSON 字符串
        String json = ChatMessageSerializer.messagesToJson(messages);

        ChatHistory history = chatHistoryMapper.selectById(sessionId);
        if (history == null) {
            history = new ChatHistory();
            history.setSessionId(sessionId);
            history.setHistoryJson(json);
            chatHistoryMapper.insert(history);
        } else {
            history.setHistoryJson(json);
            chatHistoryMapper.updateById(history);
        }
    }

    // 3. 删除记忆
    @Override
    public void deleteMessages(Object memoryId) {
        chatHistoryMapper.deleteById((String) memoryId);
    }
}