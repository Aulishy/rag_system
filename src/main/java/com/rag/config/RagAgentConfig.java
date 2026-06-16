package com.rag.config;

import com.rag.ai.agent.CustomerSupportAgent;
import com.rag.ai.model.DashScopeScoringModel;
import com.rag.ai.store.PersistentChatMemoryStore;
import com.rag.ai.store.RestSearchQdrantEmbeddingStore;
import com.rag.config.properties.DashscopeProperties;
import com.rag.config.properties.QdrantProperties;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.dashscope.QwenChatModel;
import dev.langchain4j.model.dashscope.QwenStreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallzhq.BgeSmallZhQuantizedEmbeddingModel;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.aggregator.ContentAggregator;
import dev.langchain4j.rag.content.aggregator.ReRankingContentAggregator;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.transformer.CompressingQueryTransformer;
import dev.langchain4j.rag.query.transformer.DefaultQueryTransformer;
import dev.langchain4j.rag.query.transformer.QueryTransformer;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
public class RagAgentConfig {

    private static final Logger log = LoggerFactory.getLogger(RagAgentConfig.class);

    @Bean
    public StreamingChatLanguageModel streamingChatLanguageModel(DashscopeProperties properties) {
        return QwenStreamingChatModel.builder()
                .apiKey(properties.getApiKey())
                .modelName(properties.getModelName())
                .build();
    }

    @Bean
    public ScoringModel scoringModel(DashscopeProperties properties) {
        return new DashScopeScoringModel(properties.getApiKey(), "qwen3-vl-rerank");
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        return new BgeSmallZhQuantizedEmbeddingModel();
    }

    @Bean
    public EmbeddingStore<TextSegment> qdrantStore(QdrantProperties properties) {
        QdrantEmbeddingStore grpcStore = QdrantEmbeddingStore.builder()
                .host(properties.getHost())
                .port(properties.getPort())
                .collectionName(properties.getCollection())
                .build();
        return new RestSearchQdrantEmbeddingStore(grpcStore, properties);
    }

    @Bean
    @Scope("prototype") // 将 Agent 设置为原型作用域，确保每个请求都获得一个新实例
    public CustomerSupportAgent customerSupportAgent(
            StreamingChatLanguageModel streamingChatLanguageModel,//流式聊天模型
            EmbeddingModel embeddingModel,//词嵌入模型
            EmbeddingStore<TextSegment> qdrantStore,//向量数据库
            ScoringModel scoringModel,//评分模型
            PersistentChatMemoryStore persistentChatMemoryStore//聊天记忆存储
    ) {

        QueryTransformer queryTransformer = new DefaultQueryTransformer();
        log.info("queryTransformer:", queryTransformer);
        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(qdrantStore)// 设置嵌入存储
                .embeddingModel(embeddingModel)// 设置嵌入模型
                .maxResults(10)// 设置最大结果数
                .minScore(0.6)// 设置最小分数
                .build();

        ContentAggregator contentAggregator = ReRankingContentAggregator.builder()
                .scoringModel(scoringModel)// 设置评分模型
                .build();

        RetrievalAugmentor retrievalAugmentor = DefaultRetrievalAugmentor.builder()
                .queryTransformer(queryTransformer)// 设置查询转换器
                .contentRetriever(contentRetriever)// 设置内容检索器
                .contentAggregator(contentAggregator)// 设置内容聚合器
                .build();

        log.info("CustomerSupportAgent 初始化完成 - 使用默认检索器 (暂未启用 QueryTransformer)");

        return AiServices.builder(CustomerSupportAgent.class)
                .streamingChatLanguageModel(streamingChatLanguageModel)
                .retrievalAugmentor(retrievalAugmentor)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(memoryId)
                        .maxMessages(20)
                        .chatMemoryStore(persistentChatMemoryStore)
                        .build())
                .build();
    }
}