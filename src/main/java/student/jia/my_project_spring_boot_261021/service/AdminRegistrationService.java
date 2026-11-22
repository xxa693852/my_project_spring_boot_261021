package student.jia.my_project_spring_boot_261021.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import student.jia.my_project_spring_boot_261021.constant.ResponseMessages;
import student.jia.my_project_spring_boot_261021.entity.*;
import student.jia.my_project_spring_boot_261021.exception.ResourceNotFoundException;
import student.jia.my_project_spring_boot_261021.repository.*;

@Service
public class AdminRegistrationService {
    private final RegistrationCancellationMail notificationMail;
    private final EventService eventService;
    private final EventRepository events;
    private final RegistrationRepository registrations;
    private final MemberRepository members;
    private final PaymentRepository payments;
    private final RegistrationCancellationRepository cancellations;

    public AdminRegistrationService(EventService eventService, EventRepository events,
            RegistrationRepository registrations, MemberRepository members,
            PaymentRepository payments, RegistrationCancellationRepository cancellations, RegistrationCancellationMail notificationMail) {
        this.notificationMail = notificationMail;
        this.eventService = eventService;
        this.events = events;
        this.registrations = registrations;
        this.members = members;
        this.payments = payments;
        this.cancellations = cancellations;
    }

    @Transactional(readOnly = true)
    public Roster list(Long operatorId, Long eventId) {
        Event event = eventService.findById(operatorId, eventId);
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Taipei"));
        List<Registration> rows = registrations.findByEventIdOrderByRegisteredAtAscIdAsc(eventId);
        Set<Long> memberIds = new HashSet<>();
        for (Registration registration : rows) {
            memberIds.add(registration.getMemberId());
        }
        Map<Long, Member> memberMap = new HashMap<>();
        for (Member member : members.findAllById(memberIds)) {
            memberMap.put(member.getId(), member);
        }
        Map<Long, Payment> latestPayments = new HashMap<>();
        for (Payment payment : payments.findForEvent(eventId)) {
            Payment previous = latestPayments.get(payment.getRegistrationId());
            if (previous == null || payment.getId() > previous.getId()) {
                latestPayments.put(payment.getRegistrationId(), payment);
            }
        }
        List<Entry> entries = new ArrayList<>();
        long registeredCount = 0;
        long heldCount = 0;
        for (Registration registration : rows) {
            Member member = memberMap.get(registration.getMemberId());
            String name = "會員資料不存在";
            String account = "—";
            if (member != null) {
                name = member.getName();
                account = member.getAccount();
            }
            String paymentStatus = null;
            Payment payment = latestPayments.get(registration.getId());
            if (payment != null) {
                paymentStatus = payment.getPaymentStatus();
            }
            if ("REGISTERED".equals(registration.getStatus())) {
                registeredCount++;
            } else if (activeHold(registration, now)) {
                heldCount++;
            }
            boolean canCancel = "OPEN".equals(event.getStatus()) && now.isBefore(event.getStartAt())
                    && ("REGISTERED".equals(registration.getStatus()) || activeHold(registration, now));
            entries.add(new Entry(registration.getId(), registration.getMemberId(), name, account,
                    registration.getRegisteredAt(), registration.getCancelledAt(), registration.getStatus(),
                    paymentStatus, registration.getPaymentExpiresAt(), canCancel));
        }
        return new Roster(event.getTitle(), event.getCapacity(), registeredCount, heldCount,
                Math.max(0, event.getCapacity() - registeredCount - heldCount), entries,
                cancellations.findByEventIdOrderByCancelledAtDescIdDesc(eventId), notificationMail.list(eventId));
    }

    // 與報名、付款通知共用活動鎖，在同一交易釋放席位、更新付款並留下歷程。
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void cancel(Long operatorId, Long eventId, Long registrationId, String reason) {
        requireAdmin(operatorId);
        if (reason == null || reason.isBlank() || reason.strip().length() > 255) {
            throw new IllegalArgumentException("請填寫取消原因，最多 255 字元。");
        }
        Optional<Event> optionalEvent = events.findByIdForUpdate(eventId);
        if (optionalEvent.isEmpty()) {
            throw new ResourceNotFoundException("event", ResponseMessages.EVENT_NOT_FOUND);
        }
        Event event = optionalEvent.get();
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Taipei"));
        if (!"OPEN".equals(event.getStatus()) || !now.isBefore(event.getStartAt())) {
            throw new IllegalArgumentException("僅能取消尚未開始且開放中的活動報名。");
        }
        Optional<Registration> optionalRegistration = registrations.findById(registrationId);
        if (optionalRegistration.isEmpty() || !eventId.equals(optionalRegistration.get().getEventId())) {
            throw new ResourceNotFoundException("registration", ResponseMessages.REGISTRATION_NOT_FOUND);
        }
        Registration registration = optionalRegistration.get();
        if (!"REGISTERED".equals(registration.getStatus()) && !activeHold(registration, now)) {
            throw new IllegalArgumentException("此報名已取消或付款保留已逾期，無需再次取消。");
        }
        Optional<Payment> payment = payments.findFirstByRegistrationIdOrderByIdDesc(registrationId);
        String paymentStatus = null;
        if (payment.isPresent()) {
            paymentStatus = payment.get().getPaymentStatus();
        }
        RegistrationCancellation saved = cancellations.save(new RegistrationCancellation(registration, operatorId, reason.strip(), paymentStatus, now));
        registration.setStatus("CANCELLED");
        registration.setCancelledAt(now);
        registration.setPaymentExpiresAt(null);
        String paymentNote = PaymentLifecycle.cancelLatest(payments, registrationId);
        notificationMail.enqueue(saved.getId(), registration.getMemberId(), event, reason.strip(), paymentNote);
        // JPA 於交易提交時寫入報名與付款；任何例外會讓整筆操作回滾。
    }

    private void requireAdmin(Long operatorId) {
        Optional<Member> optionalMember = members.findByIdAndStatus(operatorId, "ACTIVE");
        if (optionalMember.isEmpty()) {
            throw new ResourceNotFoundException("member", ResponseMessages.MEMBER_NOT_FOUND);
        }
        Member operator = optionalMember.get();
        if (!"ADMIN".equals(operator.getRole())
                || operator.isSuspendedAt(LocalDateTime.now(ZoneId.of("Asia/Taipei")))) {
            throw new IllegalArgumentException("只有有效的管理員可以操作。");
        }
    }

    private boolean activeHold(Registration registration, LocalDateTime now) {
        return "PENDING_PAYMENT".equals(registration.getStatus())
                && registration.getPaymentExpiresAt() != null && now.isBefore(registration.getPaymentExpiresAt());
    }

    public record Entry(Long id, Long memberId, String name, String account,
            LocalDateTime registeredAt, LocalDateTime cancelledAt, String status,
            String paymentStatus, LocalDateTime paymentExpiresAt, boolean canCancel) {}
    public record Roster(String title, int capacity, long registeredCount, long heldCount, long remainingSlots,
            List<Entry> entries, List<RegistrationCancellation> cancellations, List<Map<String, Object>> notifications) {}
}
