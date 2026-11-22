package student.jia.my_project_spring_boot_261021;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import student.jia.my_project_spring_boot_261021.entity.*;
import student.jia.my_project_spring_boot_261021.repository.*;
import student.jia.my_project_spring_boot_261021.service.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminRegistrationTests {
    private final EventService eventService = mock(EventService.class);
    private final EventRepository events = mock(EventRepository.class);
    private final RegistrationRepository registrations = mock(RegistrationRepository.class);
    private final MemberRepository members = mock(MemberRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final RegistrationCancellationRepository history = mock(RegistrationCancellationRepository.class);
    private final AdminRegistrationService service = new AdminRegistrationService(
            eventService, events, registrations, members, payments, history, mock(RegistrationCancellationMail.class));
    private final LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Taipei"));
    private Event event;
    private Registration registration;

    @BeforeEach
    void setup() {
        event = new Event("Roster", "Description", "Host", "Taipei", now.plusDays(1), now.plusDays(2), now.minusHours(1), 3, true, 1L);
        when(history.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        event.setId(11L);
        event.setStatus("OPEN");
        registration = new Registration(7L, 11L);
        registration.setId(20L);
        registration.setRegisteredAt(now.minusDays(1));
        Member admin = new Member("admin", "hash", "Admin", "admin@example.com");
        admin.setRole("ADMIN");
        when(members.findByIdAndStatus(1L, "ACTIVE")).thenReturn(Optional.of(admin));
        when(events.findByIdForUpdate(11L)).thenReturn(Optional.of(event));
        when(eventService.findById(1L, 11L)).thenReturn(event);
        when(registrations.findById(20L)).thenReturn(Optional.of(registration));
    }

    @Test
    void afterDeadlineCancellationReleasesSeatAndRecordsSnapshot() {
        service.cancel(1L, 11L, 20L, "  臨時無法參加  ");
        assertEquals("CANCELLED", registration.getStatus());
        assertNotNull(registration.getCancelledAt());
        assertNull(registration.getPaymentExpiresAt());
        ArgumentCaptor<RegistrationCancellation> captor = ArgumentCaptor.forClass(RegistrationCancellation.class);
        verify(history).save(captor.capture());
        RegistrationCancellation record = captor.getValue();
        assertEquals("臨時無法參加", record.getReason());
        assertEquals(1L, record.getOperatorId());
        assertEquals(7L, record.getMemberId());
        assertEquals("REGISTERED", record.getPreviousStatus());
        LocalDateTime snapshot = record.getRegisteredAt();
        registration.setStatus("REGISTERED");
        registration.setRegisteredAt(now.plusHours(1));
        registration.setCancelledAt(null);
        assertEquals(now.minusDays(1), snapshot);
        assertNotNull(record.getCancelledAt());
        assertEquals("臨時無法參加", record.getReason());
    }

    @Test
    void paidCancellationMarksRefundPendingWithoutClaimingRefundCompleted() {
        Payment payment = new Payment(20L, "order", "merchant", 500);
        payment.setPaymentStatus("PAID");
        payment.setPaidAt(now.minusHours(2));
        when(payments.findFirstByRegistrationIdOrderByIdDesc(20L)).thenReturn(Optional.of(payment));
        service.cancel(1L, 11L, 20L, "事故");
        assertEquals("REFUND_PENDING", payment.getPaymentStatus());
        assertNull(payment.getRefundedAt());
        assertEquals(now.minusHours(2), payment.getPaidAt());
    }

    @Test
    void activePaymentHoldIsReleasedAndOrderClosed() {
        registration.setStatus("PENDING_PAYMENT");
        registration.setPaymentExpiresAt(now.plusMinutes(10));
        Payment payment = new Payment(20L, "order", "merchant", 500);
        when(payments.findFirstByRegistrationIdOrderByIdDesc(20L)).thenReturn(Optional.of(payment));
        service.cancel(1L, 11L, 20L, "無法參加");
        assertEquals("CANCELLED", registration.getStatus());
        assertEquals("CANCELLED", payment.getPaymentStatus());
        assertNull(registration.getPaymentExpiresAt());
    }

    @Test
    void repeatedCancellationDoesNotCreateAnotherHistory() {
        service.cancel(1L, 11L, 20L, "事故");
        assertThrows(IllegalArgumentException.class, () -> service.cancel(1L, 11L, 20L, "事故"));
        verify(history, times(1)).save(any());
    }

    @Test
    void startedAndClosedEventsCannotBeChanged() {
        event.setStartAt(now.minusSeconds(1));
        assertThrows(IllegalArgumentException.class, () -> service.cancel(1L, 11L, 20L, "事故"));
        event.setStartAt(now.plusDays(1));
        event.setStatus("CANCELLED");
        assertThrows(IllegalArgumentException.class, () -> service.cancel(1L, 11L, 20L, "事故"));
        verifyNoInteractions(history, payments);
        assertEquals("REGISTERED", registration.getStatus());
    }

    @Test
    void crossEventAndExpiredHoldCannotBeCancelled() {
        registration.setEventId(99L);
        assertThrows(RuntimeException.class, () -> service.cancel(1L, 11L, 20L, "事故"));
        registration.setEventId(11L);
        registration.setStatus("PENDING_PAYMENT");
        registration.setPaymentExpiresAt(now.minusMinutes(1));
        assertThrows(IllegalArgumentException.class, () -> service.cancel(1L, 11L, 20L, "事故"));
        verifyNoInteractions(history, payments);
    }

    @Test
    void invalidReasonAndNonAdminAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.cancel(1L, 11L, 20L, "  "));
        assertThrows(IllegalArgumentException.class, () -> service.cancel(1L, 11L, 20L, "x".repeat(256)));
        Member member = new Member("user", "hash", "User", "user@example.com");
        member.setRole("MEMBER");
        when(members.findByIdAndStatus(1L, "ACTIVE")).thenReturn(Optional.of(member));
        assertThrows(IllegalArgumentException.class, () -> service.cancel(1L, 11L, 20L, "事故"));
        verifyNoInteractions(history, payments);
    }

    @Test
    void rosterCountsActiveHoldsAndUsesLatestPaymentOnly() {
        Registration held = new Registration(8L, 11L);
        held.setId(21L);
        held.setStatus("PENDING_PAYMENT");
        held.setPaymentExpiresAt(now.plusMinutes(10));
        Registration expired = new Registration(9L, 11L);
        expired.setId(22L);
        expired.setStatus("PENDING_PAYMENT");
        expired.setPaymentExpiresAt(now.minusMinutes(1));
        Payment old = new Payment(20L, "old", "merchant", 500);
        old.setId(1L);
        old.setPaymentStatus("REFUNDED");
        Payment latest = new Payment(20L, "latest", "merchant", 500);
        latest.setId(2L);
        latest.setPaymentStatus("PAID");
        when(registrations.findByEventIdOrderByRegisteredAtAscIdAsc(11L)).thenReturn(List.of(registration, held, expired));
        when(payments.findForEvent(11L)).thenReturn(List.of(old, latest));
        AdminRegistrationService.Roster result = service.list(1L, 11L);
        assertEquals(1, result.registeredCount());
        assertEquals(1, result.heldCount());
        assertEquals(1, result.remainingSlots());
        assertEquals("PAID", result.entries().get(0).paymentStatus());
        assertTrue(result.entries().get(0).canCancel());
        assertTrue(result.entries().get(1).canCancel());
        assertFalse(result.entries().get(2).canCancel());
    }
}
