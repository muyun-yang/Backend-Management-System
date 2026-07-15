package com.example.Controller;

import com.example.Service.BailianService;
import com.example.model.Message;
import com.example.model.MessageType;
// import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
// @Slf4j // 建议加上日志
public class ChatController {

    private final BailianService bailianService;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BailianService.class);

    public ChatController(BailianService bailianService) {
        this.bailianService = bailianService;
    }

    @PostMapping("/sessions")
    public ResponseEntity<String> createSession() {
        String newSessionId = UUID.randomUUID().toString();
        // 系统消息初始化
        Message systemMessage = new Message(MessageType.SYSTEM, "会话已创建。");
        bailianService.saveMessage(newSessionId, systemMessage);
        return ResponseEntity.ok(newSessionId);
    }

    /**
     * 普通文字对话流
     */
    @PostMapping(value = "/sessions/{sessionId}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sendMessage(@PathVariable String sessionId, @RequestBody Map<String, String> payload) {
        String prompt = payload.get("prompt");
        SseEmitter emitter = new SseEmitter(1800000L); // 30分钟超时

        if (prompt == null || prompt.trim().isEmpty()) {
            try {
                emitter.send(SseEmitter.event().name("error").data("Prompt cannot be empty."));
                emitter.complete();
            } catch (IOException e) {
                log.error("发送错误信息失败", e);
            }
            return emitter;
        }

        // 调用 Service 内部的异步流 (Spring AI subscribe 是非阻塞的)
        bailianService.streamChat(sessionId, prompt, emitter);
        return emitter;
    }

    /**
     * 获取历史记录
     */
    @GetMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<List<Message>> getChatHistory(@PathVariable String sessionId) {
        List<Message> messages = bailianService.getChatHistory(sessionId);
        return ResponseEntity.ok(messages);
    }

    /**
     * 流式重写/润色
     */
    @PostMapping(value = "/sessions/{sessionId}/regenerate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter regenerate(@PathVariable String sessionId, @RequestBody Map<String, String> payload) {
        String selectedText = payload.get("selectedText");
        String newPrompt = payload.get("newPrompt");
        SseEmitter emitter = new SseEmitter(1800000L);

        if (selectedText == null || newPrompt == null) {
            try {
                emitter.send(SseEmitter.event().name("error").data("Missing parameters"));
                emitter.complete();
            } catch (IOException e) { /* ignore */ }
            return emitter;
        }

        bailianService.streamRegenerate(sessionId, selectedText, newPrompt, emitter);
        return emitter;
    }

    /**
     * 毕设：图片 OCR + 对话流
     */
    @PostMapping(value = "/sessions/{sessionId}/messages/image", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter handleImageUpload(
            @PathVariable String sessionId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "prompt", required = false) String prompt) throws IOException {

        // 1. 立即读取字节，防止 MultipartFile 在异步线程中失效
        byte[] fileBytes = file.getBytes();
        String originalFilename = file.getOriginalFilename();
        SseEmitter emitter = new SseEmitter(300000L); // 5分钟超时

        // 2. 开启异步线程处理耗时的 OCR 和模型调用
        new Thread(() -> {
            try {
                log.info("开始处理图片上传会话: {}", sessionId);
                bailianService.streamChatWithImage(sessionId, prompt, fileBytes, originalFilename, emitter);
            } catch (Exception e) {
                log.error("图片对话处理异常", e);
                try {
                    emitter.send(SseEmitter.event().name("error").data("分析失败: " + e.getMessage()));
                } catch (IOException ignored) {}
                emitter.completeWithError(e);
            }
        }).start();

        return emitter;
    }

    // --- 简单的删除和修改接口，逻辑保持不变 ---

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> deleteSession(@PathVariable String sessionId) {
        bailianService.deleteSession(sessionId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<Void> clearChat(@PathVariable String sessionId) {
        bailianService.clearSessionMessages(sessionId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/sessions/{sessionId}/messages/{messageId}")
    public ResponseEntity<Void> updateMessageContent(
            @PathVariable String sessionId,
            @PathVariable String messageId,
            @RequestBody Map<String, String> payload) {
        String newContent = payload.get("newContent");
        if (newContent == null) return ResponseEntity.badRequest().build();
        bailianService.updateMessage(sessionId, messageId, newContent);
        return ResponseEntity.noContent().build();
    }
}