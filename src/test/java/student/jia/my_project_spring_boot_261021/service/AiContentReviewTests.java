package student.jia.my_project_spring_boot_261021.service;

import java.io.IOException;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiContentReviewTests {
    @Test void disabledOrMissingKeyNeverCallsProvider() throws Exception {
        AiModerationGateway gateway = mock(AiModerationGateway.class);
        AiContentReview disabled = new AiContentReview(gateway, false, "test-key", Clock.systemUTC());
        disabled.validate("正常留言");
        assertEquals("DISABLED", disabled.status().state());
        AiContentReview missing = new AiContentReview(gateway, true, "", Clock.systemUTC());
        missing.validate("正常留言");
        assertEquals("NOT_CONFIGURED", missing.status().state());
        verifyNoInteractions(gateway);
    }
    @Test void explicitBlockIsNeverTreatedAsProviderFailure() throws Exception {
        AiModerationGateway gateway = mock(AiModerationGateway.class);
        when(gateway.review(anyString())).thenReturn(AiModerationGateway.Decision.BLOCK);
        AiContentReview review = new AiContentReview(gateway, true, "test-key", Clock.systemUTC());
        for (int i=0;i<2;i++) {
            assertEquals(PublicContentPolicy.REJECTION, assertThrows(IllegalArgumentException.class, () -> review.validate("待審內容")).getMessage());
            assertEquals("ACTIVE", review.status().state());
        }
        verify(gateway,times(2)).review(anyString());
    }
    @Test void outageFallsBackThenRecoversAfterCooldown() throws Exception {
        AiModerationGateway gateway = mock(AiModerationGateway.class);
        Clock clock = mock(Clock.class);
        when(clock.millis()).thenReturn(1000L);
        when(gateway.review(anyString())).thenThrow(new IOException("not exposed")).thenReturn(AiModerationGateway.Decision.ALLOW);
        AiContentReview review = new AiContentReview(gateway, true, "test-key", clock);
        assertDoesNotThrow(() -> review.validate("正常內容"));
        assertEquals("FALLBACK", review.status().state());
        when(clock.millis()).thenReturn(60999L);
        review.validate("另一則內容");
        verify(gateway,times(1)).review(anyString());
        when(clock.millis()).thenReturn(61000L);
        review.validate("恢復正常內容");
        assertEquals("ACTIVE", review.status().state());
        verify(gateway,times(2)).review(anyString());
    }
    @Test void dictionaryRemainsMandatoryEvenWhenAiIsUnavailable() throws Exception {
        AiModerationGateway gateway = mock(AiModerationGateway.class);
        when(gateway.review(anyString())).thenThrow(new IllegalStateException("HTTP_404"));
        AiContentReview review = new AiContentReview(gateway,true,"test-key",Clock.systemUTC());
        PublicContentPolicy policy = new PublicContentPolicy("操你媽",review);
        assertDoesNotThrow(() -> policy.validate("正常屠龍介紹"));
        assertEquals("FALLBACK",review.status().state());
        assertThrows(IllegalArgumentException.class, () -> policy.validate("操你媽"));
        verify(gateway,times(1)).review(anyString());
    }
    @Test void dictionaryBlockDoesNotSendAnyContentToAi() {
        AiModerationGateway gateway = mock(AiModerationGateway.class);
        PublicContentPolicy policy = new PublicContentPolicy("操你媽",new AiContentReview(gateway,true,"test-key",Clock.systemUTC()));
        assertThrows(IllegalArgumentException.class, () -> policy.validate("正常活動", "操你媽"));
        verifyNoInteractions(gateway);
    }
    @Test void activityFieldsAreReviewedInOneRequest() throws Exception {
        AiModerationGateway gateway = mock(AiModerationGateway.class);
        when(gateway.review(anyString())).thenReturn(AiModerationGateway.Decision.ALLOW);
        PublicContentPolicy policy = new PublicContentPolicy("操你媽",new AiContentReview(gateway,true,"test-key",Clock.systemUTC()));
        policy.validate("標題","介紹","地點","標籤");
        verify(gateway,times(1)).review("標題\n介紹\n地點\n標籤\n");
    }
    @Test void nullProviderDecisionFallsBackRatherThanCrashing() {
        AiContentReview review = new AiContentReview(mock(AiModerationGateway.class),true,"test-key",Clock.systemUTC());
        assertDoesNotThrow(() -> review.validate("正常文字"));
        assertEquals("FALLBACK",review.status().state());
    }    @Test void timeoutRemovedModelAndInvalidResponseAllFallback() throws Exception {
        for (Exception failure : new Exception[] {new java.net.http.HttpTimeoutException("timeout"), new IllegalStateException("HTTP_404"), new IllegalStateException("INVALID_RESPONSE")}) {
            AiModerationGateway gateway = mock(AiModerationGateway.class);
            when(gateway.review(anyString())).thenThrow(failure);
            AiContentReview review = new AiContentReview(gateway,true,"test-key",Clock.systemUTC());
            assertDoesNotThrow(() -> review.validate("正常留言"));
            assertEquals("FALLBACK",review.status().state());
        }
    }

}
