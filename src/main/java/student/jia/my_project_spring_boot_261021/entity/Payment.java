package student.jia.my_project_spring_boot_261021.entity;

import java.time.LocalDateTime;
import java.time.ZoneId;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
import  student.jia.my_project_spring_boot_261021.constant.PaymentProviders;
import  student.jia.my_project_spring_boot_261021.constant.PaymentStatus;

@Getter
@Setter
@Entity
@Table(name = "payments")
public class Payment {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "registration_id", nullable = false)
    private Long registrationId; // 對應本地報名，同一筆報名可有多次付款。

    @Column(name = "merchant_order_no", nullable = false, length = 64, unique = true)
    private String merchantOrderNo; // 藍新回傳此編號，查回這一次付款。

    @Column(name = "merchant_id", nullable = false, length = 32)
    private String merchantId;

    @Column(nullable = false, length = 32)
    private String provider = PaymentProviders.NEWEBPAY;

    @Column(name = "provider_trade_no", length = 64)
    private String providerTradeNo;

    @Column(name = "payment_method", length = 32)
    private String paymentMethod;

    @Column(nullable = false)
    private Integer amount; // 建單時的費用快照，以整數新台幣元記錄。

    @Column(name = "payment_status", nullable = false, length = 32)
    private String paymentStatus = PaymentStatus.INIT;

    @Column(name = "trade_sha", length = 128)
    private String tradeSha;

    @Column(name = "return_code", length = 32)
    private String returnCode;

    @Column(name = "return_message")
    private String returnMessage;

    @Column(name = "paid_at")
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    private LocalDateTime paidAt;

    @Column(name = "created_at", nullable = false)
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    private LocalDateTime updatedAt;

    // 管理員完成全額退款後，填寫實際處理結果供對帳。
    @Column(name = "refunded_at")
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    private LocalDateTime refundedAt;

    @Column(name = "refund_note")
    private String refundNote;

    protected Payment() {
    }

    public Payment(Long registrationId, String merchantOrderNo, String merchantId, Integer amount) {
        this.registrationId = registrationId;
        this.merchantOrderNo = merchantOrderNo;
        this.merchantId = merchantId;
        this.amount = amount;
    }

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Taipei"));
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now(ZoneId.of("Asia/Taipei"));
    }
}