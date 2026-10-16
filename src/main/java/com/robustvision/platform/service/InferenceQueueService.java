package com.robustvision.platform.service;

import com.robustvision.platform.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class InferenceQueueService {
    private final StringRedisTemplate redis;
    private final String mode;
    private final String queueKey;
    private final ThreadLocal<String> receipt = new ThreadLocal<>();
    private static final org.springframework.data.redis.core.script.DefaultRedisScript<String> CLAIM = new org.springframework.data.redis.core.script.DefaultRedisScript<>("local id=redis.call('RPOP',KEYS[1]); if not id then return nil end; local t=redis.call('TIME'); redis.call('HSET',KEYS[2],ARGV[1],id); redis.call('ZADD',KEYS[3],tonumber(t[1])+600,ARGV[1]); return id",String.class);
    private static final org.springframework.data.redis.core.script.DefaultRedisScript<Long> ACK = new org.springframework.data.redis.core.script.DefaultRedisScript<>("redis.call('HDEL',KEYS[1],ARGV[1]); return redis.call('ZREM',KEYS[2],ARGV[1])",Long.class);
    private static final org.springframework.data.redis.core.script.DefaultRedisScript<Long> RECOVER = new org.springframework.data.redis.core.script.DefaultRedisScript<>("local t=redis.call('TIME'); local receipts=redis.call('ZRANGEBYSCORE',KEYS[3],'-inf',t[1],'LIMIT',0,100); for _,r in ipairs(receipts) do local id=redis.call('HGET',KEYS[2],r); if id then redis.call('LPUSH',KEYS[1],id) end; redis.call('HDEL',KEYS[2],r); redis.call('ZREM',KEYS[3],r); end; return #receipts",Long.class);

    public InferenceQueueService(StringRedisTemplate redis,
                                 @Value("${app.queue.mode:inline}") String mode,
                                 @Value("${app.queue.key:personal:inference:jobs}") String queueKey) {
        this.redis = redis;
        this.mode = mode;
        this.queueKey = queueKey;
    }

    public boolean inline() { return !"redis".equalsIgnoreCase(mode); }

    public void enqueue(String taskId) {
        try { redis.opsForList().leftPush(queueKey, taskId); }
        catch (Exception exception) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "INFERENCE_QUEUE_UNAVAILABLE", "推理队列暂时不可用");
        }
    }

    public String poll() {
        if (inline()) return null;
        try {
            String token=java.util.UUID.randomUUID().toString();
            String id=redis.execute(CLAIM,java.util.List.of(queueKey,queueKey+":receipts",queueKey+":leases"),token);
            if(id!=null)receipt.set(token);
            return id;
        }
        catch (Exception ignored) { return null; }
    }
    public void acknowledge(){
        String token=receipt.get();receipt.remove();
        if(token==null||inline())return;
        try{redis.execute(ACK,java.util.List.of(queueKey+":receipts",queueKey+":leases"),token);}catch(RuntimeException unavailable){/* Lease expiry redelivers; database row locking prevents duplicate billing. */}
    }
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay=30000)
    public void recoverExpired(){if(!inline())try{redis.execute(RECOVER,java.util.List.of(queueKey,queueKey+":receipts",queueKey+":leases"));}catch(RuntimeException unavailable){/* Retry next cycle. */}}
}
