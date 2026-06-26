package com.example.priceprediction.config;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 的分布式聊天内存存储
 * 支持多实例部署下的对话状态一致性
 */
@Slf4j
@Component
public class RedisChatMemoryStore implements ChatMemoryStore {

    private final StringRedisTemplate redisTemplate;

    public RedisChatMemoryStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // Redis 键前缀
    private static final String CHAT_MEMORY_PREFIX = "chat_memory:";
    // 对话过期时间：24小时
    private static final long CHAT_EXPIRE_HOURS = 24;

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        try {
            String key = CHAT_MEMORY_PREFIX + memoryId;
            String messagesJson = redisTemplate.opsForValue().get(key);

            if (messagesJson == null || messagesJson.isBlank()) {
                return new ArrayList<>();
            }

            List<ChatMessage> messages = ChatMessageDeserializer.messagesFromJson(messagesJson);

            // 刷新过期时间
            redisTemplate.expire(key, CHAT_EXPIRE_HOURS, TimeUnit.HOURS);

            log.debug("从 Redis 获取用户 {} 的对话历史，共 {} 条消息", memoryId, messages.size());
            return messages;

        } catch (Exception e) {
            log.error("获取聊天内存失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        try {
            String key = CHAT_MEMORY_PREFIX + memoryId;
            String messagesJson = ChatMessageSerializer.messagesToJson(messages);
            redisTemplate.opsForValue().set(key, messagesJson, CHAT_EXPIRE_HOURS, TimeUnit.HOURS);
            log.debug("更新用户 {} 的对话历史到 Redis，共 {} 条消息", memoryId, messages.size());

        } catch (Exception e) {
            log.error("更新聊天内存失败: {}", e.getMessage());
        }
    }

    public void compactConversationMemory(Object memoryId) {
        if (memoryId == null) {
            return;
        }
        try {
            String key = CHAT_MEMORY_PREFIX + memoryId;
            String messagesJson = redisTemplate.opsForValue().get(key);
            if (messagesJson == null || messagesJson.isBlank()) {
                return;
            }

            List<ChatMessage> compacted = ChatMessageDeserializer.messagesFromJson(messagesJson)
                    .stream()
                    .filter(this::isConversationMessage)
                    .map(this::compactConversationMessage)
                    .toList();
            redisTemplate.opsForValue().set(
                    key,
                    ChatMessageSerializer.messagesToJson(compacted),
                    CHAT_EXPIRE_HOURS,
                    TimeUnit.HOURS
            );
            log.debug("Compacted chat memory for user {}, kept {} conversation messages", memoryId, compacted.size());
        } catch (Exception e) {
            log.error("Compacting chat memory failed: {}", e.getMessage());
        }
    }

    private boolean isConversationMessage(ChatMessage message) {
        if (message == null || message.type() == ChatMessageType.TOOL_EXECUTION_RESULT) {
            return false;
        }
        if (message instanceof AiMessage aiMessage && aiMessage.hasToolExecutionRequests()) {
            return false;
        }
        return (message.type() == ChatMessageType.USER || message.type() == ChatMessageType.AI)
                && message.text() != null
                && !message.text().isBlank();
    }

    private ChatMessage compactConversationMessage(ChatMessage message) {
        if (message.type() == ChatMessageType.USER) {
            return UserMessage.from(extractOriginalUserQuestion(message.text()));
        }
        if (message instanceof AiMessage aiMessage) {
            return AiMessage.from(aiMessage.text());
        }
        return message;
    }

    private String extractOriginalUserQuestion(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String marker = "用户原始问题：";
        int markerIndex = text.indexOf(marker);
        if (markerIndex < 0) {
            return text.trim();
        }

        String afterMarker = text.substring(markerIndex + marker.length());
        for (String line : afterMarker.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.isBlank()) {
                return trimmed;
            }
        }
        return text.trim();
    }

    @Override
    public void deleteMessages(Object memoryId) {
        try {
            String key = CHAT_MEMORY_PREFIX + memoryId;
            redisTemplate.delete(key);
            log.debug("删除用户 {} 的对话历史", memoryId);

        } catch (Exception e) {
            log.error("删除聊天内存失败: {}", e.getMessage());
        }
    }
}
