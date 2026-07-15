package com.example.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class BailianResponse {

    private String filedId;  // 注意：前端是 filedId（拼写错误？）
    private String fileName;

    public BailianResponse() {}

    public BailianResponse(String filedId, String fileName) {
        this.filedId = filedId;
        this.fileName = fileName;
    }

    public String getFiledId() { return filedId; }
    public void setFiledId(String filedId) { this.filedId = filedId; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
}