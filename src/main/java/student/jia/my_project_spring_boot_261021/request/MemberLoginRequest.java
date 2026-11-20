package student.jia.my_project_spring_boot_261021.request;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import student.jia.my_project_spring_boot_261021.constant.ResponseMessages;


/**
 * 接收登入帳號與密碼；只代表輸入資料，實際比對與 Token 簽發由 MemberService 處理。
 */
@Getter
@Setter
public class MemberLoginRequest {

    @NotBlank(message = ResponseMessages.MEMBER_ACCOUNT_REQUIRED)
    private String account;

    @NotBlank(message = ResponseMessages.MEMBER_PASSWORD_REQUIRED)
    private String password;


}
