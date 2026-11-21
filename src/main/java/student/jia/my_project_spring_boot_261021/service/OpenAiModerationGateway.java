package student.jia.my_project_spring_boot_261021.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** 只傳公開文字，不傳會員資料、登入 Token 或密碼。不跟隨重新導向，避免金鑰外流。 */
@Component
public class OpenAiModerationGateway implements AiModerationGateway {
    private final HttpClient client;
    private final String apiKey;
    private final String model;
    private final String[] blockedCategories;
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Autowired
    public OpenAiModerationGateway(@Value("${app.moderation.ai.api-key:}") String apiKey, @Value("${app.moderation.ai.model:omni-moderation-latest}") String model) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build(), apiKey, model);
    }
    public OpenAiModerationGateway(HttpClient client, String apiKey, String model) {
        this.client = client;
        this.apiKey = apiKey;
        this.model = model;
        // 幻想戰鬥的 violence 分類不自動封鎖；也不直接採用整體 flagged。
        this.blockedCategories = new String[] {"harassment", "harassment/threatening", "hate", "hate/threatening", "sexual/minors"};
    }
    @Override
    public Decision review(String text) throws Exception {
        String body = mapper.writeValueAsString(Map.of("model", model, "input", text));
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/moderations"))
                .timeout(Duration.ofSeconds(3)).header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IllegalStateException("HTTP_" + response.statusCode());
        var results = mapper.readTree(response.body()).path("results");
        if (!results.isArray() || results.size() != 1) throw new IllegalStateException("INVALID_RESPONSE");
        var result = results.get(0);
        if (!result.path("flagged").isBoolean()) throw new IllegalStateException("INVALID_RESPONSE");
        var categories = result.path("categories");
        boolean blocked = false;
        for (String category : blockedCategories) {
            var value = categories.path(category);
            if (!value.isBoolean()) throw new IllegalStateException("INVALID_RESPONSE");
            if (value.asBoolean()) blocked = true;
        }
        if (blocked) return Decision.BLOCK;
        return Decision.ALLOW;
    }
}
