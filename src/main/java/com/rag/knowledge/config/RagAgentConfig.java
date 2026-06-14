package com.rag.knowledge.config;

import com.rag.knowledge.store.PersistentChatMemoryStore;
import com.rag.knowledge.store.RestSearchQdrantEmbeddingStore;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.rag.knowledge.agent.CustomerSupportAgent;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.aggregator.ContentAggregator;
import dev.langchain4j.rag.content.aggregator.ReRankingContentAggregator;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.transformer.CompressingQueryTransformer;
import dev.langchain4j.rag.query.transformer.QueryTransformer;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.model.dashscope.QwenStreamingChatModel;
import dev.langchain4j.model.dashscope.QwenChatModel;
import dev.langchain4j.model.embedding.onnx.bgesmallzhq.BgeSmallZhQuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;

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

    // 新增：标准的非流式模型 Bean，专门供 QueryTransformer 等内部不需要流式输出的组件使用
    @Bean
    public ChatLanguageModel chatLanguageModel(DashscopeProperties properties) {
        return QwenChatModel.builder()
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
    public CustomerSupportAgent customerSupportAgent(
            StreamingChatLanguageModel streamingChatLanguageModel, // 只注入流式模型
            ChatLanguageModel chatLanguageModel,                   // 注入非流式模型
            EmbeddingModel embeddingModel,
            EmbeddingStore<TextSegment> qdrantStore,
            ScoringModel scoringModel,
            PersistentChatMemoryStore persistentChatMemoryStore
    ) {

        // 直接将非流式模型传入 QueryTransformer，完美避开 Adapter 的依赖问题
        QueryTransformer queryTransformer = new CompressingQueryTransformer(chatLanguageModel);

        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(qdrantStore)
                .embeddingModel(embeddingModel)
                .maxResults(10)
                .build();

        ContentAggregator contentAggregator = ReRankingContentAggregator.builder()
                .scoringModel(scoringModel)
                .build();

        RetrievalAugmentor retrievalAugmentor = DefaultRetrievalAugmentor.builder()
                .queryTransformer(queryTransformer)
                .contentRetriever(contentRetriever)
                .contentAggregator(contentAggregator)
                .build();

        log.info("CustomerSupportAgent 初始化完成 - 使用默认检索器");

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