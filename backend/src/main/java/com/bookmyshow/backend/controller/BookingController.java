package com.bookmyshow.backend.controller;

import com.bookmyshow.backend.dto.SeatHoldRequest;
import com.bookmyshow.backend.service.BookingConfirmationService;
import com.bookmyshow.backend.service.SeatLockingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/shows")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*") // Allow frontend to connect
public class BookingController {

    private final SeatLockingService seatLockingService;
    private final BookingConfirmationService bookingConfirmationService;
    private final com.bookmyshow.backend.repository.ShowSeatRepository showSeatRepository;

    @GetMapping("/{showId}/seats")
    public ResponseEntity<List<com.bookmyshow.backend.entity.ShowSeat>> getSeats(@PathVariable Long showId) {
        return ResponseEntity.ok(showSeatRepository.findByShowId(showId));
    }

    @PostMapping("/{showId}/hold")
    public ResponseEntity<String> holdSeats(@PathVariable Long showId,
                                            @RequestBody SeatHoldRequest request) {
        boolean success = seatLockingService.holdSeats(showId, request.getSeatNumbers(), request.getUserId());
        
        if (success) {
            log.info("Redis returned success for holding seats.");
            return ResponseEntity.ok("Seats hold successful for 10 minutes.");
        }
        
        log.warn("Failed to acquire Redis hold.");
        return ResponseEntity.status(409).body("One or more seats are currently unavailable.");
    }

    @PostMapping("/{showId}/confirm")
    public ResponseEntity<String> confirmBooking(@PathVariable Long showId,
                                                 @RequestBody ConfirmRequest request) {
        try {
            // Database Optimistic Lock check
            bookingConfirmationService.confirmBooking(showId, request.getSeatDatabaseIds(), request.getSeatNumbers(), request.getUserId());
            return ResponseEntity.ok("Booking Guaranteed!");
        } catch (Exception e) {
            log.error("Optimistic Lock Violation during checkout", e);
            return ResponseEntity.status(409).body("Failed to secure booking at checkout.");
        }
    }

    // Helper inner class
    static class ConfirmRequest {
        private Long userId;
        private List<Long> seatDatabaseIds;
        private List<String> seatNumbers;

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public List<Long> getSeatDatabaseIds() { return seatDatabaseIds; }
        public void setSeatDatabaseIds(List<Long> seatDatabaseIds) { this.seatDatabaseIds = seatDatabaseIds; }
        public List<String> getSeatNumbers() { return seatNumbers; }
        public void setSeatNumbers(List<String> seatNumbers) { this.seatNumbers = seatNumbers; }
    }
}