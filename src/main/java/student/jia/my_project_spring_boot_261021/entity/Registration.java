package student.jia.my_project_spring_boot_261021.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity //這句讓 registrations 成為 Entity
@Table(name = "registrations")
public class Registration {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) //這個 ID 交給資料庫自動遞增（Auto Increment）來產生，Java 程式不需要（也不應該）手動傳入 ID 值。
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(nullable = false, length = 20)
    private String status = "REGISTERED"; // REGISTERED：已報名；CANCELLED：已取消

    @Column(name = "registered_at", nullable = false)
    private LocalDateTime registeredAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Registration() { //無參數建構子，JPA需要它
    }

    public  Registration (Long memberId, Long eventId){
        this.memberId = memberId;
        this.eventId = eventId;
    }

    // @PrePersist: 新建時自動初始化建立與更新時間
    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.registeredAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }
    // @PreUpdate: 修改時自動刷新最後更新時間
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }


}
