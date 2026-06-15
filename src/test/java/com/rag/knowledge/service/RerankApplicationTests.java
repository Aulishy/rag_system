package com.rag.knowledge.service;


import com.rag.ai.agent.CustomerSupportAgent;
import com.rag.service.DocumentIngestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

@SpringBootTest
class RerankApplicationTests {

    @Autowired
    private DocumentIngestionService ingestionService;

    @Test
    void testIngestion() throws IOException {
        // 将测试文件放在 src/test/resources/test-docs/ 目录下，使其与项目解耦
        var path = new ClassPathResource("test-docs/Java基础入门80问.md").getFile().toPath();
        // 模拟把这份文档划分给 "HR" 部门账户
        ingestionService.ingestDocument(path, "HR");
    }

    @Autowired
    private CustomerSupportAgent agent;

    @Test
    void testAgent() {
        // 模拟张三发起咨询
        String sessionId = "zhangsan_session_1";

        // 第一轮：抛出问题
        System.out.println(agent.chat(sessionId, "&和&&的区别是什么"));

        // 第二轮：测试记忆与 Query Transformer（此时系统会自动把“它”重写为“报销审批”）
        System.out.println(agent.chat(sessionId, "介绍一下fianl关键字"));
    }
}