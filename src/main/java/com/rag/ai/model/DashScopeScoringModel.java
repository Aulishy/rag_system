package com.rag.ai.model;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 自定义调用阿里云百炼 (DashScope) 的 gte-rerank 重排序模型
 */
public class DashScopeScoringModel implements ScoringModel {

    private final String apiKey;
    private final String modelName;
    private final RestTemplate restTemplate;

    public DashScopeScoringModel(String apiKey, String modelName) {
        this.apiKey = apiKey;
        this.modelName = modelName != null ? modelName : "gte-rerank";
        this.restTemplate = new RestTemplate();
    }

    @Override
    public Response<List<Double>> scoreAll(List<TextSegment> segments, String query) {
        // 阿里云重排序 API 地址
        String url = "https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        // 组装请求参数
        Map<String, Object> input = new HashMap<>();
        input.put("query", query);
        input.put("documents", segments.stream().map(TextSegment::text).collect(Collectors.toList()));

        Map<String, Object> body = new HashMap<>();
        body.put("model", modelName);
        body.put("input", input);

        // 根据官方 API 示例，补充 parameters 参数
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("return_documents", false); // 我们只需要得分，不需要它返回冗余的原文，节省网络开销
        parameters.put("top_n", segments.size());  // 动态设置为传入切片的数量，确保每一个段落都能得到打分
        body.put("parameters", parameters);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> output = (Map<String, Object>) response.get("output");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> results = (List<Map<String, Object>>) output.get("results");

            // 阿里云返回的结果可能是乱序的，按照 index 恢复原始顺序
            Double[] scores = new Double[segments.size()];
            for (Map<String, Object> result : results) {
                int index = (Integer) result.get("index");
                double score = ((Number) result.get("relevance_score")).doubleValue();
                scores[index] = score;
            }
            return Response.from(List.of(scores));
        } catch (Exception e) {
            throw new RuntimeException("DashScope 重排序失败: " + e.getMessage(), e);
        }
    }
}