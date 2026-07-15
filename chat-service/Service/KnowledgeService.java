package com.example.Service;

// com/example/service/KnowledgeFileService.java
import com.example.model.KnowledgeBaseFile;
import com.example.repository.KnowledgeFileRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.example.dto.BailianResponse;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.jsoup.select.Elements;

@Service
public class KnowledgeService {

    @Autowired
    private KnowledgeFileRepository repository;

    // 生成唯一 file_id
    private String generateFileId() {
        return "file_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 3);
    }

    // 获取文件列表（返回 DTO）
    public List<BailianResponse> getAllFiles() {
        return repository.findAllByOrderByIdDesc().stream()
                .map(file -> new BailianResponse(file.getFileId(), file.getFileName()))
                .collect(Collectors.toList());
    }

    // 根据 fileId 获取内容
    public String getContentByFileId(String fileId) {
        return repository.findByFileId(fileId)
                .map(KnowledgeBaseFile::getContent)
                .orElse(null);
    }

    // 删除文件
    @Transactional
    public boolean deleteByFileId(String fileId) {
        if (repository.findByFileId(fileId).isPresent()) {
            repository.deleteByFileId(fileId);
            return true;
        }
        return false;
    }

    // 上传本地文件


    @Transactional
    public void uploadFile(String originalFilename, byte[] contentBytes, String contentType, Long size) {
        String fileId = generateFileId();
        String url = "/uploads/" + fileId;

        try {
            String extractedText;
            if ("application/pdf".equals(contentType)) {
                // 解析 PDF 内容
                extractedText = extractTextFromPdf(contentBytes);
            } else {
                // 其他文本文件（如 .txt）
                extractedText = new String(contentBytes, StandardCharsets.UTF_8);
            }

            KnowledgeBaseFile file = new KnowledgeBaseFile();
            file.setFileId(fileId);
            file.setFileName(originalFilename);
            file.setFileUrl(url);
            file.setFileType(contentType);
            file.setFileSize(size);
            file.setUploadMethod("file");
            file.setContent(extractedText); // 存的是提取的文本，不是二进制
            file.setUploadDate(LocalDateTime.now());
            file.setCreatedAt(LocalDateTime.now());
            file.setUpdatedAt(LocalDateTime.now());

            repository.save(file);

        } catch (Exception e) {
            throw new RuntimeException("Failed to process file: " + e.getMessage(), e);
        }
    }

    // 提取 PDF 文本
    private String extractTextFromPdf(byte[] contentBytes) throws IOException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(contentBytes);
             PDDocument document = PDDocument.load(bais)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // 通过 URL 上传（简化版：只存 URL 和内容）
    @Transactional
    public void uploadFromUrl(String url, String customTitle) {
        String fileId = generateFileId();

        try {
            // 1. 下载网页
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko)")
                    .timeout(10000)
                    .get();

            // 2. 提取标题：优先使用传入的 customTitle，否则用网页 title
            String extractedTitle = doc.title(); // 比如："南湖区新兴街道创新治理模式 - 浙江在线"

            // 去掉网站名后缀（可选优化）
            String cleanedTitle = cleanTitle(extractedTitle);

            String finalTitle = (customTitle != null && !customTitle.trim().isEmpty())
                    ? customTitle
                    : cleanedTitle;

            // 3. 提取正文内容
            String content = extractContent(doc);

            // 4. 保存到数据库
            KnowledgeBaseFile file = new KnowledgeBaseFile();
            file.setFileId(fileId);
            file.setFileName(finalTitle);     // ✅ 这里设置标题
            file.setFileUrl(url);
            file.setUploadMethod("url");
            file.setContent(content);
            file.setUploadDate(LocalDateTime.now());
            file.setCreatedAt(LocalDateTime.now());
            file.setUpdatedAt(LocalDateTime.now());

            repository.save(file);

        } catch (Exception e) {
            // 失败时也保存一条记录
            KnowledgeBaseFile file = new KnowledgeBaseFile();
            file.setFileId(fileId);
            file.setFileName(customTitle != null ? customTitle : "【解析失败】");
            file.setFileUrl(url);
            file.setUploadMethod("url");
            file.setContent("【内容解析失败】" + e.getMessage());
            file.setUploadDate(LocalDateTime.now());
            file.setCreatedAt(LocalDateTime.now());
            file.setUpdatedAt(LocalDateTime.now());
            repository.save(file);
        }
    }
    private String cleanTitle(String title) {
        if (title == null) return "Unknown";

        // 常见分隔符：-, _, |, —
        return title
                .replaceAll("\\s*[-_—|]\\s*浙江在线.*$", "")
                .replaceAll("\\s*[-_—|]\\s*新华网.*$", "")
                .replaceAll("\\s*[-_—|]\\s*人民网.*$", "")
                .trim();
    }

    // 提取网页正文内容（可根据网站结构调整）
    private String extractContent(Document doc) {
        String[] selectors = {
                "div#zoom",           // 优先匹配你目标网站
                "div.article-content",
                "article",
                "div.content",
                "div.main-content",
                "div#con_con"
        };

        for (String selector : selectors) {
            Elements elements = doc.select(selector);
            if (!elements.isEmpty()) {
                // 取第一个匹配的元素的文本
                return elements.first().text();
            }
        }

        // 备用：返回整个 body 的文本（可能包含导航、广告等噪音）
        return doc.body().text();
    }

    // 上传文本内容
    @Transactional
    public void uploadTextContent(String content, String title) {
        String fileId = generateFileId();
        String fileName = title != null && !title.isEmpty() ? title : "Untitled Text";

        KnowledgeBaseFile file = new KnowledgeBaseFile();
        file.setFileId(fileId);
        file.setFileName(fileName);
        file.setFileUrl("/text/" + fileId);
        file.setUploadMethod("text");
        file.setContent(content);
        file.setUploadDate(LocalDateTime.now());
        file.setCreatedAt(LocalDateTime.now());
        file.setUpdatedAt(LocalDateTime.now());

        repository.save(file);
    }
}