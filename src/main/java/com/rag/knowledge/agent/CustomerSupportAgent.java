package com.rag.knowledge.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface CustomerSupportAgent {

    @SystemMessage({
            "你是一名专业的Java开发人员。",
            "请务必基于系统提供的上下文（Context）来回答用户的问题，如果上下文包含图片，请直接返回图片。",
            "如果你在上下文中找不到答案，请直接回答‘抱歉，知识库中未找到相关规定’，绝不能编造答案！"
    })
    String chat(@MemoryId String sessionId, @UserMessage String userMessage);
}