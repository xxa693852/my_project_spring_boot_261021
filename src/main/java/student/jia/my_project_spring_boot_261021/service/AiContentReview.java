package student.jia.my_project_spring_boot_261021.service;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 詞庫通過後才呼叫 AI。錯誤降級放行，但明確拒絕永遠不可當故障放行。 */
@Service
public class AiContentReview {
    private static final Logger LOG = LoggerFactory.getLogger(AiContentReview.class);
    private final AiModerationGateway gateway;
    private final boolean enabled;
    private final boolean configured;
    private final Clock clock;
    private volatile Health health;
    public record Health(String state, String message, long retryAt) {}

    @Autowired
    public AiContentReview(AiModerationGateway gateway, @Value("${app.moderation.ai.enabled:false}") boolean enabled, @Value("${app.moderation.ai.api-key:}") String key) {
        this(gateway, enabled, key, Clock.systemUTC());
    }
    public AiContentReview(AiModerationGateway gateway, boolean enabled, String key, Clock clock) {
        this.gateway = gateway;
        this.enabled = enabled;
        this.configured = key != null && !key.isBlank();
        this.clock = clock;
        this.health = new Health("WAITING", "AI 審核已設定，等待首次審核。", 0);
    }
    public Health status() {
        if (!enabled) return new Health("DISABLED", "AI 審核已關閉，目前僅使用詞庫檢查。", 0);
        if (!configured) return new Health("NOT_CONFIGURED", "AI 金鑰尚未設定，目前僅使用詞庫檢查。", 0);
        return health;
    }
    public void validate(String text) {
        if (!enabled || !configured || text.isBlank() || clock.millis() < health.retryAt()) return;
        AiModerationGateway.Decision decision;
        try {
            decision = gateway.review(text);
            if (decision == null) throw new IllegalStateException("INVALID_RESPONSE");
        } catch (Exception failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            health = new Health("FALLBACK", "AI 暫時無法使用，目前僅使用詞庫檢查；稍後自動重試。", clock.millis() + 60000);
            // 僅記錄錯誤類型，不記錄送審原文、API 金鑰或第三方回應內容。
            String reason = failure.getClass().getSimpleName();
            if (failure instanceof IllegalStateException && failure.getMessage() != null
                    && failure.getMessage().matches("HTTP_[0-9]{3}|INVALID_RESPONSE")) reason = failure.getMessage();
            LOG.warn("AI moderation unavailable; dictionary fallback for 60 seconds ({})", reason);
            return;
        }
        health = new Health("ACTIVE", "詞庫與 AI 審核正常啟用。", 0);
        if (decision == AiModerationGateway.Decision.BLOCK) throw new IllegalArgumentException(PublicContentPolicy.REJECTION);
    }
}
