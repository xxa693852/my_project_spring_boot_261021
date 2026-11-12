package student.jia.my_project_spring_boot_261021.service;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class LineNotificationClient {
    private final RestClient client;
    private final String channelId;
    private final String channelSecret;
    private final String callbackUrl;
    private final String botToken;

    public LineNotificationClient(
            @Value("${LINE_LOGIN_CHANNEL_ID:}") String channelId,
            @Value("${LINE_LOGIN_CHANNEL_SECRET:}") String channelSecret,
            @Value("${LINE_NOTIFICATION_CALLBACK_URL:}") String callbackUrl,
            @Value("${LINE_CHANNEL_ACCESS_TOKEN:}") String botToken) {
        this.channelId = channelId;
        this.channelSecret = channelSecret;
        this.callbackUrl = callbackUrl;
        this.botToken = botToken;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(5000);
        client = RestClient.builder().requestFactory(factory).build();
    }

    public boolean isConfigured() {
        return !channelId.isBlank() && !channelSecret.isBlank()
                && callbackUrl.startsWith("https://") && !botToken.isBlank();
    }

    public String authorizationUrl(String state, String nonce) {
        return UriComponentsBuilder.fromUriString("https://access.line.me/oauth2/v2.1/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", channelId)
                .queryParam("redirect_uri", "{callback}")
                .queryParam("state", state)
                .queryParam("nonce", nonce)
                .queryParam("scope", "{scope}")
                .queryParam("bot_prompt", "normal")
                .encode().buildAndExpand(callbackUrl, "openid profile").toUriString();
    }

    public String verifyAndGetFriend(String code, String nonce) {
        LinkedMultiValueMap<String, String> exchange = new LinkedMultiValueMap<>();
        exchange.add("grant_type", "authorization_code");
        exchange.add("code", code);
        exchange.add("redirect_uri", callbackUrl);
        exchange.add("client_id", channelId);
        exchange.add("client_secret", channelSecret);
        Tokens tokens = client.post().uri("https://api.line.me/oauth2/v2.1/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(exchange).retrieve().body(Tokens.class);
        if (tokens == null || tokens.idToken() == null || tokens.accessToken() == null) {
            throw new IllegalArgumentException("LINE 未傳回完整的授權資料");
        }

        LinkedMultiValueMap<String, String> verification = new LinkedMultiValueMap<>();
        verification.add("id_token", tokens.idToken());
        verification.add("client_id", channelId);
        verification.add("nonce", nonce);
        Identity identity = client.post().uri("https://api.line.me/oauth2/v2.1/verify")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(verification).retrieve().body(Identity.class);
        if (identity == null || identity.sub() == null || !identity.sub().matches("U[0-9a-f]{32}")) {
            throw new IllegalArgumentException("無法確認 LINE 帳號");
        }

        Friendship friendship = client.get().uri("https://api.line.me/friendship/v1/status")
                .headers(headers -> headers.setBearerAuth(tokens.accessToken()))
                .retrieve().body(Friendship.class);
        if (friendship == null || !friendship.friendFlag()) {
            throw new IllegalArgumentException("請先加入 LINE 官方帳號好友，再重新綁定");
        }
        // 確認 Messaging API 的官方帳號也能找到相同的收件人。
        verifyRecipient(identity.sub());
        return identity.sub();
    }

    void verifyRecipient(String userId) {
        client.get().uri("https://api.line.me/v2/bot/profile/{id}", userId)
                .headers(headers -> headers.setBearerAuth(botToken))
                .retrieve().toBodilessEntity();
    }

    void push(String userId, String text) {
        client.post().uri("https://api.line.me/v2/bot/message/push")
                .headers(headers -> headers.setBearerAuth(botToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("to", userId, "messages", List.of(Map.of("type", "text", "text", text))))
                .retrieve().toBodilessEntity();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Tokens(@JsonProperty("id_token") String idToken,
            @JsonProperty("access_token") String accessToken) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Identity(String sub) {}
    private record Friendship(boolean friendFlag) {}
}
