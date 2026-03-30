package com.bookmyshow.backend.service;

import com.bookmyshow.backend.config.RabbitMQConfig;
import com.bookmyshow.backend.entity.SeatStatus;
import com.bookmyshow.backend.entity.ShowSeat;
import com.bookmyshow.backend.repository.ShowSeatRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingConfirmationService {

    private final RabbitTemplate rabbitTemplate;
    private final ShowSeatRepository seatRepository;
    private final SeatLockingService seatLockingService;
    private final org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

    @Transactional
    public void confirmBooking(Long showId, List<Long> showSeatIds, List<String> seatNumbers, Long userId) {
        log.info("Confirming booking for user {}, show {}, seats {}", userId, showId, showSeatIds);

        // 1. Database final write (Optimistic Lock @Version check)
        List<ShowSeat> seats = seatRepository.findAllById(showSeatIds);
        for (ShowSeat s : seats) {
            if (s.getStatus() == SeatStatus.BOOKED) {
                throw new IllegalStateException("Seat " + s.getSeatNumber() + " already booked!");
            }
            s.setStatus(SeatStatus.BOOKED);
            s.setLockedByUserId(userId);
        }
        seatRepository.saveAll(seats);

        // 2. Clear Redis Locks and broadcast to other users
        seatLockingService.releaseSeats(showId, seatNumbers);
        
        // Broadcast the final 'BOOKED' status to all subscribers
        messagingTemplate.convertAndSend("/topic/shows/" + showId + "/seats", 
            new com.bookmyshow.backend.dto.SeatStatusEvent(seatNumbers, SeatStatus.BOOKED));

        // 3. Notification Logic (RabbitMQ DLX)
        NotificationPayload payload = new NotificationPayload(userId, showId, seatNumbers, "Booking Confirmed!");
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KEY_SUCCESS, payload);
    }
    
    // Simplistic DTO for Rabbit payload
    @Data
    @AllArgsConstructor
    public static class NotificationPayload {
        private Long userId;
        private Long showId;
        private List<String> seats;
        private String message;
    }
}