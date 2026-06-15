package com.rag.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "qdrant")
public class QdrantProperties {
    // 完全依赖 application.yml 中的配置
    private String host;
    private int port;
    private String collection;

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public String getCollection() { return collection; }
    public void setCollection(String collection) { this.collection = collection; }
}