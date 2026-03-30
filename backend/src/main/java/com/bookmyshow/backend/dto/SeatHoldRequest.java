package com.bookmyshow.backend.dto;

import lombok.Data;

import java.util.List;

@Data
public class SeatHoldRequest {
    private List<String> seatNumbers;
    private Long userId;
}