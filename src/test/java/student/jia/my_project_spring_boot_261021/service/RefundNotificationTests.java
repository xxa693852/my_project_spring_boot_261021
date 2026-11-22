package student.jia.my_project_spring_boot_261021.service;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import student.jia.my_project_spring_boot_261021.entity.*;
import student.jia.my_project_spring_boot_261021.repository.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class RefundNotificationTests {
    @Test void snapshotExcludesInternalNoteAndSendsOnlyAfterQueueIsRead() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class); MailService mail=mock(MailService.class); LineNotificationClient line=mock(LineNotificationClient.class);
        RefundNotificationService service=new RefundNotificationService(jdbc,mail,line);
        when(jdbc.queryForObject(anyString(),eq(String.class),eq(11L))).thenReturn("龍穴");
        Payment payment=new Payment(20L,"ORDER1","merchant",1000);payment.setId(1L);
        payment.setRefundedAt(LocalDateTime.of(2026,11,22,14,30,12,123456789));payment.setRefundNote("內部秘密");
        service.enqueue(payment,7L,11L);
        ArgumentCaptor<String> body=ArgumentCaptor.forClass(String.class);
        verify(jdbc,times(2)).update(anyString(),eq(1L),body.capture(),eq(7L));
        assertEquals(body.getAllValues().get(0),body.getAllValues().get(1));
        assertTrue(body.getValue().contains("2026-11-22 14:30:12（台北時間）"));
        assertTrue(body.getValue().contains("不代表款項已入帳"));assertFalse(body.getValue().contains("內部秘密"));
        verifyNoInteractions(mail,line);
    }
    @Test void channelFailureDoesNotPreventOtherChannelAndClaimPreventsDuplicateDelivery() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);MailService mail=mock(MailService.class);LineNotificationClient line=mock(LineNotificationClient.class);
        when(jdbc.queryForList(anyString())).thenReturn(List.of(Map.of("id",1L,"channel","EMAIL","recipient","a@example.com","body","退款"),Map.of("id",2L,"channel","LINE","recipient","U123","body","退款")));
        when(jdbc.update(anyString(),eq(1L))).thenReturn(1,0);when(jdbc.update(anyString(),eq(2L))).thenReturn(1,0);
        doThrow(new IllegalStateException()).when(mail).sendEmail(anyString(),anyString(),anyString());when(line.isConfigured()).thenReturn(true);
        RefundNotificationService service=new RefundNotificationService(jdbc,mail,line);service.deliverPending();service.deliverPending();
        verify(jdbc).update(anyString(),eq("UNKNOWN"),eq(1L));verify(jdbc).update(anyString(),eq("SENT"),eq(2L));
        verify(line,times(1)).push("U123","退款");verify(mail,times(1)).sendEmail(anyString(),anyString(),anyString());
    }
    @Test void recordingTwiceQueuesOnlyOnceAndPreservesTimestamp() {
        EventRepository events=mock(EventRepository.class);RegistrationRepository registrations=mock(RegistrationRepository.class);
        PaymentRepository payments=mock(PaymentRepository.class);MemberRepository members=mock(MemberRepository.class);RefundNotificationService notifications=mock(RefundNotificationService.class);
        Member admin=new Member("admin","hash","管理員","a@example.com");admin.setRole("ADMIN");
        when(members.findByIdAndStatus(1L,"ACTIVE")).thenReturn(Optional.of(admin));
        LocalDateTime start=LocalDateTime.now().plusDays(1);
        Event event=new Event("冒險","介紹","主持","台北",start,start.plusHours(2),start.minusHours(1),5,true,1L);
        when(events.findByIdForUpdate(11L)).thenReturn(Optional.of(event));
        Registration registration=new Registration(7L,11L);registration.setId(20L);
        when(registrations.findById(20L)).thenReturn(Optional.of(registration));
        Payment payment=new Payment(20L,"ORDER1","merchant",1000);payment.setId(3L);payment.setPaymentStatus("REFUND_PENDING");
        when(payments.findById(3L)).thenReturn(Optional.of(payment));
        PaymentLifecycle service=new PaymentLifecycle(events,registrations,payments,members,notifications);
        service.recordRefund(1L,11L,3L,"內部說明");LocalDateTime time=payment.getRefundedAt();service.recordRefund(1L,11L,3L,"另一說明");
        verify(notifications,times(1)).enqueue(payment,7L,11L);assertEquals(time,payment.getRefundedAt());assertEquals("內部說明",payment.getRefundNote());
    }
}
