-- 1. 创建数据库（如果不存在则创建），并设置默认字符集为 utf8mb4（支持存储 Emoji 等特殊字符）
CREATE DATABASE IF NOT EXISTS `rag_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 2. 切换到刚创建的数据库
USE `rag_db`;

-- 3. 创建用户表：存储谁能登录系统
CREATE TABLE IF NOT EXISTS `sys_user` (
                                          `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                          `username` varchar(50) NOT NULL COMMENT '用户名',
                                          `password` varchar(100) NOT NULL COMMENT '密码(加密)',
                                          `role` varchar(20) DEFAULT 'USER' COMMENT '角色(USER/ADMIN)',
                                          `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                          PRIMARY KEY (`id`),
                                          UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 4. 创建会话记录表：存储用户的聊天列表
CREATE TABLE IF NOT EXISTS `chat_session` (
                                              `session_id` varchar(64) NOT NULL COMMENT '会话唯一ID(关联Langchain4j的MemoryId)',
                                              `user_id` bigint(20) NOT NULL COMMENT '所属用户ID',
                                              `title` varchar(100) DEFAULT '新会话' COMMENT '会话标题(可由AI自动生成)',
                                              `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                              `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后对话时间',
                                              PRIMARY KEY (`session_id`),
                                              KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话列表表';

-- 5. 创建文档资产表：记录用户上传的文档元数据
CREATE TABLE IF NOT EXISTS `doc_asset` (
                                           `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                           `file_name` varchar(255) NOT NULL COMMENT '文件原始名称',
                                           `uploader_id` bigint(20) NOT NULL COMMENT '上传人ID',
                                           `status` varchar(20) DEFAULT 'PENDING' COMMENT '解析状态(PENDING/PARSING/SUCCESS/FAILED)',
                                           `page_count` int(11) DEFAULT '0' COMMENT '总页数',
                                           `file_path` varchar(500) COMMENT '文件存储路径(本地或OSS)',
                                           `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
                                           PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档资产表';


CREATE TABLE IF NOT EXISTS `chat_history` (
                                              `session_id` varchar(64) NOT NULL COMMENT '关联 chat_session 表的 ID',
                                              `history_json` longtext NOT NULL COMMENT 'LangChain4j 序列化后的聊天记录 JSON',
                                              PRIMARY KEY (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 聊天记忆表';