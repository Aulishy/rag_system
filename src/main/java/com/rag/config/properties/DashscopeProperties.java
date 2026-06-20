package com.rag.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "dashscope")
public class DashscopeProperties {

    private String apiKey;

    private ChatModel chatModel = new ChatModel();

    private StreamingChatModel streamingChatModel = new StreamingChatModel();

    private VisionModel visionModel = new VisionModel();

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public ChatModel getChatModel() {
        return chatModel;
    }

    public void setChatModel(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public StreamingChatModel getStreamingChatModel() {
        return streamingChatModel;
    }

    public void setStreamingChatModel(StreamingChatModel streamingChatModel) {
        this.streamingChatModel = streamingChatModel;
    }

    public VisionModel getVisionModel() {
        return visionModel;
    }

    public void setVisionModel(VisionModel visionModel) {
        this.visionModel = visionModel;
    }

    /**
     * 同步聊天模型配置
     */
    public static class ChatModel {
        private String modelName = "qwen-plus";

        public String getModelName() {
            return modelName;
        }

        public void setModelName(String modelName) {
            this.modelName = modelName;
        }
    }

    /**
     * 流式聊天模型配置
     */
    public static class StreamingChatModel {
        private String modelName = "qwen-turbo";

        public String getModelName() {
            return modelName;
        }

        public void setModelName(String modelName) {
            this.modelName = modelName;
        }
    }

    /**
     * 视觉模型配置（多模态）
     */
    public static class VisionModel {
        private String modelName = "qwen-vl-max";

        public String getModelName() {
            return modelName;
        }

        public void setModelName(String modelName) {
            this.modelName = modelName;
        }
    }
}
