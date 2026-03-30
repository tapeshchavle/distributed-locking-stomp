package com.bookmyshow.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "show_seats")
@Getter
@Setter
public class ShowSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long showId;

    @Column(nullable = false)
    private String seatNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatStatus status; // AVAILABLE, LOCKED, BOOKED

    private Long lockedByUserId;

    // Concurrency control via JPA optimistic locking
    @Version
    private Integer version;

    public ShowSeat() {
    }

    public ShowSeat(Long showId, String seatNumber, SeatStatus status) {
        this.showId = showId;
        this.seatNumber = seatNumber;
        this.status = status;
    }
}