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
@Entity // 讓 Event 成為 JPA Entity
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "host_name", nullable = false, length = 50)
    private String hostName;

    @Column(nullable = false, length = 255)
    private String location;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Column(name = "registration_deadline", nullable = false)
    private LocalDateTime registrationDeadline;

    @Column(nullable = false)
    private int capacity;

    @Column(name = "beginner_friendly", nullable = false)
    private boolean beginnerFriendly;

    // OPEN：開放報名；CLOSED：已關閉；CANCELLED：已取消
    @Column(nullable = false, length = 20)
    private String status = "OPEN";

    // 建立活動的會員 ID
    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // JPA 需要的無參數建構子
    protected Event() {
    }

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

    // id                    BIGINT       NOT NULL AUTO_INCREMENT,
    // title                 VARCHAR(100) NOT NULL,
    // description           TEXT         NOT NULL,
    // host_name             VARCHAR(50)  NOT NULL,
    // location              VARCHAR(255) NOT NULL,
    // start_at              DATETIME(6)  NOT NULL,
    // end_at                DATETIME(6)  NOT NULL,
    // registration_deadline DATETIME(6)  NOT NULL,
    // capacity              INT          NOT NULL,
    // beginner_friendly     BOOLEAN      NOT NULL DEFAULT FALSE,
    // status                VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    // created_by            BIGINT       NOT NULL,
    // created_at            DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    // updated_at            DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
    //                                    ON UPDATE CURRENT_TIMESTAMP(6),
