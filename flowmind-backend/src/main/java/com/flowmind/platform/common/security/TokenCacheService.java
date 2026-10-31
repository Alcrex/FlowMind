package com.flowmind.platform.common.security;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
@Slf4j
@Service
public class TokenCacheService {
    private final ConcurrentHashMap<String, Long> local = new ConcurrentHashMap<>();
    private final StringRedisTemplate redis;
    private final boolean enabled;
    private volatile long retryAfter;
    public TokenCacheService(StringRedisTemplate redis, @Value("${flowmind.redis-enabled:false}") boolean enabled) { this.redis = redis; this.enabled = enabled; }
    public void blacklistToken(String jti, long remainingMillis) {
        if (remainingMillis <= 0) return;
        local.put(jti, System.currentTimeMillis() + remainingMillis);
        if (useRedis()) try { redis.opsForValue().set("flowmind:blacklist:" + jti, "1", Duration.ofMillis(remainingMillis)); }
        catch (RuntimeException ex) { fallback(); }
    }
    public boolean isBlacklisted(String jti) {
        Long expires = local.get(jti);
        if (expires != null && expires > System.currentTimeMillis()) return true;
        if (expires != null) local.remove(jti, expires);
        if (useRedis()) try { return Boolean.TRUE.equals(redis.hasKey("flowmind:blacklist:" + jti)); }
        catch (RuntimeException ex) { fallback(); }
        return false;
    }
    private boolean useRedis() { return enabled && System.currentTimeMillis() >= retryAfter; }
    private void fallback() { retryAfter = System.currentTimeMillis() + 30000; log.warn("Redis unavailable; using local token blacklist"); }
    @Scheduled(fixedDelay = 60000) public void evictExpired() { long now = System.currentTimeMillis(); local.entrySet().removeIf(e -> e.getValue() <= now); }
}
