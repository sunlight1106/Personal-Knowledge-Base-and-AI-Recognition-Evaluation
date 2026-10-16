package com.robustvision.platform.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="REDIS_TEST_PORT",matches=".+")
class RedisDeliveryIntegrationTest {
 LettuceConnectionFactory factory;StringRedisTemplate redis;String key;
 @BeforeEach void setup(){factory=new LettuceConnectionFactory("127.0.0.1",Integer.parseInt(System.getenv("REDIS_TEST_PORT")));factory.afterPropertiesSet();factory.start();redis=new StringRedisTemplate(factory);key="test:delivery:"+UUID.randomUUID();}
 @AfterEach void cleanup(){redis.delete(List.of(key,key+":receipts",key+":leases"));factory.destroy();}
 @Test void crashedWorkerRedeliversOnlyExpiredUnacknowledgedJobs(){
  var first=new InferenceQueueService(redis,"redis",key);var second=new InferenceQueueService(redis,"redis",key);
  first.enqueue("crashed");first.enqueue("completed");assertThat(first.poll()).isEqualTo("crashed");
  assertThat(second.poll()).isEqualTo("completed");second.acknowledge();
  first.recoverExpired();assertThat(second.poll()).isNull();
  var leases=redis.opsForZSet().range(key+":leases",0,-1);assertThat(leases).hasSize(1);
  for(String receipt:leases)redis.opsForZSet().add(key+":leases",receipt,0);
  second.recoverExpired();assertThat(second.poll()).isEqualTo("crashed");second.acknowledge();
  first.acknowledge();second.recoverExpired();assertThat(second.poll()).isNull();
  assertThat(redis.opsForHash().size(key+":receipts")).isZero();
 }
 @Test void realBusDeliversOnlyToMatchingUserOnAnotherInstance()throws Exception{
  var origin=new LiveUpdateService();var peer=new LiveUpdateService();ReflectionTestUtils.setField(origin,"redis",redis);ReflectionTestUtils.setField(origin,"deliveryMode","redis");
  SseEmitter target=mock(SseEmitter.class),other=mock(SseEmitter.class);CountDownLatch latch=new CountDownLatch(1);
  doAnswer(invocation->{latch.countDown();return null;}).when(target).send(any(SseEmitter.SseEventBuilder.class));
  Map<Long,Set<SseEmitter>> clients=new ConcurrentHashMap<>();clients.put(7L,Set.of(target));clients.put(8L,Set.of(other));ReflectionTestUtils.setField(peer,"clients",clients);
  var listener=new RedisMessageListenerContainer();listener.setConnectionFactory(factory);
  listener.addMessageListener((message,pattern)->peer.receive(new String(message.getBody(),java.nio.charset.StandardCharsets.UTF_8)),new ChannelTopic(LiveUpdateService.CHANNEL));listener.afterPropertiesSet();listener.start();
  try {origin.changed(List.of(7L));assertThat(latch.await(5,TimeUnit.SECONDS)).isTrue();verify(target,times(1)).send(any(SseEmitter.SseEventBuilder.class));verifyNoInteractions(other);peer.receive("malformed");peer.receive(UUID.randomUUID()+":invalid");verifyNoInteractions(other);}
  finally {listener.stop();listener.destroy();}
 }
}
