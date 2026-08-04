package DuAnTrainning.AuctionSystem.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "categories")
@Getter @Setter
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parent_id")
    private Long parentId; // Phân cấp danh mục (self-reference)

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "requires_verification", nullable = false)
    private boolean requiresVerification = false;

    @Column(name = "requires_deposit", nullable = false)
    private boolean requiresDeposit = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}