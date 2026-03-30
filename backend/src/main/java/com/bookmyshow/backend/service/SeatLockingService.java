package com.bookmyshow.backend.service;

import com.bookmyshow.backend.dto.SeatStatusEvent;
import com.bookmyshow.backend.entity.SeatStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.bookmyshow.backend.entity.ShowSeat;
import com.bookmyshow.backend.repository.ShowSeatRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import com.bookmyshow.backend.config.RabbitMQConfig;
import java.util.HashMap;
import java.util.Map;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SeatLockingService {

    private final StringRedisTemplate redisTemplate;
    private final SimpMessagingTemplate webSocketMessaging;
    private final RabbitTemplate rabbitTemplate;
    private final ShowSeatRepository showSeatRepository;

    private static final long LOCK_EXPIRY_MINUTES = 10;

    /**
     * Attempts to atomically lock multiple seats in Redis.
     * @return true if all seats were successfully locked.
     */
    @Transactional
    public boolean holdSeats(Long showId, List<String> seatNumbers, Long userId) {
        log.info("User {} attempting to lock seats {} for show {}", userId, seatNumbers, showId);

        // Execute Redis Pipeline to acquire locks (SETNX)
        List<Object> results = redisTemplate.executePipelined(new SessionCallback<Object>() {
            @Override
            public Object execute(RedisOperations operations) {
                for (String seatId : seatNumbers) {
                    String lockKey = buildLockKey(showId, seatId);
                    // Use SETNX to guarantee mutual exclusion in Redis
                    operations.opsForValue().setIfAbsent(lockKey, String.valueOf(userId),
                            Duration.ofMinutes(LOCK_EXPIRY_MINUTES));
                }
                return null; // Must return null for executePipelined
            }
        });

        // Evaluate results: Did ALL keys get securely set?
        boolean allLocked = results.stream().allMatch(result -> (Boolean) result);

        if (!allLocked) {
            log.warn("Failed to lock all requested seats for User {}. Rolling back partial locks.", userId);
            // Compensating action: Delete the keys that were successfully locked in this batch
            for (int i = 0; i < results.size(); i++) {
                if ((Boolean) results.get(i)) {
                    redisTemplate.delete(buildLockKey(showId, seatNumbers.get(i)));
                }
            }
            return false;
        }

        log.info("Successfully locked seats for User {}. Broadcasting event and scheduling auto-release.", userId);
        
        // Update database status to LOCKED
        for (String seatNumber : seatNumbers) {
            ShowSeat seat = showSeatRepository.findByShowIdAndSeatNumber(showId, seatNumber);
            if (seat != null) {
                seat.setStatus(SeatStatus.LOCKED);
                seat.setLockedByUserId(userId);
                showSeatRepository.save(seat);
            }
        }

        // Success! Broadcast via WebSocket that these seats are now GREYED OUT
        broadcastSeatStatus(showId, seatNumbers, SeatStatus.LOCKED);
        
        // Schedule auto-release via RabbitMQ Delayed Message
        for (String seatNumber : seatNumbers) {
            Map<String, Object> message = new HashMap<>();
            message.put("showId", showId);
            message.put("seatNumber", seatNumber);
            message.put("userId", userId);

            rabbitTemplate.convertAndSend(RabbitMQConfig.SEAT_WAIT_EXCHANGE, RabbitMQConfig.ROUTING_KEY_SEAT_EXPIRATION, message, m -> {
                m.getMessageProperties().setExpiration(String.valueOf(LOCK_EXPIRY_MINUTES * 60 * 1000));
                return m;
            });
        }
        
        return true;
    }

    public void releaseSeats(Long showId, List<String> seatNumbers) {
        seatNumbers.forEach(seatId -> redisTemplate.delete(buildLockKey(showId, seatId)));
        broadcastSeatStatus(showId, seatNumbers, SeatStatus.AVAILABLE);
    }
    
    private void broadcastSeatStatus(Long showId, List<String> seatNumbers, SeatStatus status) {
        webSocketMessaging.convertAndSend(
                "/topic/shows/" + showId + "/seats",
                new SeatStatusEvent(seatNumbers, status)
        );
    }

    private String buildLockKey(Long showId, String seatId) {
        return "seatlock:" + showId + ":" + seatId;
    }
}