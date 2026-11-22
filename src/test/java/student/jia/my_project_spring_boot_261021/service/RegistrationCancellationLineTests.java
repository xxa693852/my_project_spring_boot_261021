package student.jia.my_project_spring_boot_261021.service;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.http.HttpStatus;
import static org.mockito.Mockito.*;

class RegistrationCancellationLineTests {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final LineNotificationClient line = mock(LineNotificationClient.class);
    private final RegistrationCancellationLine service = new RegistrationCancellationLine(jdbc, line);

    @BeforeEach
    void setup() {
        when(jdbc.queryForList(anyString())).thenReturn(List.of(Map.of("id", 1L, "recipient", "U123", "body", "取消原因：事故")));
        when(jdbc.update(anyString(), eq(1L))).thenReturn(1);
        when(line.isConfigured()).thenReturn(true);
    }

    @Test
    void boundMemberGetsLineMessageAndSeparateSentStatus() {
        service.deliverPending();
        verify(line).verifyRecipient("U123");
        verify(line).push("U123", "取消原因：事故");
        verify(jdbc).update(anyString(), eq("SENT"), eq(1L));
    }

    @Test
    void alreadyClaimedDoesNotSendTwice() {
        when(jdbc.update(anyString(), eq(1L))).thenReturn(0);
        service.deliverPending();
        verifyNoInteractions(line);
    }

    @Test
    void unboundMemberDoesNotPush() {
        when(jdbc.queryForList(anyString())).thenReturn(List.of(Map.of("id", 1L, "recipient", "", "body", "通知")));
        service.deliverPending();
        verifyNoInteractions(line);
        verify(jdbc).update(anyString(), eq("NOT_BOUND"), eq(1L));
    }

    @Test
    void disabledConfigurationDoesNotPush() {
        when(line.isConfigured()).thenReturn(false);
        service.deliverPending();
        verify(line, never()).push(anyString(), anyString());
        verify(jdbc).update(anyString(), eq("NOT_CONFIGURED"), eq(1L));
    }

    @Test
    void recipientFailureDoesNotPush() {
        doThrow(new RestClientException("unavailable")).when(line).verifyRecipient("U123");
        service.deliverPending();
        verify(line, never()).push(anyString(), anyString());
        verify(jdbc).update(anyString(), eq("RECIPIENT_ERROR"), eq(1L));
    }

    @Test
    void timeoutIsUnknownAndDoesNotRetry() {
        doThrow(new RestClientException("timeout")).when(line).push("U123", "取消原因：事故");
        service.deliverPending();
        verify(line, times(1)).push(anyString(), anyString());
        verify(jdbc).update(anyString(), eq("UNKNOWN"), eq(1L));
    }

    @Test
    void rejectionIsRecordedSeparately() {
        doThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST)).when(line).push("U123", "取消原因：事故");
        service.deliverPending();
        verify(jdbc).update(anyString(), eq("REJECTED"), eq(1L));
    }
}
