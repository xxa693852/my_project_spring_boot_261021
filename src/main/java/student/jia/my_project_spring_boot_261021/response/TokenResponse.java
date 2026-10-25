package student.jia.my_project_spring_boot_261021.response;

import  java.time.LocalDateTime;

import lombok.Getter;

@Getter 
public class TokenResponse {

    private String tokenType = "Bearer";
    private String accessToken;
    private String refreshToken;
    private LocalDateTime accessExpiresAt;
    private LocalDateTime refreshExpiresAt;

    public  TokenResponse(
        String accessToken,
        String refreshToken,
        LocalDateTime accessExpiresAt,
        LocalDateTime refreshExpiresAt){
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.accessExpiresAt = accessExpiresAt;
            this.refreshExpiresAt = refreshExpiresAt;
        }


}

