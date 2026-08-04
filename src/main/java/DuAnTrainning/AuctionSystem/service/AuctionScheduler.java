package DuAnTrainning.AuctionSystem.service;

import DuAnTrainning.AuctionSystem.enums.AuctionStatus;
import DuAnTrainning.AuctionSystem.enums.AuctionType;
import DuAnTrainning.AuctionSystem.enums.ProductStatus;
import DuAnTrainning.AuctionSystem.repository.AuctionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class AuctionScheduler {

    private final AuctionRepository auctionRepository;

    // Robot chạy ngầm mỗi 10 giây (fixedRate = 10000ms)
    @Scheduled(fixedRate = 10000)
    @Transactional
    public void processAuctionStatusTransitions() {
        LocalDateTime now = LocalDateTime.now();

        // 1. Tự động chuyển SCHEDULED -> RUNNING
        auctionRepository.autoStartAuctions(
                now,
                AuctionStatus.SCHEDULED,
                AuctionStatus.RUNNING,
                ProductStatus.APPROVED
        );

        // 2. Tự động chuyển RUNNING -> ENDED
        auctionRepository.autoEndAuctions(
                now,
                AuctionStatus.RUNNING,
                AuctionStatus.ENDED
        );
        //3. Tự đông từ RUNNING --> EXPRIED
        auctionRepository.autoExpireBuyNowAuctions(
                now, AuctionStatus.RUNNING, AuctionStatus.EXPIRED, AuctionType.BUY_NOW
        );
    }
}
