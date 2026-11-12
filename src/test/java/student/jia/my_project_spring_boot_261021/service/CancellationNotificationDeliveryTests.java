package student.jia.my_project_spring_boot_261021.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.web.client.ResourceAccessException;

import student.jia.my_project_spring_boot_261021.entity.Member;
import student.jia.my_project_spring_boot_261021.repository.MemberRepository;
import student.jia.my_project_spring_boot_261021.service.CancellationNotificationQueue.Pending;

import static org.mockito.Mockito.*;

class CancellationNotificationDeliveryTests {
    private final CancellationNotificationQueue queue = mock(CancellationNotificationQueue.class);
    private final MemberRepository members = mock(MemberRepository.class);
    private final LineNotificationClient line = mock(LineNotificationClient.class);
    private final MailService mail = mock(MailService.class);
    private final CancellationNotificationDelivery delivery = new CancellationNotificationDelivery(queue, members, line, mail);
    private final Member member = new Member("qa", "hash", "QA", "qa@example.com");

    @BeforeEach
    void prepareQueue() {
        when(queue.isEnabled()).thenReturn(true);
        when(queue.pending()).thenReturn(List.of(new Pending(1L, 2L, "QA", LocalDateTime.of(2027, 1, 1, 12, 0))));
        when(queue.claim(1L)).thenReturn(true);
        when(members.findByIdAndStatus(2L, "ACTIVE")).thenReturn(Optional.of(member));
    }

    @Test
    void linkedMemberGetsOnlyLine() {
        member.setNotificationLineUserId("line-user");
        when(line.isConfigured()).thenReturn(true);
        delivery.deliverPending();
        verify(line).push(eq("line-user"), contains("已取消"));
        verify(queue).finish(1L, "SENT", "LINE", null);
        verifyNoInteractions(mail);
    }

    @Test
    void unlinkedMemberGetsOnlyEmail() {
        delivery.deliverPending();
        verify(mail).sendEmail(eq("qa@example.com"), anyString(), contains("已取消"));
        verify(queue).finish(1L, "SENT", "EMAIL", null);
        verifyNoInteractions(line);
    }

    @Test
    void unavailableLineFallsBackToEmail() {
        member.setNotificationLineUserId("line-user");
        when(line.isConfigured()).thenReturn(true);
        doThrow(new ResourceAccessException("profile unavailable")).when(line).verifyRecipient("line-user");
        delivery.deliverPending();
        verify(line, never()).push(anyString(), anyString());
        verify(mail).sendEmail(eq("qa@example.com"), anyString(), anyString());
        verify(queue).finish(1L, "SENT", "EMAIL", "LINE_RECIPIENT_UNAVAILABLE");
    }

    @Test
    void ambiguousPushDoesNotSendASecondChannel() {
        member.setNotificationLineUserId("line-user");
        when(line.isConfigured()).thenReturn(true);
        doThrow(new ResourceAccessException("timeout")).when(line).push(eq("line-user"), anyString());
        delivery.deliverPending();
        verify(queue).finish(1L, "UNKNOWN", "LINE", "LINE_DELIVERY_UNCONFIRMED");
        verifyNoInteractions(mail);
    }

    @Test
    void smtpFailureRemainsVisibleInDeliveryLog() {
        doThrow(new MailSendException("test")).when(mail).sendEmail(anyString(), anyString(), anyString());
        delivery.deliverPending();
        verify(queue).finish(1L, "FAILED", "EMAIL", "EMAIL_SEND_ERROR");
    }
}
