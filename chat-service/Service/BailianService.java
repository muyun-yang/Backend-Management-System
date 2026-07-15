package com.example.Service;

import com.example.model.MessageType;
// import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
// @Slf4j
public class BailianService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BailianService.class);
    private final ChatModel chatModel;
    private final Map<String, List<com.example.model.Message>> chatSessions = new ConcurrentHashMap<>();

    public BailianService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * 同步调用 (Ollama)
     */
    public String call(String sessionId, String prompt) {
        saveMessage(sessionId, new com.example.model.Message(MessageType.USER, prompt));
        
        // 直接调用 Spring AI 抽象接口
        String aiResponse = chatModel.call(prompt);
        
        saveMessage(sessionId, new com.example.model.Message(MessageType.ASSISTANT, aiResponse));
        return aiResponse;
    }

    /**
     * 清空指定会话的所有消息
     */
    public void clearSessionMessages(String sessionId) {
        List<com.example.model.Message> messages = chatSessions.get(sessionId);
        if (messages != null) {
            messages.clear();
            log.info("会话 {} 的消息已清空", sessionId);
        }
    }

    /**
     * 更新指定消息的内容
     * 注意：这里假设你的 Message 模型有 getId() 和 setContent() 方法
     */
    public void updateMessage(String sessionId, String messageId, String newContent) {
        List<com.example.model.Message> messages = chatSessions.get(sessionId);
        if (messages != null) {
            for (com.example.model.Message message : messages) {
                // 这里的 getId 逻辑取决于你 Message 类的具体实现
                if (message.getId() != null && message.getId().equals(messageId)) {
                    message.setContent(newContent);
                    log.debug("消息 {} 已更新内容", messageId);
                    break;
                }
            }
        }
    }

    /**
     * 删除单条消息
     */
    public void deleteMessage(String sessionId, String messageId) {
        List<com.example.model.Message> messages = chatSessions.get(sessionId);
        if (messages != null) {
            messages.removeIf(m -> m.getId() != null && m.getId().equals(messageId));
        }
    }

    /**
     * 基础流式对话 (Ollama)
     */
    public void streamChat(String sessionId, String prompt, SseEmitter emitter) {
    saveMessage(sessionId, new com.example.model.Message(MessageType.USER, prompt));
    StringBuilder fullResponse = new StringBuilder();

    List<Message> springAiMessages = getSpringAiMessages(sessionId);
    Prompt springAiPrompt = new Prompt(springAiMessages);

    chatModel.stream(springAiPrompt).subscribe(
        chunk -> {
            try {
                // 如果 getText() 依然报错，请尝试 chunk.getGeneration().getOutput().getText()
                if (chunk.getResult() != null && chunk.getResult().getOutput() != null) {
                    String content = chunk.getResult().getOutput().getText();
                    if (content != null) {
                        emitter.send(SseEmitter.event().data(content));
                        fullResponse.append(content);
                    }
                }
            } catch (IOException e) {
                log.error("SSE 发送失败", e);
            }
        },
        error -> {
            log.error("流输出异常", error);
            emitter.completeWithError(error);
        },
        () -> {
            saveMessage(sessionId, new com.example.model.Message(MessageType.ASSISTANT, fullResponse.toString()));
            emitter.complete();
        }
    );
}

    /**
     * 流式重写/润色
     */
    public void streamRegenerate(String sessionId, String selectedText, String newPrompt, SseEmitter emitter) {
        String combinedPrompt = String.format(
                "请根据以下内容：「%s」，并结合新要求：「%s」，重新创作一段文字。",
                selectedText, newPrompt
        );

        StringBuilder fullResponse = new StringBuilder();
        chatModel.stream(combinedPrompt).subscribe(
            content -> {
                try {
                    emitter.send(SseEmitter.event().data(content));
                    fullResponse.append(content);
                } catch (IOException e) {
                    log.error("重写发送失败", e);
                }
            },
            error -> emitter.completeWithError(error),
            () -> {
                try {
                    emitter.send(SseEmitter.event().name("REGENERATION_COMPLETE").data(fullResponse.toString()));
                    emitter.complete();
                } catch (IOException e) {
                    log.error("完成通知失败", e);
                }
            }
        );
    }

    /**
     * 图文结合处理 (OCR + Ollama 总结)
     */
    public void streamChatWithImage(String sessionId, String prompt, byte[] fileBytes, String originalFilename, SseEmitter emitter) {
        try {
            // 1. 调用 Python OCR 服务
            String markdownResult = processTableOcr(fileBytes, originalFilename);

            // 2. 构建 Final Prompt
            String finalPrompt = "你是一个表格分析专家。请根据以下 OCR 结果回答用户问题。\n" +
                    "OCR 结果：\n" + markdownResult + "\n" +
                    "用户问题：" + (prompt != null ? prompt : "请总结内容");

            // 3. 记录用户意图
            saveMessage(sessionId, new com.example.model.Message(MessageType.USER, prompt != null ? prompt : "[图片分析]"));

            // 4. 流式输出
            StringBuilder fullResponse = new StringBuilder();
            chatModel.stream(finalPrompt).subscribe(
                chunk -> {
                    try {
                        emitter.send(SseEmitter.event().data(chunk));
                        fullResponse.append(chunk);
                    } catch (IOException e) {
                        log.error("SSE 图片分析发送失败", e);
                    }
                },
                error -> emitter.completeWithError(error),
                () -> {
                    saveMessage(sessionId, new com.example.model.Message(MessageType.ASSISTANT, fullResponse.toString()));
                    emitter.complete();
                }
            );

        } catch (Exception e) {
            log.error("图文对话失败", e);
            emitter.completeWithError(e);
        }
    }

    /**
     * 辅助方法：将自定义 Message 转换为 Spring AI Message
     */
    private List<Message> getSpringAiMessages(String sessionId) {
        List<com.example.model.Message> history = chatSessions.getOrDefault(sessionId, new ArrayList<>());
        return history.stream().map(m -> {
            if (m.getType() == MessageType.USER) {
                return new UserMessage(m.getContent());
            } else if (m.getType() == MessageType.ASSISTANT) {
                // 修复点：直接传入 String 构造
                return new AssistantMessage(m.getContent());
            }
            return new UserMessage(m.getContent());
        }).collect(Collectors.toList());
    }

    // --- 以下是原有的 OCR 处理逻辑，保持不变 ---

    public String processTableOcr(byte[] fileBytes, String originalFilename) throws IOException {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(120000);
        RestTemplate restTemplate = new RestTemplate(factory);

        String suffix = originalFilename.contains(".") ? originalFilename.substring(originalFilename.lastIndexOf(".")) : ".tmp";
        java.io.File tempFile = java.io.File.createTempFile("ocr_", suffix);
        
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile)) {
            fos.write(fileBytes);
        }

        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new FileSystemResource(tempFile));
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity("http://localhost:8000/api/extract-table", requestEntity, Map.class);
            return (String) response.getBody().get("markdown");
        } finally {
            if (tempFile.exists()) tempFile.delete();
        }
    }

    // --- 会话管理方法 ---
    public void saveMessage(String sessionId, com.example.model.Message message) {
        chatSessions.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(message);
    }

    public List<com.example.model.Message> getChatHistory(String sessionId) {
        return chatSessions.getOrDefault(sessionId, new ArrayList<>());
    }

    public void deleteSession(String sessionId) { chatSessions.remove(sessionId); }
}