package com.example.priceprediction.config;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
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

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // Redis 键前缀
    private static final String CHAT_MEMORY_PREFIX = "chat_memory:";
    // 对话过期时间：24小时
    private static final long CHAT_EXPIRE_HOURS = 24;

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        try {
            String key = CHAT_MEMORY_PREFIX + memoryId;
            @SuppressWarnings("unchecked")
            List<ChatMessage> messages = (List<ChatMessage>) redisTemplate.opsForValue().get(key);

            if (messages == null) {
                return new ArrayList<>();
            }

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
            redisTemplate.opsForValue().set(key, messages, CHAT_EXPIRE_HOURS, TimeUnit.HOURS);
            log.debug("更新用户 {} 的对话历史到 Redis，共 {} 条消息", memoryId, messages.size());

        } catch (Exception e) {
            log.error("更新聊天内存失败: {}", e.getMessage());
        }
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
