package student.jia.my_project_spring_boot_261021.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import student.jia.my_project_spring_boot_261021.constant.PaymentStatus;
import student.jia.my_project_spring_boot_261021.entity.Event;
import student.jia.my_project_spring_boot_261021.entity.Payment;
import student.jia.my_project_spring_boot_261021.entity.Registration;
import student.jia.my_project_spring_boot_261021.repository.EventRepository;
import student.jia.my_project_spring_boot_261021.repository.MemberRepository;
import student.jia.my_project_spring_boot_261021.repository.PaymentRepository;
import student.jia.my_project_spring_boot_261021.repository.RegistrationRepository;

@Service
public class PaymentLifecycle {
    private final EventRepository events;
    private final RegistrationRepository registrations;
    private final PaymentRepository payments;
    private final MemberRepository members;

    public PaymentLifecycle(EventRepository events, RegistrationRepository registrations,
            PaymentRepository payments, MemberRepository members) {
        this.events = events;
        this.registrations = registrations;
        this.payments = payments;
        this.members = members;
    }

    static LocalDateTime now() {
        return LocalDateTime.now(ZoneId.of("Asia/Taipei"));
    }

    // 所有會改動名額的流程先鎖活動。付款與報名的變更在同一筆交易完成。
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void applyNotice(String orderNo, String merchantId, int amount, String tradeNo,
            String code, boolean success) {
        // 先查活動 ID，再鎖活動。鎖定後第一次讀取付款實體，才能看見前一筆通知的結果。
        Long eventId = payments.findEventIdByMerchantOrderNo(orderNo)
                .orElseThrow(() -> new IllegalArgumentException("查無付款訂單。"));
        Event event = events.findByIdForUpdate(eventId).orElseThrow();
        Payment payment = payments.findByMerchantOrderNo(orderNo).orElseThrow();
        if (!payment.getMerchantId().equals(merchantId) || payment.getAmount() != amount) {
            throw new IllegalArgumentException("付款通知的商店編號或金額不符。");
        }
        if (payment.getPaidAt() != null) {
            if (success && !tradeNo.equals(payment.getProviderTradeNo())) {
                throw new IllegalArgumentException("同一訂單收到不同交易編號。");
            }
            return;
        }

        Registration registration = registrations.findById(payment.getRegistrationId()).orElseThrow();
        Payment latest = payments.findFirstByRegistrationIdOrderByIdDesc(registration.getId()).orElseThrow();
        LocalDateTime now = now();
        boolean currentHold = latest.getId().equals(payment.getId())
                && "PENDING_PAYMENT".equals(registration.getStatus());
        if (success) {
            payment.setPaidAt(now);
            payment.setProviderTradeNo(tradeNo);
            payment.setPaymentMethod("CREDIT");
            payment.setPaymentStatus(PaymentStatus.REVIEW_REQUIRED);
            if (currentHold && "OPEN".equals(event.getStatus())
                    && now.isBefore(registration.getPaymentExpiresAt())
                    && now.isBefore(event.getRegistrationDeadline())) {
                registration.setStatus("REGISTERED");
                registration.setRegisteredAt(now);
                registration.setPaymentExpiresAt(null);
                payment.setPaymentStatus(PaymentStatus.PAID);
            }
            return;
        }

        // 明確失敗的通知只釋放目前這次保留，舊訂單無法取消新的報名。
        if (currentHold) {
            registration.setStatus("CANCELLED");
            registration.setCancelledAt(now);
            registration.setPaymentExpiresAt(null);
            payment.setPaymentStatus(PaymentStatus.FAILED);
            payment.setReturnCode(code);
        }
    }

    static String cancelLatest(PaymentRepository payments, Long registrationId) {
        Optional<Payment> result = payments.findFirstByRegistrationIdOrderByIdDesc(registrationId);
        if (result.isEmpty()) {
            return null;
        }
        Payment payment = result.get();
        if (PaymentStatus.PAID.equals(payment.getPaymentStatus())) {
            payment.setPaymentStatus(PaymentStatus.REFUND_PENDING);
            return "已付款，將全額退款。";
        }
        if (PaymentStatus.INIT.equals(payment.getPaymentStatus())
                || PaymentStatus.PENDING.equals(payment.getPaymentStatus())) {
            payment.setPaymentStatus(PaymentStatus.CANCELLED);
        }
        return "這筆報名尚未確認付款。若已扣款，請聯絡管理員。";
    }

    @Transactional(readOnly = true)
    public List<PaymentRecord> list(Long memberId, Long eventId) {
        requireAdmin(memberId);
        List<PaymentRecord> result = new ArrayList<>();
        for (Payment payment : payments.findForEvent(eventId)) {
            result.add(new PaymentRecord(payment.getId(), payment.getRegistrationId(),
                    payment.getMerchantOrderNo(), payment.getAmount(), payment.getPaymentStatus(),
                    payment.getPaidAt(), payment.getRefundedAt(), payment.getRefundNote(),
                    payment.getProviderTradeNo()));
        }
        return result;
    }

    // 此操作只記錄已在金流後台完成的全額退款。
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void recordRefund(Long memberId, Long eventId, Long paymentId, String note) {
        requireAdmin(memberId);
        events.findByIdForUpdate(eventId).orElseThrow(() -> new IllegalArgumentException("查無活動。"));
        Payment payment = payments.findById(paymentId).orElseThrow(() -> new IllegalArgumentException("查無付款。"));
        Registration registration = registrations.findById(payment.getRegistrationId()).orElseThrow();
        if (!eventId.equals(registration.getEventId())) {
            throw new IllegalArgumentException("此付款不屬於指定活動。");
        }
        if (PaymentStatus.REFUNDED.equals(payment.getPaymentStatus())) {
            return;
        }
        if (!PaymentStatus.REFUND_PENDING.equals(payment.getPaymentStatus())
                && !PaymentStatus.REVIEW_REQUIRED.equals(payment.getPaymentStatus())) {
            throw new IllegalArgumentException("此付款目前無法登記退款。");
        }
        payment.setPaymentStatus(PaymentStatus.REFUNDED);
        payment.setRefundedAt(now());
        payment.setRefundNote(note);
    }

    private void requireAdmin(Long memberId) {
        var member = members.findByIdAndStatus(memberId, "ACTIVE");
        if (member.isEmpty() || !"ADMIN".equals(member.get().getRole()) || member.get().isSuspendedAt(now())) {
            throw new IllegalArgumentException("只有有效的管理員可以處理退款。");
        }
    }

    public record PaymentRecord(Long id, Long registrationId, String merchantOrderNo,
            int amount, String status, LocalDateTime paidAt, LocalDateTime refundedAt,
            String refundNote, String providerTradeNo) {}
}
