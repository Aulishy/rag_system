package com.rag.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "aliyun")
public class AliyunProperties {

    private String accessKeyId;
    private String accessKeySecret;
    private Docmind docmind = new Docmind();

    public String getAccessKeyId() {
        return accessKeyId;
    }

    public void setAccessKeyId(String accessKeyId) {
        this.accessKeyId = accessKeyId;
    }

    public String getAccessKeySecret() {
        return accessKeySecret;
    }

    public void setAccessKeySecret(String accessKeySecret) {
        this.accessKeySecret = accessKeySecret;
    }

    public Docmind getDocmind() {
        return docmind;
    }

    public void setDocmind(Docmind docmind) {
        this.docmind = docmind;
    }

    public static class Docmind {
        private String endpoint = "docmind-api.cn-beijing.aliyuncs.com";

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }
    }
}

