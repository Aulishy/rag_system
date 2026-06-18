package com.rag.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.rag.common.Result;
import com.rag.entity.DocAsset;
import com.rag.service.DocAssetService;
import com.rag.service.DocumentIngestionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

@Slf4j
@RestController
@RequestMapping("/api/document")
@CrossOrigin(origins = "http://localhost:5173")
public class DocumentController {

    @Autowired
    private DocumentIngestionService ingestionService;

    @Autowired
    private DocAssetService docAssetService;

    /**
     * 文件上传并解析入库
     */
    @PostMapping("/upload")
    public Result<String> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "tenantId", defaultValue = "default_tenant") String tenantId) {
        log.info("文件：{}", file.getOriginalFilename());
        log.info("租户ID：{}", tenantId);
        // 1. 权限与参数校验
        if (!StpUtil.isLogin()) {
            return Result.error(401, "请先登录后再上传文件");
        }
        if (file.isEmpty()) {
            return Result.error(400, "上传的文件为空");
        }

        // 2. 文件格式校验
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !isValidFileType(originalFilename)) {
            return Result.error(400, "不支持的文件格式，仅支持 PDF、MD、TXT、DOCX 格式");
        }

        // 3. 文件大小校验（限制 50MB）
        long maxSize = 50 * 1024 * 1024;  // 50MB
        if (file.getSize() > maxSize) {
            return Result.error(400, "文件大小超过限制（最大 50MB），当前文件大小: " + formatFileSize(file.getSize()));
        }

        log.info("文件大小: {}", formatFileSize(file.getSize()));

        Path tempFilePath = null;
        DocAsset docAsset = null;

        try {
            // 4. 插入文档资产记录（状态：解析中）
            long uploaderId = StpUtil.getLoginIdAsLong();
            docAsset = new DocAsset();
            docAsset.setFileName(originalFilename);
            docAsset.setUploaderId(uploaderId);
            docAsset.setStatus("PARSING");  // 解析中
            docAsset.setPageCount(0);  // 暂时设为0，后续可以扩展获取PDF页数
            docAsset.setFilePath(tempFilePath != null ? tempFilePath.toString() : null);
            docAsset.setCreateTime(LocalDateTime.now());

            docAssetService.save(docAsset);
            log.info("文档资产记录已创建，ID: {}", docAsset.getId());

            // 5. 临时文件处理
            String prefix = "upload_";
            String suffix = "_" + originalFilename;

            tempFilePath = Files.createTempFile(prefix, suffix);
            file.transferTo(tempFilePath.toFile());

            log.info("收到文件上传请求: {}", originalFilename);

            // 6. 核心业务：解析、切片、向量化入库
            ingestionService.ingestDocument(tempFilePath, tenantId);

            // 7. 更新文档资产记录（状态：成功）
            docAsset.setStatus("SUCCESS");
            docAsset.setFilePath(tempFilePath.toString());
            docAssetService.updateById(docAsset);
            log.info("文档资产记录已更新为成功状态，ID: {}", docAsset.getId());

            return Result.success("文件 '" + originalFilename + "' 上传并处理成功");

        } catch (IOException e) {
            log.error("文件读写失败: {}", originalFilename, e);

            // 更新状态为失败
            if (docAsset != null) {
                docAsset.setStatus("FAILED");
                docAssetService.updateById(docAsset);
            }

            return Result.error("文件读写失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("文档解析入库失败: {}", originalFilename, e);

            // 更新状态为失败
            if (docAsset != null) {
                docAsset.setStatus("FAILED");
                docAssetService.updateById(docAsset);
            }

            return Result.error("文档解析入库失败: " + e.getMessage());
        } finally {
            // 8. 清理资源 (放在 finally 中确保一定会被执行)
            if (tempFilePath != null) {
                try {
                    Files.deleteIfExists(tempFilePath);
                    log.debug("临时文件已清理: {}", tempFilePath);
                } catch (IOException e) {
                    log.warn("清理临时文件失败: {}", tempFilePath, e);
                }
            }
        }
    }

    /**
     * 验证文件类型是否支持
     */
    private boolean isValidFileType(String filename) {
        String lowerCaseFilename = filename.toLowerCase();
        return lowerCaseFilename.endsWith(".pdf")
                || lowerCaseFilename.endsWith(".md")
                || lowerCaseFilename.endsWith(".txt")
                || lowerCaseFilename.endsWith(".docx")
                || lowerCaseFilename.endsWith(".doc");
    }

    private String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", size / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
        }
    }
}
