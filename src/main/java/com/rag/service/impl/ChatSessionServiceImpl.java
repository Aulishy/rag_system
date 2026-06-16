package com.rag.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.rag.entity.ChatSession;
import com.rag.mapper.ChatSessionMapper;
import com.rag.service.ChatSessionService;
import org.springframework.stereotype.Service;

@Service
public class ChatSessionServiceImpl extends ServiceImpl<ChatSessionMapper, ChatSession> implements ChatSessionService {
}