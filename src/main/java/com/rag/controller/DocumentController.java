package com.rag.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.rag.common.Result;
import com.rag.service.DocumentIngestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/document")
@CrossOrigin(origins = "*")
public class DocumentController {

    @Autowired
    private DocumentIngestionService ingestionService;

    /**
     * 文件上传并解析入库
     */
    @PostMapping("/upload")
    public Result<String> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "tenantId", defaultValue = "default_tenant") String tenantId) {

        // 1. 权限与参数校验
        if (!StpUtil.isLogin()) {
            return Result.error(401, "请先登录后再上传文件");
        }
        if (file.isEmpty()) {
            return Result.error(400, "上传的文件为空");
        }

        Path tempFilePath = null;
        try {
            // 2. 临时文件处理 (理想情况下这部分逻辑也可以抽到 DocumentService 中)
            String originalFilename = file.getOriginalFilename();
            String prefix = "upload_";
            String suffix = originalFilename != null ? "_" + originalFilename : ".tmp";

            tempFilePath = Files.createTempFile(prefix, suffix);
            file.transferTo(tempFilePath.toFile());

            System.out.println("收到文件上传请求: " + originalFilename);

            // 3. 核心业务：解析、切片、向量化入库
            ingestionService.ingestDocument(tempFilePath, tenantId);

            return Result.success("文件 '" + originalFilename + "' 上传并处理成功");

        } catch (IOException e) {
            e.printStackTrace();
            return Result.error("文件读写失败: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("文档解析入库失败: " + e.getMessage());
        } finally {
            // 4. 清理资源 (放在 finally 中确保一定会被执行)
            if (tempFilePath != null) {
                try {
                    Files.deleteIfExists(tempFilePath);
                } catch (IOException e) {
                    System.err.println("清理临时文件失败: " + tempFilePath);
                }
            }
        }
    }
}