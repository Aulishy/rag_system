package com.rag.model.dto;

public class ParsedBlock {
    public enum BlockType { TEXT, IMAGE, TABLE }

    private BlockType type;
    private String content;  // 如果是文本就是文字；如果是图片就是图片的 URL

    public ParsedBlock(BlockType type, String content) {
        this.type = type;
        this.content = content;
    }
    public BlockType getType() {
        return type;
    }
    public String getContent() {
        return content;
    }

    public void setType(BlockType type) {
        this.type = type;
    }

    public void setContent(String content) {
        this.content = content;
    }
}