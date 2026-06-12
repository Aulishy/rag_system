package com.rag.controller;

import com.rag.knowledge.agent.CustomerSupportAgent;
import com.rag.knowledge.config.QdrantProperties;
import com.rag.knowledge.service.DocumentIngestionService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/rag")
@CrossOrigin(origins = "*")
public class RagTestController {

    @Autowired
    private CustomerSupportAgent agent;

    @Autowired
    private DocumentIngestionService ingestionService;

    @Autowired
    private EmbeddingStore<TextSegment> embeddingStore;

    @Autowired
    private ScoringModel scoringModel;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private QdrantProperties qdrantProperties;
    private final RestTemplate restTemplate = new RestTemplate();

    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestBody ChatRequest request) {
        System.out.println("Received chat request: " + request.getMessage());
        Map<String, Object> response = new HashMap<>();

        try {
            String sessionId = request.getSessionId() != null ? request.getSessionId() : "default_session";
            String answer = agent.chat(sessionId, request.getMessage());
            response.put("success", true);
            response.put("sessionId", sessionId);
            response.put("answer", answer);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            e.printStackTrace();
        }

        return response;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ok");
        response.put("message", "RAG 服务运行正常");
        return response;
    }
    
@GetMapping("/debug/vector-check")
public Map<String, Object> vectorCheck(
        @RequestParam(defaultValue = "Java基础") String query) {
    Map<String, Object> result = new HashMap<>();
    // ① 检查 embedding 模型是否正常
    Embedding queryEmbedding = embeddingModel.embed(query).content();
    int queryDim = queryEmbedding.vector().length;
    result.put("queryVectorDim", queryDim);
    // ② 用 REST API 读 Qdrant（绕过 gRPC，判断数据是否真的存在）
    try {
        String scrollUrl = "http://" + qdrantProperties.getHost() + ":6333/collections/"
                + qdrantProperties.getCollection() + "/points/scroll";
        Map<String, Object> scrollBody = Map.of("limit", 1, "with_vector", true);
        @SuppressWarnings("unchecked")
        Map<String, Object> scrollResp = restTemplate.postForObject(scrollUrl, scrollBody, Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> points = (List<Map<String, Object>>)
                ((Map<String, Object>) scrollResp.get("result")).get("points");
        if (points == null || points.isEmpty()) {
            result.put("restVectorDim", 0);
            result.put("diagnosis", "DATA_ISSUE：Qdrant 里没有数据，请先跑 testIngestion 入库");
            return result;
        }
        @SuppressWarnings("unchecked")
        List<Double> restVector = (List<Double>) points.get(0).get("vector");
        int restDim = restVector != null ? restVector.size() : 0;
        result.put("restVectorDim", restDim);
        result.put("restPointId", points.get(0).get("id"));
    } catch (Exception e) {
        result.put("restError", e.getMessage());
        result.put("diagnosis", "DATA_ISSUE：无法连接 Qdrant 或 collection 不存在");
        return result;
    }
    // ③ 用 Java gRPC 客户端 search（就是 chat 报错的路径）
    try {
        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(3)
                        .build());
        List<Map<String, Object>> matchDetails = searchResult.matches().stream().map(m -> {
            Map<String, Object> detail = new HashMap<>();
            detail.put("id", m.embeddingId());
            detail.put("storedVectorDim",
                    m.embedding() != null ? m.embedding().vector().length : -1);
            detail.put("textPreview", m.embedded() != null
                    ? m.embedded().text().substring(0, Math.min(30, m.embedded().text().length()))
                    : null);
            return detail;
        }).toList();
        result.put("grpcMatchCount", searchResult.matches().size());
        result.put("grpcMatches", matchDetails);
        int grpcDim = matchDetails.isEmpty() ? -1
                : (int) matchDetails.get(0).get("storedVectorDim");
        result.put("grpcStoredVectorDim", grpcDim);
        // ④ 自动下结论
        int restDim = (int) result.get("restVectorDim");
        if (queryDim != 512) {
            result.put("diagnosis", "EMBEDDING_ISSUE：查询向量维度异常，期望 512，实际 " + queryDim);
        } else if (restDim == 512 && grpcDim == 0) {
            result.put("diagnosis", "DEPENDENCY_ISSUE：REST 读到 512 维，gRPC 读到 0 维，是依赖/反序列化问题");
        } else if (restDim == 0) {
            result.put("diagnosis", "DATA_ISSUE：Qdrant 里存的向量就是空的，需要重建 collection 并重新入库");
        } else if (grpcDim == 512) {
            result.put("diagnosis", "OK：向量正常，chat 报错可能是其他原因");
        }
    } catch (Exception e) {
        result.put("grpcSearchError", e.getMessage());
        int restDim = (int) result.get("restVectorDim");
        if (queryDim == 512 && restDim == 512) {
            result.put("diagnosis", "DEPENDENCY_ISSUE：REST 数据正常但 gRPC search 报错，是依赖问题");
        }
    }
    return result;
}


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
