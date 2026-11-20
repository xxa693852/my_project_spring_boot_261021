package student.jia.my_project_spring_boot_261021.response;

import  java.time.LocalDateTime;

import lombok.Getter;

/**
 * 回傳登入或換發後的 Access Token、Refresh Token 與各自期限，供前端後續驗證使用。
 */
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

