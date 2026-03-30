package com.bookmyshow.backend.dto;

import com.bookmyshow.backend.entity.SeatStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeatStatusEvent {
    private List<String> seatNumbers;
    private SeatStatus status;
}