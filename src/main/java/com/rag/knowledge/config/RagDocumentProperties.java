package com.rag.knowledge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "rag.document")
public class RagDocumentProperties {
    
    private int maxSegmentSize = 300;
    private int maxOverlapSize = 30;

    public int getMaxSegmentSize() { return maxSegmentSize; }
    public void setMaxSegmentSize(int maxSegmentSize) { this.maxSegmentSize = maxSegmentSize; }

    public int getMaxOverlapSize() { return maxOverlapSize; }
    public void setMaxOverlapSize(int maxOverlapSize) { this.maxOverlapSize = maxOverlapSize; }
}