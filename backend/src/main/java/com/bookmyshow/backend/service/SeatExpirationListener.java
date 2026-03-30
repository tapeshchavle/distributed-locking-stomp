package com.bookmyshow.backend.service;

import com.bookmyshow.backend.config.RabbitMQConfig;
import com.bookmyshow.backend.entity.SeatStatus;
import com.bookmyshow.backend.entity.ShowSeat;
import com.bookmyshow.backend.repository.ShowSeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SeatExpirationListener {

    private final ShowSeatRepository seatRepository;
    private final SeatLockingService seatLockingService;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_SEAT_EXPIRATION_DLQ)
    public void handleSeatExpiration(Map<String, Object> message) {
        Long showId = ((Number) message.get("showId")).longValue();
        String seatNumber = (String) message.get("seatNumber");
        Long userId = ((Number) message.get("userId")).longValue();

        log.info("Processing expiration for show {} seat {} held by user {}", showId, seatNumber, userId);

        ShowSeat seat = seatRepository.findByShowIdAndSeatNumber(showId, seatNumber);

        if (seat != null && seat.getStatus() == SeatStatus.LOCKED && userId.equals(seat.getLockedByUserId())) {
            log.info("Seat {} for show {} still locked by user {}. Releasing now.", seatNumber, showId, userId);
            
            // Releasing via seatLockingService ensures Redis is cleared and WebSockets are notified
            seatLockingService.releaseSeats(showId, Collections.singletonList(seatNumber));
            
            // Also update DB status to AVAILABLE (since releaseSeats only clears Redis and notifies)
            seat.setStatus(SeatStatus.AVAILABLE);
            seat.setLockedByUserId(null);
            seatRepository.save(seat);
            
            log.info("Seat {} successfully released for show {}", seatNumber, showId);
        } else {
            log.info("Seat {} for show {} is already {} or held by another user. Skipping release.", 
                seatNumber, showId, seat != null ? seat.getStatus() : "NULL");
        }
    }
}
