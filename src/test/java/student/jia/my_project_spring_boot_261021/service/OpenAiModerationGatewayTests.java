package student.jia.my_project_spring_boot_261021.service;

import java.net.http.*;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OpenAiModerationGatewayTests {
    private String response(boolean harassment,boolean violence) {
        return "{\"results\":[{\"flagged\":true,\"categories\":{\"harassment\":"+harassment+",\"harassment/threatening\":false,\"hate\":false,\"hate/threatening\":false,\"sexual/minors\":false,\"violence\":"+violence+"}}]}";
    }
    @SuppressWarnings("unchecked")
    private OpenAiModerationGateway gateway(int status,String body,HttpClient client) throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        doReturn(response).when(client).send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
        return new OpenAiModerationGateway(client,"test-key","test-model");
    }
    @Test void onlyConfiguredCategoriesCauseRejectionAndRequestHasTimeout() throws Exception {
        HttpClient client = mock(HttpClient.class);
        var gateway = gateway(200,response(false,true),client);
        assertEquals(AiModerationGateway.Decision.ALLOW,gateway.review("殺死哥布林"));
        ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(request.capture(),any(HttpResponse.BodyHandler.class));
        assertEquals(Duration.ofSeconds(3),request.getValue().timeout().get());
        assertEquals("https://api.openai.com/v1/moderations",request.getValue().uri().toString());
        gateway = gateway(200,response(true,false),mock(HttpClient.class));
        assertEquals(AiModerationGateway.Decision.BLOCK,gateway.review("待審文字"));
    }
    @Test void removedModelAuthenticationRateLimitAndServerErrorsAreUnavailable() throws Exception {
        for(int status:new int[]{401,403,404,429,500,503}) {
            var gateway = gateway(status,"{}",mock(HttpClient.class));
            assertThrows(IllegalStateException.class, () -> gateway.review("文字"));
        }
    }
    @Test void malformedOrChangedResponseIsNotAValidDecision() throws Exception {
        for(String body:new String[]{"not json","{}","{\"results\":[]}","{\"results\":[{\"flagged\":true,\"categories\":{}}]}","{\"results\":[{\"flagged\":false,\"categories\":{\"harassment\":\"false\"}}]}"}) {
            var gateway = gateway(200,body,mock(HttpClient.class));
            assertThrows(Exception.class, () -> gateway.review("文字"));
        }
    }
}
