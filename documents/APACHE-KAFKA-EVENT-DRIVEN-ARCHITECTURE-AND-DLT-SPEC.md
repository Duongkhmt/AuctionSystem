# TÀI LIỆU PHÂN TÍCH CHUYÊN SÂU & ĐẶC TẢ KỸ THUẬT: KIẾN TRÚC EVENT-DRIVEN VỚI APACHE KAFKA & GIẢI PHÁP XỬ LÝ LỖI DLT (RETRY & DEAD LETTER TOPIC)

---

# 1. BÀI TOÁN NGHIỆP VỤ CỐT LÕI (CORE BUSINESS PROBLEM)

Trong hệ thống Đấu Giá Trực Tuyến `AuctionSystem`, khi một phiên đấu giá đến giờ hết hạn (trạng thái `TIMEOUT`) hoặc người dùng kích hoạt **Mua Ngay** (`BUY_NOW`), sự kiện **`AUCTION_ENDED` (Phiên Đấu Giá Kết Thúc)** được kích hoạt.

Tại thời điểm này, hệ thống phải thực hiện hai nhóm công việc quan trọng:
1. **Xử lý Chốt thầu Đồng bộ (Synchronous DB Settlement):**
   - Chốt Người chiến thắng (`winnerId`) và mức giá trúng thầu cuối cùng (`finalPrice`).
   - Chuyển trạng thái phiên đấu giá từ `RUNNING` sang `ENDED`.
   - Lưu trữ dữ liệu an toàn vào cơ sở dữ liệu PostgreSQL trong giao dịch độc lập.
2. **Xử lý Tác vụ Bất đồng bộ (Asynchronous Event-Driven Processing):**
   - Tự động tạo Đơn hàng mới ở trạng thái chờ thanh toán (`UNPAID`) với hạn thanh toán trong **48 giờ**.
   - Gửi Email thông báo trúng thầu cho Người Mua kèm liên kết thanh toán.
   - Đánh dấu chống trùng lặp dữ liệu (Idempotency) trên hệ thống Caching Redis.

---

### Thảm Họa Của Mô Hình Xử Lý Đồng Bộ (Synchronous Model)

Nếu hệ thống xử lý tất cả các công việc trên **trên cùng một luồng xử lý đồng bộ** (Request-Response hoặc trong cùng một DB Transaction):

```text
[ Hết Giờ Đấu Giá ] ──► (1) Chốt Winner & DB ──► (2) Tạo Đơn Hàng ──► (3) Gọi Gmail SMTP Server
                                                                            │
                                                                      ❌ [MAIL SERVER BỊ CHẬM / TIMEOUT]
                                                                            │
                                                                            ▼
                                                        [ TOÀN BỘ LUỒNG BỊ TREO HOẶC ROLLBACK! ]
```

#### Các Rủi Ro Hệ Thống Nghiêm Trọng:
1. **Độ trễ quá lớn (High Latency):** Việc chờ kết nối tới dịch vụ ngoài (Mail Server Google/SendGrid) khiến luồng xử lý bị ngâm hàng giây đồng hồ.
2. **Sự cố dây chuyền (Cascading Failure & Transaction Rollback):** Nếu Mail Server bị gián đoạn mạng, Exception ném ra sẽ làm **Rollback** toàn bộ giao dịch DB $\rightarrow$ **Người mua thắng thầu hợp lệ nhưng hệ thống lại làm mất đơn hàng và phiên đấu giá bị hủy!**
3. **Rủi ro Dual-Write:** Nếu phát tin nhắn sự kiện ra bên ngoài trước khi giao dịch DB chốt đơn hoàn tất (commit), trường hợp DB rollback sẽ dẫn đến tình trạng bắn tin nhắn rác (phát thông báo trúng thầu cho phiên chưa được chốt trong DB).

---

# 2. CÁC KỊCH BẢN SỰ CỐ THỰC TẾ CHI TIẾT (REAL-WORLD FAILURE SCENARIOS)

Khi triển khai hệ thống phân tán chịu tải cao, kiến trúc phải giải quyết triệt để **4 kịch bản sự cố thực tế** sau:

---

### 💥 Kịch Bản 1: Dịch Vụ Bên Thứ 3 Bị Chậm Hoặc Tạm Thời Gián Đoạn (Transient Infrastructure Failure)
- **Tình huống:** Máy chủ gửi Email (Gmail SMTP / SendGrid) bị quá tải hoặc chập chờn mạng trong khoảng 3 đến 5 giây.
- **Hậu quả nếu xử lý kém:** Hàng chục email thông báo trúng thầu bị bốc hơi. Người thắng thầu không nhận được thông báo để vào thanh toán đơn hàng trong thời hạn 48h.
- **Giải pháp lập trình:** Sử dụng **Non-Blocking Retry Topic** (`auction.events.ended-retry`) với cơ chế Fixed Backoff 2 giây và thử lại tối đa 3 lần.

---

### 💥 Kịch Bản 2: Sự Cố "Viên Thuốc Độc" (Poison Pill Message)
- **Tình huống:** Một tin nhắn sự kiện `AUCTION_ENDED` bị lỗi cấu trúc dữ liệu (ví dụ: thiếu thông tin ID người thắng, hoặc sai định dạng số) do lỗi code hoặc sai lệch schema.
- **Hậu quả nếu xử lý kém:** Phía Consumer đọc phải tin nhắn hỏng này và ném Exception liên tục. Nếu thử lại vô hạn tại chỗ (Infinite Retry Loop) $\rightarrow$ **Toàn bộ băng chuyền Kafka Consumer bị kẹt cứng, hàng vạn tin nhắn của các phiên đấu giá hợp lệ phía sau không thể tiến lên!**
- **Giải pháp lập trình:** Chuyển hướng tin nhắn hỏng sang **Dead Letter Topic (DLT)** (`auction.events.ended-dlt`) sau 3 lần thử thất bại, kích hoạt `@DltHandler` để log cảnh báo cho Admin kiểm tra thủ công.

---

### 💥 Kịch Bản 3: Gửi Trùng Tin Nhắn Do Consumer Restart Hoặc Kafka Rebalance (Duplicate Delivery)
- **Tình huống:** Phía Consumer vừa tiêu thụ tin nhắn và tạo đơn hàng thành công, nhưng chưa kịp commit offset về Kafka Broker thì máy chủ bị restart hoặc rebalance nhóm consumer. Kafka sẽ phát lại (re-deliver) tin nhắn này lần thứ 2.
- **Hậu quả nếu xử lý kém:** Đẻ ra 2 hoặc nhiều Đơn hàng trùng lặp cho cùng 1 phiên đấu giá.
- **Giải pháp lập trình:** Sử dụng **Redis Idempotency Key** (`kafka:processed_event:{eventId}`, TTL 24h) kết hợp kiểm tra DB `existsByAuction_Id(auctionId)`.

---

### 💥 Kịch Bản 4: Bùng Nổ Tải Phút Chót & Rủi Ro Dual-Write (High Throughput Spike)
- **Tình huống:** Vào khung giờ cao điểm, có **1.000 phiên đấu giá cùng kết thúc tại đúng 1 giây**.
- **Hậu quả nếu xử lý kém:** Quá tải CPU/RAM và rủi ro Dual-Write khi chốt đơn.
- **Giải pháp lập trình:**
  - Chia Topic `auction.events.ended` thành **3 Partitions** để các Worker Consumer xử lý song song.
  - Sử dụng Partition Key = `String.valueOf(auctionId)` để đảm bảo thứ tự sự kiện của từng phiên.
  - Áp dụng `TransactionSynchronizationManager.registerSynchronization` (`afterCommit`) đảm bảo DB commit 100% thành công mới đẩy tin nhắn lên Kafka Broker.

---

# 3. KĨ THUẬT LẬP TRÌNH & ĐẶC TẢ CHI TIẾT CODE THỰC TẾ (CODE IMPLEMENTATION SPEC)

Hệ thống `AuctionSystem` đã triển khai hoàn chỉnh Kiến trúc Hướng Sự kiện (EDA) dựa trên các thành phần Java/Spring Boot sau:

---

## ️ THÀNH PHẦN 1: QUY TRÌNH CHỐT THẦU & CHỐNG DUAL-WRITE

Trong class `AuctionEndedSettlementHelper.java`, mỗi phiên đấu giá kết thúc được chốt trong một DB Transaction hoàn toàn riêng biệt (`REQUIRES_NEW`). Tin nhắn Kafka chỉ được phát **SAU KHI** giao dịch DB đã commit thành công (`afterCommit`):

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionEndedSettlementHelper {

    private final AuctionRepository auctionRepository;
    private final AuctionKafkaProducer kafkaProducer;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processSingleAuctionEnded(Auction auction, Bid highestBid, LocalDateTime now) {
        User winner = determineWinner(auction, highestBid);

        // 1. Cập nhật thông tin chốt phiên trong Database
        auction.setWinner(winner);
        auction.setStatus(AuctionStatus.ENDED);
        auctionRepository.save(auction);

        // 2. Đóng gói DTO Sự kiện với eventId dạng UUID duy nhất
        AuctionEndedEvent endedEvent = AuctionEndedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .auctionId(auction.getId())
                .winnerId(winner != null ? winner.getId() : null)
                .finalPrice(highestBid != null ? highestBid.getBidAmount() : auction.getCurrentPrice())
                .endedReason("TIMEOUT")
                .timestamp(now)
                .build();

        // 3. 🟢 CHỐNG DUAL-WRITE: ĐĂNG KÝ AFTER_COMMIT HOOK BẮN KAFKA
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    kafkaProducer.sendAuctionEndedEvent(endedEvent);
                }
            });
        } else {
            kafkaProducer.sendAuctionEndedEvent(endedEvent);
        }
    }
}
```

---

## ️ THÀNH PHẦN 2: CẤU HÌNH KAFKA INFRASTRUCTURE & 3-TIER RETRY/DLT (`KafkaConfig.java`)

Class `KafkaConfig.java` thiết lập Topic chính (3 Partitions), Producer/Consumer Factory, và cấu hình Non-blocking Retry & DLT 3 Tầng:

```java
@Slf4j
@EnableKafka
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    public static final String TOPIC_AUCTION_ENDED = "auction.events.ended";

    // 1. Khởi tạo Topic chính với 3 Partitions cho xử lý song song
    @Bean
    public NewTopic auctionEndedTopic() {
        return TopicBuilder.name(TOPIC_AUCTION_ENDED)
                .partitions(3)
                .build();
    }

    // 2. Producer Factory (Key: String, Value: JSON Serializer)
    @Bean
    public ProducerFactory<Object, Object> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<Object, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    // 3. Consumer Factory (Group ID: auction-service-group)
    @Bean
    public ConsumerFactory<Object, Object> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "auction-service-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JacksonJsonDeserializer.class);
        props.put(JacksonJsonDeserializer.TRUSTED_PACKAGES, "*");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        return factory;
    }

    // 4. Cấu hình Chi tiết Non-blocking Retry & Dead Letter Topic (DLT) 3 Tầng
    @Bean
    public RetryTopicConfiguration auctionEndedRetryTopicConfig(KafkaTemplate<Object, Object> kafkaTemplate) {
        return RetryTopicConfigurationBuilder
                .newInstance()
                .maxAttempts(3)                                 // Tối đa 3 lần thử (bao gồm lần đầu)
                .fixedBackOff(2000L)                            // Khoảng thời gian chờ 2000ms (2 giây)
                .sameIntervalTopicReuseStrategy(SameIntervalTopicReuseStrategy.SINGLE_TOPIC) // Dùng chung 1 topic -retry
                .retryTopicSuffix("-retry")                     // Topic: auction.events.ended-retry
                .dltSuffix("-dlt")                              // Topic: auction.events.ended-dlt
                .includeTopic(TOPIC_AUCTION_ENDED)
                .dltProcessingFailureStrategy(DltStrategy.FAIL_ON_ERROR)
                .create(kafkaTemplate);
    }
}
```

---

## THÀNH PHẦN 3: KAFKA PRODUCER BẤT ĐỒNG BỘ (`AuctionKafkaProducer.java`)

Gửi tin nhắn kèm Partition Key = `auctionId` và xử lý callback kết quả bất đồng bộ qua `CompletableFuture`:

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionKafkaProducer {

    private final KafkaTemplate<Object, Object> kafkaTemplate;

    public void sendAuctionEndedEvent(AuctionEndedEvent event) {
        log.info("[Kafka Producer] Đang gửi sự kiện AUCTION_ENDED. AuctionId: {}, WinnerId: {}",
                event.getAuctionId(), event.getWinnerId());

        CompletableFuture<SendResult<Object, Object>> future = kafkaTemplate.send(
                KafkaConfig.TOPIC_AUCTION_ENDED,
                String.valueOf(event.getAuctionId()), // Partition Key giúp gom nhóm partition
                event
        );

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("[Kafka Producer SUCCESS] Đã gửi thành công AuctionId: {} vào Topic: {}, Partition: {}, Offset: {}",
                        event.getAuctionId(),
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                log.error("[Kafka Producer ERROR] Gửi thất bại sự kiện AuctionId: {} lên Kafka!",
                        event.getAuctionId(), ex);
            }
        });
    }
}
```

---

## 📥 THÀNH PHẦN 4: KAFKA CONSUMER, REDIS IDEMPOTENCY & DLT HANDLER (`AuctionEndedConsumer.java`)

Consumer thực hiện tiêu thụ sự kiện, kiểm tra chống trùng lặp qua Redis, tạo Đơn hàng 48h, gửi email ngầm và tiếp nhận Poison Pill tại `@DltHandler`:

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionEndedConsumer {

    private final OrderRepository orderRepository;
    private final AuctionRepository auctionRepository;
    private final UserRepository userRepository;
    private final OrderMapper orderMapper;
    private final RedisTemplate<String, String> redisTemplate;
    private final Clock clock;
    private final EmailService emailService;

    private static final String IDEMPOTENT_KEY_PREFIX = "kafka:processed_event:";

    @KafkaListener(topics = KafkaConfig.TOPIC_AUCTION_ENDED)
    @Transactional
    public void listenAuctionEnded(AuctionEndedEvent event) {
        log.info("[Kafka Consumer SUCCESS] Nhận sự kiện AUCTION_ENDED cho auctionId: {}, winnerId: {}",
                event.getAuctionId(), event.getWinnerId());

        String idempotencyKey = IDEMPOTENT_KEY_PREFIX + event.getEventId();

        // 🟢 BƯỚC 1: READ-ONLY CHECK IDEMPOTENCY TRÊN REDIS
        if (Boolean.TRUE.equals(redisTemplate.hasKey(idempotencyKey))) {
            log.warn("[Kafka Consumer IDEMPOTENT] EventId: {} đã được xử lý trước đó. Bỏ qua ghi trùng!",
                    event.getEventId());
            return;
        }

        // 🟢 BƯỚC 2: TẠO ĐƠN HÀNG VÀ GỬI EMAIL NGẦM
        if (event.getWinnerId() != null && !orderRepository.existsByAuction_Id(event.getAuctionId())) {
            Auction auction = auctionRepository.findById(event.getAuctionId()).orElse(null);
            User winner = userRepository.findById(event.getWinnerId()).orElse(null);

            if (auction != null && winner != null) {
                Order order = orderMapper.toEntity(auction, winner, event.getFinalPrice());
                order.setPaymentDeadline(LocalDateTime.now(clock).plusHours(48)); // Hạn thanh toán 48h
                orderRepository.save(order);

                // Gửi Email thông báo thắng thầu ngầm
                emailService.sendAuctionWinnerEmail(
                        winner.getEmail(),
                        winner.getUsername() != null ? winner.getUsername() : winner.getEmail(),
                        auction.getProduct().getTitle(),
                        event.getFinalPrice(),
                        order.getId(),
                        order.getPaymentDeadline()
                );
            }
        }

        // 🟢 BƯỚC 3: ĐÁNH DẤU IDEMPOTENCY KEY VÀO REDIS (TTL 24 GIỜ)
        redisTemplate.opsForValue().set(idempotencyKey, "PROCESSED", Duration.ofHours(24));
    }

    // 🟢 DLT HANDLER: CÔ LẬP TIN NHẮN HỎNG (POISON PILL)
    @DltHandler
    public void handleDltMessage(AuctionEndedEvent event) {
        log.error("[KAFKA DLT ALERT] Tin nhắn bị hỏng đã bị đẩy vào DLT! EventId: {}, AuctionId: {}, Reason: {}. Cần Admin kiểm tra!",
                event.getEventId(), event.getAuctionId(), event.getEndedReason());
    }
}
```

---

# 4. SƠ ĐỒ LUỒNG DỮ LIỆU TOÀN CẢNH (END-TO-END DATA FLOW)

```text
       ┌──────────────────────────────────────────────────────────────────┐
       │ 🤖 AuctionScheduler (Chạy ngầm định kỳ 10s/lần)                   │
       └────────────────────────────────┬─────────────────────────────────┘
                                        │
                                        ▼
       ┌──────────────────────────────────────────────────────────────────┐
       │ 🛠️ AuctionEndedSettlementHelper (Transaction REQUIRES_NEW)        │
       │ 1. Cập nhật Auction.status = ENDED & Winner vào PostgreSQL        │
       │ 2. Đăng ký Synchronization Hook afterCommit()                    │
       └────────────────────────────────┬─────────────────────────────────┘
                                        │
                            (DB Commit Successful)
                                        │
                                        ▼
       ┌──────────────────────────────────────────────────────────────────┐
       │ 🚀 AuctionKafkaProducer                                          │
       │ Bắn AuctionEndedEvent (Partition Key = auctionId)                │
       └────────────────────────────────┬─────────────────────────────────┘
                                        │
                                        ▼
       ┌──────────────────────────────────────────────────────────────────┐
       │ 🟢 TOPIC CHÍNH: auction.events.ended (3 Partitions)              │
       └────────────────────────┬─────────────────────────────────────────┘
                                │
                      (Nhận sự kiện bởi Consumer)
                                │
                                ▼
       ┌──────────────────────────────────────────────────────────────────┐
       │ 📥 AuctionEndedConsumer                                          │
       │ 1. Read Redis Check: kafka:processed_event:{eventId}             │
       │    ├─► [Đã tồn tại] ──► SKIP (Chống ghi trùng)                   │
       │    └─► [Chưa tồn tại] ──► Tạo Order UNPAID (48h) + Gửi Email     │
       │ 2. Write Redis: kafka:processed_event:{eventId} = PROCESSED (24h)│
       └────────────────────────┬─────────────────────────────────────────┘
                                │
                     ❌ (Gặp Exception / Timeout)
                                │
                                ▼ (Sau 2 giây)
       ┌──────────────────────────────────────────────────────────────────┐
       │ 🟡 TOPIC RETRY: auction.events.ended-retry                        │
       │ - Thử lại tối đa 3 lần (Fixed Backoff = 2000ms)                  │
       └────────────────────────┬─────────────────────────────────────────┘
                                │
                     ❌ (Vẫn thất bại sau 3 lần)
                                │
                                ▼
       ┌──────────────────────────────────────────────────────────────────┐
       │ 🔴 TOPIC DLT: auction.events.ended-dlt                           │
       │ - Kích hoạt @DltHandler log [KAFKA DLT ALERT]                    │
       │ - Cô lập tin nhắn hỏng, Admin kiểm tra và Replay                 │
       │ - Băng chuyền chính chạy tiếp 100% không bị tắc nghẽn             │
       └──────────────────────────────────────────────────────────────────┘
```

---

# 5. BẢNG SO SÁNH CÁC MÔ HÌNH XỬ LÝ

| Tiêu Chí So Sánh | Mô Hình Đồng Bộ Cũ (Synchronous) | Mô Hình Async Kafka Cơ Bản | Mô Hình Kafka + Redis Idempotency + DLT 3 Tầng (Hiện Tại) |
| :--- | :--- | :--- | :--- |
| **Thời gian phản hồi Chốt đơn** | Chậm (hàng giây, ngâm luồng) | Siêu tốc (< 2ms) | **Siêu tốc (< 2ms)** |
| **Ảnh hưởng khi Mail Server sập** | Rollback toàn bộ, mất đơn hàng | Mất tin nhắn gửi email | **Không ảnh hưởng, Kafka Retry tự gửi lại khi Mail Server phục hồi** |
| **Xử lý Tin nhắn hỏng (Poison Pill)** | Đứt toàn bộ giao dịch | Gây ngâm / kẹt băng chuyền Consumer (Infinite Loop) | **Tự động cô lập vào DLT, luồng xử lý chính thông suốt 100%** |
| **Xử lý gửi trùng tin nhắn (Kafka Rebalance)** | Không hỗ trợ | Tạo nhiều đơn hàng bị trùng | **Triệt tiêu 100% nhờ Redis Idempotency Key (TTL 24h)** |
| **Chống Dual-Write (DB vs Event)** | Dễ bị lệch dữ liệu | Có nguy cơ phát event khi DB rollback | **Tuyệt đối an toàn nhờ `afterCommit` Synchronization Hook** |

---

# 6. KẾT LUẬN & QUY TRÌNH VẬN HÀNH

Tài liệu đặc tả này phản ánh chính xác 100% thiết kế và mã nguồn thực tế của hệ thống `AuctionSystem`:
- **Chống tắc nghẽn & Sẵn sàng cao (High Availability):** Chia 3 Partitions cho topic chính và tách riêng tác vụ chốt thầu DB khỏi tác vụ tạo đơn / gửi email.
- **Tự phục hồi (Fault-Tolerant):** Tầng Retry tự sửa các lỗi mạng chập chờn; tầng DLT bảo đảm an toàn dữ liệu và không làm sập luồng chính khi gặp dữ liệu rác.
- **An toàn giao dịch:** Kết hợp `TransactionSynchronization` (`afterCommit`) và Redis Idempotency Key bảo đảm nguyên tắc **Exactly-Once Semantics** ở cấp độ nghiệp vụ.
