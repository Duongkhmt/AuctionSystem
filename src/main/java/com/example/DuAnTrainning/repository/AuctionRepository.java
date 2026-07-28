package com.example.DuAnTrainning.repository;

import com.example.DuAnTrainning.entity.Auction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuctionRepository extends JpaRepository<Auction, Long> {
    Optional<Auction> findByProduct_Id(Long productId);
}
