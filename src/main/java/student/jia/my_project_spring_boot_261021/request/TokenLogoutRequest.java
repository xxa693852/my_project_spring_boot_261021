package student.jia.my_project_spring_boot_261021.request;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import student.jia.my_project_spring_boot_261021.constant.ResponseMessages;

/**
 * 接收登出使用的 Refresh Token；Service 以其雜湊找出並撤銷對應登入紀錄。
 */
@Getter 
@Setter 
public class TokenLogoutRequest {
    

    @NotBlank (message = ResponseMessages.REFRESH_TOKEN_REQUIRED)
    private  String refreshToken;
}
