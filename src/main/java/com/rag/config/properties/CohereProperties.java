package com.rag.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "cohere")
public class CohereProperties {

    private String apiKey;
    private String modelName = "rerank-multilingual-v3.0"; // 设置一个默认模型

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModelName() { return modelName; }

    public void setModelName(String modelName) { this.modelName = modelName; }
}