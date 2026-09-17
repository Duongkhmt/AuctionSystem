package com.duong.auction.system.service;

import com.duong.auction.system.dto.request.BidRequestDTO;
import com.duong.auction.system.dto.response.BidResponseDTO;
import com.duong.auction.system.exception.ApplicationException;
import com.duong.auction.system.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * CLASS FACADE QUẢN LÝ KHÓA PHÂN TÁN (DISTRIBUTED LOCK)
 * Ý nghĩa: Đóng vai trò "Người gác cổng phòng đấu giá".
 * Đảm bảo các luồng Java phải lấy được chìa khóa Redisson ngoài cùng trước khi chui vào Database.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BiddingConcurrencyFacade {

    private final RedissonClient redissonClient;
    private final BiddingService biddingService;

    /**
     * [ĐOẠN 1]: Bọc Khóa Phân Tán cho luồng Đặt Giá Cạnh Tranh (placeBid)
     * Ý nghĩa: Nhét công việc placeBid vào Hộp Callback Supplier và truyền qua hàm hạ tầng xử lý lock.
     */
    public BidResponseDTO placeBidWithLock(Long auctionId, BidRequestDTO requestDTO) {
        return biddingService.placeBid(auctionId, requestDTO);

    }

    /**
     * [ĐOẠN 2]: Bọc Khóa Phân Tán cho luồng Mua Ngay Giá Cố Định (executeBuyNow)
     * Ý nghĩa: Nhét công việc executeBuyNow vào Hộp Callback Supplier để xếp hàng tranh chìa khóa.
     */
    public BidResponseDTO executeBuyNowWithLock(Long auctionId) {
        return executeWithAuctionLock(auctionId, () ->
                biddingService.executeBuyNow(auctionId)
        );
    }


     //HÀM KHUNG HẠ TẦNG XỬ LÝ LOCK NGUYÊN TẮC DRY (Template Method)
     //Gom toàn bộ hạ tầng Redisson Lock vào 1 nơi duy nhất để không bị lặp lại code.

     //@param auctionId ID của phiên đấu giá (dùng để phân định tên ổ khóa)
     //@param supplier  Hộp chứa công việc nghiệp vụ (Callback function)

    private <T> T executeWithAuctionLock(Long auctionId, Supplier<T> supplier) {

        // 🟢 BƯỚC 1: Đặt tên chìa khóa theo auctionId (Ví dụ: "lock:auction:101")
        // Ý nghĩa: Khách xem sản phẩm 101 chỉ tranh chìa khóa 101, không làm ảnh hưởng khách xem sản phẩm 102.
        String lockKey = "lock:auction:" + auctionId;
        RLock lock = redissonClient.getLock(lockKey);

        boolean isAcquired = false; // Cờ đánh dấu luồng này đã cầm được chìa khóa hay chưa
        try {
            // 🟢 BƯỚC 2: Thử xin giữ chìa khóa trong tối đa 5 giây
            // - Tham số 1 (waitTime = 5s): Thời gian tối đa luồng này chấp nhận đứng xếp hàng chờ chìa khóa.
            // - Tham số 2 (leaseTime = -1): Kích hoạt tính năng Watchdog của Redisson.
            //   (Watchdog sẽ ngầm tự động gia hạn khóa nếu Java xử lý chưa xong, tránh bị nhả khóa giữa chừng!)
            isAcquired = lock.tryLock(5, -1, TimeUnit.SECONDS);

            // 🟢 BƯỚC 3: Kiểm tra kết quả xin chìa khóa
            if (!isAcquired) {
                // Nếu sau 5 giây đứng chờ mà vẫn không lấy được chìa khóa (do có quá nhiều người tranh chấp)
                // -> Ném lỗi HTTP 409 CONCURRENT_BID_REJECTED thông báo cho Client thử lại ngay.
                log.warn("Tranh chấp ổ khóa thất bại cho phiên đấu giá auctionId: {}", auctionId);
                throw new ApplicationException(ErrorCode.CONCURRENT_BID_REJECTED);
            }

            // 🟢 BƯỚC 4: ĐÃ CÓ CHÌA KHÓA -> Kích hoạt công việc nghiệp vụ bên trong Hộp
            // Ý nghĩa: Lúc này mới chính thức chui vào BiddingService để mở Transaction kết nối Database!
            return supplier.get();

        } catch (InterruptedException e) {
            // 🟢 BƯỚC 5: Xử lý sự cố nếu luồng Thread Java bị ngắt đột ngột trong lúc đứng chờ
            Thread.currentThread().interrupt();
            throw new ApplicationException(ErrorCode.UNCATEGORIZED_EXCEPTION);

        } finally {
            // 🟢 BƯỚC 6: GIẢI PHÓNG CHÌA KHÓA AN TOÀN TRONG KHỐI FINALLY
            // Ý nghĩa: Khối finally LUÔN CHẠY dù code bên trên thành công hay ném Exception sập.
            // - Điều kiện 1 (isAcquired == true): Chỉ trả khóa nếu luồng này đã từng lấy được khóa.
            // - Điều kiện 2 (lock.isHeldByCurrentThread()): Đảm bảo ĐÚNG LUỒNG ĐANG GIỮ CHÌA KHÓA mới được mở,
            //   tuyệt đối không cho phép mở nhầm chìa khóa của luồng khác!
            if (isAcquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
