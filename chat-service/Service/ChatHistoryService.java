package com.example.Service;

import com.example.model.Message;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class ChatHistoryService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final long SESSION_TIMEOUT_MINUTES = 30;

    public ChatHistoryService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private String getSessionKey(String sessionId) {
        return "chat:session:" + sessionId;
    }

    /**
     * 获取指定会话ID的所有聊天记录
     * @param sessionId 会话ID
     * @return 聊天记录列表
     */
    public List<Message> getMessages(String sessionId) {
        String sessionKey = getSessionKey(sessionId);
        List<Object> rawMessages = redisTemplate.opsForList().range(sessionKey, 0, -1);
        if (rawMessages == null) {
            return new java.util.ArrayList<>();
        }
        return rawMessages.stream()
                .map(obj -> (Message) obj)
                .collect(Collectors.toList());
    }

    /**
     * 将一条消息保存到指定会话的历史记录中
     * @param sessionId 会话ID
     * @param message 要保存的消息
     */
    public void saveMessage(String sessionId, Message message) {
        String sessionKey = getSessionKey(sessionId);
        redisTemplate.opsForList().rightPush(sessionKey, message);
        // 设定会话超时时间为30分钟
        long SESSION_TIMEOUT_MINUTES = 30;
        redisTemplate.expire(sessionKey, SESSION_TIMEOUT_MINUTES, TimeUnit.MINUTES); // 更新超时时间
    }

    /**
     * 删除指定会话ID的所有聊天记录
     * @param sessionId 会话ID
     */
    public void deleteSession(String sessionId) {
        String sessionKey = getSessionKey(sessionId);
        redisTemplate.delete(sessionKey);
    }

    /**
     * 根据消息ID删除指定会话中的某条消息
     * @param sessionId 会话ID
     * @param messageId 消息ID
     */
    public void deleteMessage(String sessionId, String messageId) {
        String sessionKey = getSessionKey(sessionId);
        List<Message> messages = getMessages(sessionId);
        messages.removeIf(m -> m.getId().equals(messageId));

        // 清除旧列表并保存新列表
        redisTemplate.delete(sessionKey);
        if (!messages.isEmpty()) {
            redisTemplate.opsForList().rightPushAll(sessionKey, messages.toArray());
        }
    }
}