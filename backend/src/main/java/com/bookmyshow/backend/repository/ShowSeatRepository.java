package com.bookmyshow.backend.repository;

import com.bookmyshow.backend.entity.ShowSeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShowSeatRepository extends JpaRepository<ShowSeat, Long> {
    List<ShowSeat> findByShowId(Long showId);
    ShowSeat findByShowIdAndSeatNumber(Long showId, String seatNumber);
}