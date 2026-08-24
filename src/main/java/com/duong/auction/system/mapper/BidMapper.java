//package com.duong.auction.system.mapper;
//
//import com.duong.auction.system.dto.response.BidHistoryResponseDTO;
//import com.duong.auction.system.entity.Bid;
//import org.mapstruct.Mapper;
//import org.mapstruct.Mapping;
//import org.mapstruct.Named;
//
//import java.util.List;
//
///**
// * Mapper MapStruct ánh xạ lịch sử đặt giá Bid sang DTO công khai kèm quy tắc mã hóa tên người dùng.
// */
//@Mapper(componentModel = "spring")
//public interface BidMapper {
//
//    // 1. Ánh xạ 1 bản ghi Bid sang BidHistoryResponseDTO kèm gọi hàm mã hóa tên maskUsername
//    @Mapping(target = "bidId", source = "id")
//    @Mapping(target = "maskedBidderName", source = "bidder.username", qualifiedByName = "maskUsername")
//    BidHistoryResponseDTO toHistoryDTO(Bid bid);
//
//    // 2. Ánh xạ danh sách bản ghi Bid sang danh sách BidHistoryResponseDTO
//    List<BidHistoryResponseDTO> toHistoryDTOList(List<Bid> bids);
//
//    // 3. Quy tắc mã hóa tên ẩn danh người tham gia đấu giá (Ví dụ: "duong" -> "d***g")
//    @Named("maskUsername")
//    default String maskUsername(String username) {
//        if (username == null || username.isBlank()) return "u***r";
//        if (username.length() <= 2) return username.charAt(0) + "***";
//        return username.charAt(0) + "***" + username.charAt(username.length() - 1);
//    }
//}
package com.duong.auction.system.mapper;

import com.duong.auction.system.dto.response.BidHistoryResponseDTO;
import com.duong.auction.system.entity.Bid;
import com.duong.auction.system.entity.User;
import com.duong.auction.system.repository.UserRepository;
import org.mapstruct.AfterMapping;
import org.mapstruct.BeforeMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;


@Mapper(componentModel = "spring")
public abstract class BidMapper {

    // 🟢 1. TIÊM TRỰC TIẾP UserRepository VÀO TRONG MAPPER
    @Autowired
    protected UserRepository userRepository;

    // Cache tạm thời theo luồng (ThreadLocal) để lưu danh sách User nạp sẵn
    private final ThreadLocal<Map<Long, User>> userCache = new ThreadLocal<>();

    // 🟢 2. CƠ CHẾ @BeforeMapping: Chạy tự động TRƯỚC KHI map danh sách Bids
    @BeforeMapping
    protected void preloadBidders(List<Bid> bids) {
        if (bids == null || bids.isEmpty()) return;

        // Trích xuất toàn bộ bidder_id từ danh sách Bids
        Set<Long> bidderIds = bids.stream()
                .filter(b -> b != null && b.getBidder() != null)
                .map(b -> b.getBidder().getId())
                .collect(Collectors.toSet());

        if (!bidderIds.isEmpty()) {
            // 💥 BẮN 1 CÂU SQL BATCH NẠP TẤT CẢ USER VÀO RAM: SELECT * FROM users WHERE id IN (...)
            Map<Long, User> map = userRepository.findAllById(bidderIds).stream()
                    .collect(Collectors.toMap(User::getId, Function.identity()));
            userCache.set(map);
        }
    }

    // 🟢 3. HÀM MAP DANH SÁCH CHÍNH (Service gọi hàm này)
    public abstract List<BidHistoryResponseDTO> toHistoryDTOList(List<Bid> bids);

    // 🟢 4. HÀM MAP TỪNG BẢN GHI BID SANG DTO
    @Mapping(target = "bidId", source = "id")
    @Mapping(target = "maskedBidderName", expression = "java(getMaskedNameFromCache(bid))")
    public abstract BidHistoryResponseDTO toHistoryDTO(Bid bid);

    // 🟢 5. CƠ CHẾ @AfterMapping: Chạy tự động SAU KHI map xong để dọn dẹp bộ nhớ chống Memory Leak
    @AfterMapping
    protected void clearCache(List<Bid> bids) {
        userCache.remove(); // Xóa sạch ThreadLocal sau khi map xong
    }

    // Hàm phụ trợ: Lấy username từ userCache nạp sẵn ở bước @BeforeMapping
    protected String getMaskedNameFromCache(Bid bid) {
        if (bid == null || bid.getBidder() == null) return "u***r";

        Map<Long, User> map = userCache.get();
        User bidder = (map != null) ? map.get(bid.getBidder().getId()) : null;
        String username = (bidder != null) ? bidder.getUsername() : null;

        return maskUsername(username);
    }

    // Quy tắc mã hóa tên ẩn danh (Ví dụ: "duong" -> "d***g")
    @Named("maskUsername")
    public String maskUsername(String username) {
        if (username == null || username.isBlank()) return "u***r";
        if (username.length() <= 2) return username.charAt(0) + "***";
        return username.charAt(0) + "***" + username.charAt(username.length() - 1);
    }
}
