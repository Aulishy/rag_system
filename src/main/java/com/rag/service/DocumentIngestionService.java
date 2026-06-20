package com.rag.service;

import com.aliyun.docmind_api20220711.Client;
import com.aliyun.docmind_api20220711.models.GetDocParserResultRequest;
import com.aliyun.docmind_api20220711.models.GetDocParserResultResponse;
import com.aliyun.docmind_api20220711.models.QueryDocParserStatusRequest;
import com.aliyun.docmind_api20220711.models.QueryDocParserStatusResponse;
import com.aliyun.docmind_api20220711.models.SubmitDocParserJobAdvanceRequest;
import com.aliyun.docmind_api20220711.models.SubmitDocParserJobResponse;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.models.RuntimeOptions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.config.properties.AliyunProperties;
import com.rag.model.dto.ParsedBlock;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.stereotype.Service;
import com.rag.config.properties.RagDocumentProperties;

import java.io.FileInputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
// ... existing code ...




@Service
public class DocumentIngestionService {

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final DocumentParser documentParser = new ApacheTikaDocumentParser(); // 解析器直接 new 即可
    private final RagDocumentProperties documentProperties;//文档切片配置
    private final AliyunProperties aliyunProperties;
    private final ChatLanguageModel visionModel;
    // 采用构造器注入依赖和配置，确保不可变性 (final)
    public DocumentIngestionService(EmbeddingStore<TextSegment> embeddingStore,
                                    EmbeddingModel embeddingModel,
                                    RagDocumentProperties documentProperties,
                                    ChatLanguageModel visionModel,
                                    AliyunProperties aliyunProperties) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
        this.documentProperties = documentProperties;
        this.visionModel = visionModel;
        this.aliyunProperties = aliyunProperties;
    }

    /**
     * 将外部文档灌入向量库（ETL 核心管道）
     * @param filePath 文档的本地路径
     * @param tenantId 租户隔离ID（或者业务模块ID，如 "HR", "IT"）
     */
    public void ingestDocument(Path filePath, String tenantId) {
        String fileName = filePath.getFileName().toString();

        // 步骤 1：调用阿里云文档解析 API 获取结构化区块 (这里用伪代码代替具体的 HTTP 请求)
        // 真实情况你需要对接阿里云 DocMind API，解析后它会返回文本以及 OSS 上的图片 URL
        List<ParsedBlock> documentBlocks = callAliyunDocumentParsingApi(filePath);

        List<TextSegment> finalSegments = new ArrayList<>();


        // 步骤 2：遍历区块，实现图文分别处理
        for (ParsedBlock block : documentBlocks) {
            Metadata metadata = new Metadata();
            metadata.put("tenant_id", tenantId);
            metadata.put("file_name", fileName);

            if (block.getType() == ParsedBlock.BlockType.TEXT) {
                // 普通文本：直接构建 Segment
                // （这里为了简便直接添加，你依然可以使用 DocumentSplitter 对长文本二次切分）
                metadata.put("type", "text");
                finalSegments.add(TextSegment.from(block.getContent(), metadata));

            } else if (block.getType() == ParsedBlock.BlockType.IMAGE) {
                // 图片处理逻辑
                String imageUrl = block.getContent(); // 阿里云返回的图片临时/公开下载链接

                // 让 Qwen-VL 看图并生成总结
                String imageCaption = generateImageCaption(imageUrl);
                System.out.println("成功生成图片摘要: " + imageCaption);

                // 核心：把总结存为文本，但把真实图片 URL 塞进 metadata
                metadata.put("type", "image_summary");
                metadata.put("source_image_url", imageUrl);

                finalSegments.add(TextSegment.from("文档中有一张图片，描述如下：" + imageCaption, metadata));
            }
        }

        // 步骤 3：统一向量化入库 (文字段落 和 图片的文字描述 一起生成向量)
        List<Embedding> embeddings = embeddingModel.embedAll(finalSegments).content();

        // Step 4: 批量生成向量并持久化入库 (Load)
        System.out.println("正在为 " + fileName + " 生成向量并写入 Qdrant，有效切片总数: " + finalSegments.size());

        // 过滤掉维度为 0 或异常的无效向量
        List<Embedding> validEmbeddings = new ArrayList<>();
        List<TextSegment> validSegments = new ArrayList<>();

        for (int i = 0; i < embeddings.size(); i++) {
            Embedding embedding = embeddings.get(i);
            TextSegment segment = finalSegments.get(i);

            if (embedding != null && embedding.vector() != null && embedding.vector().length > 0) {
                validEmbeddings.add(embedding);
                validSegments.add(segment);
            } else {
                String preview = segment.text();
                preview = preview.substring(0, Math.min(20, preview.length()));
                System.out.println("⚠️ 发现并丢弃无效的 0 维度向量，对应文本预览: " + preview);
            }
        }

        if (validEmbeddings.isEmpty()) {
            System.out.println("❌ 文档 " + fileName + " 的所有切片均未能生成有效向量，跳过入库。");
            return;
        }

        // 步骤 4：存入 Qdrant
        embeddingStore.addAll(embeddings, finalSegments);
        System.out.println("【🎉 多模态入库成功】文档 " + fileName + " 已建立索引！");
    }

    /**
     * 辅助方法：调用视觉大模型给图片打标
     */
    private String generateImageCaption(String imageUrl) {
        UserMessage prompt = UserMessage.from(
                TextContent.from("你是一个文档分析助手。请详细描述这张图片的内容。如果是架构图，请说明组件关系；如果是图表，请提取关键数据。"),
                ImageContent.from(imageUrl)
        );
        return visionModel.generate(prompt).content().text();
    }

    /**
     * 辅助方法：对接阿里云文档智能的占位方法
     */
    private List<ParsedBlock> callAliyunDocumentParsingApi(Path filePath) {
        List<ParsedBlock> parsedBlocks = new ArrayList<>();

        try {
            // 1. 初始化阿里云 OpenAPI Client
            String accessKeyId = aliyunProperties.getAccessKeyId();
            String accessKeySecret = aliyunProperties.getAccessKeySecret();
            String endpoint = aliyunProperties.getDocmind().getEndpoint();

            System.out.println("🔧 初始化阿里云 DocMind 客户端...");
            System.out.println("   Endpoint: " + endpoint);

            Config config = new Config()
                    .setAccessKeyId(accessKeyId)
                    .setAccessKeySecret(accessKeySecret)
                    .setEndpoint(endpoint);
            Client client = new Client(config);

            // 2. 构造并提交异步解析任务
            String fileName = filePath.getFileName().toString();
            String extension = fileName.substring(fileName.lastIndexOf(".") + 1);

            System.out.println("📤 提交文档解析任务: " + fileName);

            // 使用 try-with-resources 确保文件流自动关闭
            try (FileInputStream fileStream = new FileInputStream(filePath.toFile())) {
                SubmitDocParserJobAdvanceRequest submitReq = new SubmitDocParserJobAdvanceRequest();
                submitReq.setFileNameExtension(extension);
                submitReq.setFileUrlObject(fileStream);

                RuntimeOptions runtime = new RuntimeOptions();
                SubmitDocParserJobResponse submitResp = client.submitDocParserJobAdvance(submitReq, runtime);

                if (submitResp == null || submitResp.getBody() == null) {
                    throw new RuntimeException("提交文档解析任务失败,响应为空");
                }

                // 使用 Jackson 将响应转换为 JSON 以便提取 jobId
                ObjectMapper mapper = new ObjectMapper();
                JsonNode submitNode = mapper.valueToTree(submitResp.getBody());
                System.out.println("📨 提交响应 JSON: " + submitNode.toPrettyString());

                // 提取 jobId (尝试多种路径)
                String jobId = extractJsonField(submitNode, "data", "id");
                if (jobId == null) {
                    jobId = extractJsonField(submitNode, "Data", "Id");
                }

                if (jobId == null || jobId.isEmpty()) {
                    throw new RuntimeException("无法从提交响应中获取 Job ID");
                }

                System.out.println("✅ 成功提交阿里云文档解析任务, Job ID: " + jobId);

                // 3. 轮询查询解析状态 (使用 QueryDocParserStatus 接口)
                QueryDocParserStatusRequest statusRequest = new QueryDocParserStatusRequest();
                statusRequest.setId(jobId);

                String status = "Processing";
                int retryCount = 0;
                int maxRetries = 30; // 最多轮询 30 次(约 90 秒)

                while (!"success".equalsIgnoreCase(status) && !"fail".equalsIgnoreCase(status) && retryCount < maxRetries) {
                    Thread.sleep(3000);
                    retryCount++;

                    System.out.println("⏳ 第 " + retryCount + " 次轮询解析状态...");

                    QueryDocParserStatusResponse statusResp = client.queryDocParserStatus(statusRequest);

                    if (statusResp == null || statusResp.getBody() == null) {
                        System.err.println("⚠️ 第 " + retryCount + " 次轮询: 响应为空,继续等待...");
                        continue;
                    }

                    // 将状态响应转换为 JSON
                    JsonNode statusNode = mapper.valueToTree(statusResp.getBody());
                    System.out.println("📨 状态响应 JSON: " + statusNode.toPrettyString());

                    // 提取状态信息
                    JsonNode dataNode = statusNode.path("data");
                    if (dataNode.isMissingNode()) {
                        dataNode = statusNode.path("Data");
                    }

                    if (dataNode.isMissingNode()) {
                        System.err.println("⚠️ 第 " + retryCount + " 次轮询: Data 节点不存在,继续等待...");
                        continue;
                    }

                    status = dataNode.path("status").asText(dataNode.path("Status").asText("Unknown"));
                    Float processing = null;
                    if (!dataNode.path("processing").isMissingNode()) {
                        processing = (float) dataNode.path("processing").asDouble();
                    } else if (!dataNode.path("Processing").isMissingNode()) {
                        processing = (float) dataNode.path("Processing").asDouble();
                    }

                    Integer pageCountEstimate = null;
                    if (!dataNode.path("pageCountEstimate").isMissingNode()) {
                        pageCountEstimate = dataNode.path("pageCountEstimate").asInt();
                    } else if (!dataNode.path("PageCountEstimate").isMissingNode()) {
                        pageCountEstimate = dataNode.path("PageCountEstimate").asInt();
                    }

                    System.out.println("📊 当前解析状态: " + status +
                            ", 进度: " + (processing != null ? processing + "%" : "未知") +
                            ", 已处理页数: " + (pageCountEstimate != null ? pageCountEstimate : "未知"));

                    if ("fail".equalsIgnoreCase(status)) {
                        String message = dataNode.path("message").asText(dataNode.path("Message").asText("未知错误"));
                        System.err.println("❌ 阿里云文档解析失败!");
                        System.err.println("   错误信息: " + message);
                        throw new RuntimeException("阿里云文档解析失败: " + message);
                    }
                }

                if (!"success".equalsIgnoreCase(status)) {
                    throw new RuntimeException("文档解析超时(已轮询 " + maxRetries + " 次),请稍后重试");
                }

                System.out.println("✅ 文档解析状态查询成功,开始获取结果...");

                // 4. 调用 GetDocParserResult 获取解析结果
                System.out.println("✅ 解析完成,开始提取内容...");

                GetDocParserResultRequest getReq = new GetDocParserResultRequest();
                getReq.setId(jobId);
                getReq.setLayoutNum(0);
                getReq.setLayoutStepSize(3000);

                GetDocParserResultResponse getResp = client.getDocParserResult(getReq);

                if (getResp == null || getResp.getBody() == null) {
                    throw new RuntimeException("获取解析结果失败,响应数据为空");
                }

                // 将结果响应转换为 JSON
                JsonNode resultNode = mapper.valueToTree(getResp.getBody());
                System.out.println("📨 结果响应 JSON (前500字符): " +
                        resultNode.toString().substring(0, Math.min(500, resultNode.toString().length())));

                // 检查是否有错误
                String errorCode = resultNode.path("code").asText(null);
                if (errorCode != null && !"200".equals(errorCode) && !"Success".equals(errorCode)) {
                    String errorMsg = resultNode.path("message").asText("未知错误");
                    throw new RuntimeException("获取解析结果失败 [" + errorCode + "]: " + errorMsg);
                }

                // 提取 layouts
                JsonNode dataNode = resultNode.path("data");
                if (dataNode.isMissingNode()) {
                    dataNode = resultNode.path("Data");
                }

                if (dataNode.isMissingNode()) {
                    throw new RuntimeException("结果响应中缺少 data 字段");
                }

                JsonNode layoutsNode = dataNode.path("layouts");
                if (layoutsNode.isMissingNode() || !layoutsNode.isArray()) {
                    System.err.println("⚠️ 未找到任何布局区块(layouts)");
                    return parsedBlocks;
                }

                System.out.println("📄 找到 " + layoutsNode.size() + " 个布局区块");

                for (JsonNode layout : layoutsNode) {
                    String type = layout.path("type").asText(layout.path("Type").asText(null));

                    if (type == null) {
                        continue;
                    }

                    // 处理文本块(title, text, paragraph 等)
                    if (type.contains("text") || type.contains("title") || type.contains("paragraph")) {
                        String text = layout.path("text").asText(layout.path("Text").asText(null));
                        if (text != null && !text.trim().isEmpty()) {
                            parsedBlocks.add(new ParsedBlock(ParsedBlock.BlockType.TEXT, text));
                            System.out.println("  ✓ 提取文本块 [" + type + "]: " +
                                    text.substring(0, Math.min(50, text.length())) + "...");
                        }
                    }
                    // 处理图片块
                    else if (type.contains("image") || type.contains("figure")) {
                        String imageUrl = layout.path("imageUrl").asText(
                                layout.path("image_url").asText(
                                        layout.path("ImageUrl").asText(null)));

                        if (imageUrl != null && !imageUrl.isEmpty()) {
                            parsedBlocks.add(new ParsedBlock(ParsedBlock.BlockType.IMAGE, imageUrl));
                            System.out.println("  ✓ 提取图片块: " + imageUrl);
                        }
                    }
                    // 处理表格(可选,未来扩展)
                    else if (type.contains("table")) {
                        String markdownContent = layout.path("markdownContent").asText(
                                layout.path("markdown_content").asText(
                                        layout.path("MarkdownContent").asText(null)));

                        if (markdownContent != null && !markdownContent.isEmpty()) {
                            parsedBlocks.add(new ParsedBlock(ParsedBlock.BlockType.TEXT, markdownContent));
                            System.out.println("  ✓ 提取表格内容");
                        }
                    }
                }

                System.out.println("✅ 总共提取 " + parsedBlocks.size() + " 个区块");
            } // try-with-resources 结束,文件流自动关闭

        } catch (Exception e) {
            System.err.println("❌ 调用阿里云文档解析 API 异常: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("调用阿里云文档解析 API 异常", e);
        }

        return parsedBlocks;
    }


    /**
     * 从 JSON 节点中提取嵌套字段值
     * @param rootNode 根节点
     * @param fields 字段路径(可变参数)
     * @return 字段值,如果不存在返回 null
     */
    private String extractJsonField(JsonNode rootNode, String... fields) {
        JsonNode currentNode = rootNode;
        for (String field : fields) {
            if (currentNode == null || currentNode.isMissingNode()) {
                return null;
            }
            currentNode = currentNode.path(field);
        }
        if (currentNode != null && !currentNode.isMissingNode()) {
            return currentNode.asText();
        }
        return null;
    }

}