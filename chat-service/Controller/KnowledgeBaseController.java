// src/main/java/com/example/Controller/KnowledgeBaseController.java
package com.example.Controller;

import com.example.Service.KnowledgeService;
import com.example.model.KnowledgeBaseFile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import com.example.dto.BailianResponse;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.web.bind.annotation.*;


import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/knowledge")
@CrossOrigin(origins = "*")
public class KnowledgeBaseController {

    @Autowired
    private KnowledgeService service;

    @GetMapping("/files")
    public ResponseEntity<List<BailianResponse>> getFiles() {
        return ResponseEntity.ok(service.getAllFiles());
    }

    @GetMapping("/files/{fileId}/content")
    public ResponseEntity<String> getFileContent(@PathVariable String fileId) {
        String content = service.getContentByFileId(fileId);
        if (content != null) {
            return ResponseEntity.ok(content);
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/files")
    public ResponseEntity<?> deleteFile(@RequestParam String fileName) {
        boolean deleted = service.deleteByFileId(fileName);
        if (deleted) {
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            service.uploadFile(
                    file.getOriginalFilename(),
                    file.getBytes(),
                    file.getContentType(),
                    file.getSize()
            );
            return ResponseEntity.ok().build();
        } catch (IOException e) {
            return ResponseEntity.status(500).body("上传失败：" + e.getMessage());
        }
    }

    @PostMapping("/upload-url")
    public ResponseEntity<?> uploadFromUrl(@RequestBody UrlUploadRequest request) {
        service.uploadFromUrl(request.getUrl(), request.getTitle());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/upload-text")
    public ResponseEntity<?> uploadText(@RequestBody TextUploadRequest request) {
        service.uploadTextContent(request.getContent(), request.getTitle());
        return ResponseEntity.ok().build();
    }
}

// 请求 DTO
class UrlUploadRequest {
    private String url;
    private String title;
    // Getters and Setters
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
}

class TextUploadRequest {
    private String content;
    private String title;
    // Getters and Setters
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
}