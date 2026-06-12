package com.rag.knowledge.config;
import com.rag.knowledge.store.RestSearchQdrantEmbeddingStore;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.cohere.CohereScoringModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.rag.knowledge.agent.CustomerSupportAgent;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.aggregator.ContentAggregator;
import dev.langchain4j.rag.content.aggregator.ReRankingContentAggregator;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.transformer.CompressingQueryTransformer;
import dev.langchain4j.rag.query.transformer.QueryTransformer;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.model.dashscope.QwenChatModel;
import dev.langchain4j.model.embedding.onnx.bgesmallzhq.BgeSmallZhQuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;

import java.util.Collections;
import java.util.List;

@Configuration
public class RagAgentConfig {

    private static final Logger log = LoggerFactory.getLogger(RagAgentConfig.class);

    @Bean
    public ChatLanguageModel chatLanguageModel(DashscopeProperties properties) {
        return QwenChatModel.builder()
                .apiKey(properties.getApiKey())
                .modelName(properties.getModelName())
                .build();
    }

    @Bean
    public ScoringModel scoringModel(DashscopeProperties properties) {
        // 完美复用你给千问大模型配置的 DashScope API Key
        // 替换为官方示例中的多模态排序模型 qwen3-vl-rerank
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
    public CustomerSupportAgent customerSupportAgent(
            ChatLanguageModel chatModel,//聊天模型
            EmbeddingModel embeddingModel,//嵌入模型
            EmbeddingStore<TextSegment> qdrantStore,//向量数据库
            ScoringModel scoringModel//重排序模型
    ) {

        QueryTransformer queryTransformer = new CompressingQueryTransformer(chatModel);

        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(qdrantStore)
                .embeddingModel(embeddingModel)
                .maxResults(10)
                .build();

        // 重新开启重排序聚合器
        ContentAggregator contentAggregator = ReRankingContentAggregator.builder()
                .scoringModel(scoringModel)
                .build();

        RetrievalAugmentor retrievalAugmentor = DefaultRetrievalAugmentor.builder()
                .queryTransformer(queryTransformer)
                .contentRetriever(contentRetriever)
                .contentAggregator(contentAggregator) // 开启重排序
                .build();

        log.info("CustomerSupportAgent 初始化完成 - 使用默认检索器");

        return AiServices.builder(CustomerSupportAgent.class)
                .chatLanguageModel(chatModel)
                .retrievalAugmentor(retrievalAugmentor)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
                .build();
    }
}
