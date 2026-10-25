package student.jia.my_project_spring_boot_261021.request;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import student.jia.my_project_spring_boot_261021.constant.ResponseMessages;

@Getter 
@Setter 
public class TokenLogoutRequest {
    

    @NotBlank (message = ResponseMessages.REFRESH_TOKEN_REQUIRED)
    private  String refreshToken;
}
