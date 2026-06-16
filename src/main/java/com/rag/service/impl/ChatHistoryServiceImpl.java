package com.rag.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.rag.entity.ChatHistory;
import com.rag.mapper.ChatHistoryMapper;
import com.rag.service.ChatHistoryService;
import org.springframework.stereotype.Service;

@Service
public class ChatHistoryServiceImpl extends ServiceImpl<ChatHistoryMapper, ChatHistory> implements ChatHistoryService {
}