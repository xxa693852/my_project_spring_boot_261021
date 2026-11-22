package student.jia.my_project_spring_boot_261021.entity;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 管理員取消的不可覆寫快照；同一會員重新報名後仍保留。 */
@Getter
@Entity
@Table(name = "registration_cancellations")
public class RegistrationCancellation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private Long registrationId;
    @Column(nullable = false) private Long eventId;
    @Column(nullable = false) private Long memberId;
    @Column(nullable = false) private Long operatorId;
    @Column(nullable = false, length = 255) private String reason;
    @Column(nullable = false, length = 20) private String previousStatus;
    @Column(length = 32) private String paymentStatus;
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    private LocalDateTime registeredAt;
    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    private LocalDateTime cancelledAt;

    protected RegistrationCancellation() {}

    public RegistrationCancellation(Registration registration, Long operatorId,
            String reason, String paymentStatus, LocalDateTime cancelledAt) {
        this.registrationId = registration.getId();
        this.eventId = registration.getEventId();
        this.memberId = registration.getMemberId();
        this.operatorId = operatorId;
        this.reason = reason;
        this.previousStatus = registration.getStatus();
        this.paymentStatus = paymentStatus;
        this.registeredAt = registration.getRegisteredAt();
        this.cancelledAt = cancelledAt;
    }
}
