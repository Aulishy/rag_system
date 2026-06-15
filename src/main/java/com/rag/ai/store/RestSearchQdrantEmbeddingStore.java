package com.rag.ai.store;

import com.rag.config.properties.QdrantProperties;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RestSearchQdrantEmbeddingStore implements EmbeddingStore<TextSegment> {

    private static final String PAYLOAD_TEXT_KEY = "text_segment";

    private final EmbeddingStore<TextSegment> grpcStore;
    private final RestTemplate restTemplate;
    private final String searchUrl;

    public RestSearchQdrantEmbeddingStore(EmbeddingStore<TextSegment> grpcStore,
                                          QdrantProperties properties) {
        this.grpcStore = grpcStore;
        this.restTemplate = new RestTemplate();
        this.searchUrl = "http://" + properties.getHost() + ":6333/collections/"
                + properties.getCollection() + "/points/search";
    }

    // ---- 写入全部委托给 gRPC（入库正常） ----

    @Override
    public String add(Embedding embedding) {
        return grpcStore.add(embedding);
    }

    @Override
    public void add(String id, Embedding embedding) {
        grpcStore.add(id, embedding);
    }

    @Override
    public String add(Embedding embedding, TextSegment textSegment) {
        return grpcStore.add(embedding, textSegment);
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        return grpcStore.addAll(embeddings);
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> embedded) {
        return grpcStore.addAll(embeddings, embedded);
    }

    // ---- 检索改走 REST（绕过 gRPC 反序列化 bug） ----

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("vector", toFloatList(request.queryEmbedding()));
        body.put("limit", request.maxResults());
        body.put("with_payload", true);
        body.put("with_vector", true);

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restTemplate.postForObject(searchUrl, body, Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> points = (List<Map<String, Object>>) response.get("result");

        if (points == null || points.isEmpty()) {
            return new EmbeddingSearchResult<>(List.of());
        }

        List<EmbeddingMatch<TextSegment>> matches = new ArrayList<>();
        for (Map<String, Object> point : points) {
            double score = ((Number) point.get("score")).doubleValue();
            if (score < request.minScore()) {
                continue;
            }

            String id = String.valueOf(point.get("id"));
            Embedding embedding = toEmbedding(point.get("vector"));
            TextSegment segment = toTextSegment(point.get("payload"));

            matches.add(new EmbeddingMatch<>(score, id, embedding, segment));
        }

        return new EmbeddingSearchResult<>(matches);
    }

    private static List<Float> toFloatList(Embedding embedding) {
        float[] vector = embedding.vector();
        List<Float> list = new ArrayList<>(vector.length);
        for (float v : vector) {
            list.add(v);
        }
        return list;
    }

    private static Embedding toEmbedding(Object vectorObj) {
        @SuppressWarnings("unchecked")
        List<Number> vector = (List<Number>) vectorObj;
        float[] data = new float[vector.size()];
        for (int i = 0; i < vector.size(); i++) {
            data[i] = vector.get(i).floatValue();
        }
        return Embedding.from(data);
    }

    @SuppressWarnings("unchecked")
    private static TextSegment toTextSegment(Object payloadObj) {
        if (payloadObj == null) {
            return null;
        }
        Map<String, Object> payload = (Map<String, Object>) payloadObj;
        String text = (String) payload.getOrDefault(PAYLOAD_TEXT_KEY, "");

        Metadata metadata = new Metadata();
        payload.forEach((key, value) -> {
            if (!PAYLOAD_TEXT_KEY.equals(key) && value != null) {
                metadata.put(key, String.valueOf(value));
            }
        });
        return TextSegment.from(text, metadata);
    }
}