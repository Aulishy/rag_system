package com.rag.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.rag.entity.DocAsset;
import com.rag.mapper.DocAssetMapper;
import com.rag.service.DocAssetService;
import org.springframework.stereotype.Service;

@Service
public class DocAssetServiceImpl extends ServiceImpl<DocAssetMapper, DocAsset> implements DocAssetService {
}
