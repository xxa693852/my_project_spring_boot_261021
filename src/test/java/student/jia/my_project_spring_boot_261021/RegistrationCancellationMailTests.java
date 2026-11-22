package student.jia.my_project_spring_boot_261021;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import student.jia.my_project_spring_boot_261021.service.MailService;
import student.jia.my_project_spring_boot_261021.service.RegistrationCancellationMail;
import static org.mockito.Mockito.*;

class RegistrationCancellationMailTests {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final MailService mail = mock(MailService.class);
    private final student.jia.my_project_spring_boot_261021.service.RegistrationCancellationLine lineQueue = mock(student.jia.my_project_spring_boot_261021.service.RegistrationCancellationLine.class);
    private final RegistrationCancellationMail service = new RegistrationCancellationMail(jdbc, mail, lineQueue);

    @Test
    void enqueueUsesSameSnapshotForEmailAndLineWithoutSendingBeforeCommit() {
        var start = java.time.LocalDateTime.of(2026, 12, 1, 14, 0, 45, 123456789);
        var event = new student.jia.my_project_spring_boot_261021.entity.Event("冒險團", "介紹", "主持", "台北", start, start.plusHours(3), start.minusDays(1), 5, true, 1L);
        service.enqueue(3L, 7L, event, "臨時事故", "已付款，將全額退款。");
        var body = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(anyString(), eq(3L), eq(7L), body.capture());
        org.junit.jupiter.api.Assertions.assertTrue(body.getValue().contains("臨時事故"));
        org.junit.jupiter.api.Assertions.assertTrue(body.getValue().contains("全額退款"));
        org.junit.jupiter.api.Assertions.assertTrue(body.getValue().contains("\n原訂開始時間：2026-12-01 14:00（台北時間）\n"));
        org.junit.jupiter.api.Assertions.assertTrue(body.getValue().matches(
                "(?s).*\\n取消時間：[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}（台北時間）\\n取消原因：.*"));
        verify(lineQueue).enqueue(3L, 7L, body.getValue());
        verifyNoInteractions(mail);
    }

    @Test
    void onlyClaimedPendingNotificationIsSent() {
        when(jdbc.queryForList(anyString())).thenReturn(List.of(Map.of("id", 1L, "recipient", "player@example.com", "body", "取消原因：事故")));
        when(jdbc.update(anyString(), eq(1L))).thenReturn(1);
        service.deliverPending();
        verify(mail).sendEmail("player@example.com", "D&D 個別報名取消通知", "取消原因：事故");
        verify(jdbc).update(anyString(), eq("SENT"), eq(1L));
    }

    @Test
    void anotherWorkerClaimPreventsDuplicateSend() {
        when(jdbc.queryForList(anyString())).thenReturn(List.of(Map.of("id", 1L, "recipient", "player@example.com", "body", "通知")));
        service.deliverPending();
        verifyNoInteractions(mail);
    }

    @Test
    void mailFailureIsRecordedWithoutRetryingOrChangingRegistration() {
        when(jdbc.queryForList(anyString())).thenReturn(List.of(Map.of("id", 1L, "recipient", "player@example.com", "body", "通知")));
        when(jdbc.update(anyString(), eq(1L))).thenReturn(1);
        doThrow(new IllegalStateException("SMTP error")).when(mail).sendEmail(anyString(), anyString(), anyString());
        service.deliverPending();
        verify(jdbc).update(anyString(), eq("UNKNOWN"), eq(1L));
        verify(mail, times(1)).sendEmail(anyString(), anyString(), anyString());
    }
}
