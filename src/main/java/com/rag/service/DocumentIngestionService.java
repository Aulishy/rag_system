package com.rag.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.stereotype.Service;
import com.rag.config.properties.RagDocumentProperties;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DocumentIngestionService {

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final DocumentParser documentParser = new ApacheTikaDocumentParser(); // 解析器直接 new 即可
    private final RagDocumentProperties documentProperties;//文档切片配置

    // 采用构造器注入依赖和配置，确保不可变性 (final)
    public DocumentIngestionService(EmbeddingStore<TextSegment> embeddingStore,
                                    EmbeddingModel embeddingModel,
                                    RagDocumentProperties documentProperties) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
        this.documentProperties = documentProperties;
    }

    /**
     * 将外部文档灌入向量库（ETL 核心管道）
     * @param filePath 文档的本地路径
     * @param tenantId 租户隔离ID（或者业务模块ID，如 "HR", "IT"）
     */
    public void ingestDocument(Path filePath, String tenantId) {
        // Step 1: 加载并解析文档 (Extract)，打开文件并将解析后打包成一个 Document 对象
        //使用Apache Tika文档解析器：能够根据文档的 MIME 类型自动识别文档内容，并返回一个 Document 对象
        Document document = FileSystemDocumentLoader.loadDocument(filePath, documentParser);

        // Step 2: 智能化文本切片 (Transform)
        // 从 yml 配置文件中读取切片大小和重叠大小
        //使用递归切割，将文档切分为多个片段
        DocumentSplitter splitter = DocumentSplitters.recursive(documentProperties.getMaxSegmentSize(), documentProperties.getMaxOverlapSize());
        //TextSegment是 AI 框架自定义的文本分片存储对象，专门用于 RAG 知识库流程。
        List<TextSegment> segments = splitter.split(document);

        // --- 解决问题1和2：过滤空文本或纯空白文本，以及文档解析产生的无效内容 ---
        List<TextSegment> validSegments = segments.stream()
                .filter(segment -> segment.text() != null && !segment.text().trim().isEmpty())
                .collect(Collectors.toList());

        if (validSegments.isEmpty()) {
            System.out.println("⚠️ 文档 " + filePath.getFileName() + " 解析后没有有效文本内容，跳过导入。");
            return;
        }

        // Step 3:注入企业级元数据 (Metadata Enrichment)
        String fileName = filePath.getFileName().toString();//得到要处理的文件名
        //对切分出的片段添加元数据
        for (TextSegment segment : validSegments) {
            segment.metadata().put("tenant_id", tenantId); // 用于多租户/多部门权限隔离
            segment.metadata().put("file_name", fileName); // 用于溯源“这句话出自哪本书”
        }

        // Step 4: 批量生成向量并持久化入库 (Load)
        System.out.println("正在为 " + fileName + " 生成向量并写入 Qdrant，有效切片总数: " + validSegments.size());

        // embedAll 会自动调用本地 BGE 模型将文本转成浮点数数组
        //调用embeddingModel.embedAll方法将文档片段列表中的文本片段转换成嵌入向量
        //embedAll方法会返回一个Embedding对象列表，该对象列表中的每个元素都对应一个文档片段的嵌入向量。
        List<Embedding> embeddings = embeddingModel.embedAll(validSegments).content();

        // --- 解决问题3：在写入存储前过滤掉维度为 0 或异常的无效向量 ---
        List<Embedding> validEmbeddings = new ArrayList<>();
        List<TextSegment> finalSegments = new ArrayList<>();

        for (int i = 0; i < embeddings.size(); i++) {
            Embedding embedding = embeddings.get(i);
            if (embedding != null && embedding.vector() != null && embedding.vector().length > 0) {
                validEmbeddings.add(embedding);
                finalSegments.add(validSegments.get(i));
            } else {
                String preview = validSegments.get(i).text();
                preview = preview.substring(0, Math.min(20, preview.length()));
                System.out.println("⚠️ 发现并丢弃无效的 0 维度向量，对应文本预览: " + preview);
            }
        }

        if (validEmbeddings.isEmpty()) {
            System.out.println("❌ 文档 " + fileName + " 的所有切片均未能生成有效向量，跳过入库。");
            return;
        }

        // 联动存入 Qdrant：向量和原始文本段落、Metadata 是绑定存储的
        embeddingStore.addAll(validEmbeddings, finalSegments);

        System.out.println("【🎉 成功】文档 " + fileName + " 已成功建立索引！");
    }
}