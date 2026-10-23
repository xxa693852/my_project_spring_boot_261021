package student.jia.my_project_spring_boot_261021.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import student.jia.my_project_spring_boot_261021.constant.ResponseMessages;

@Getter
@Setter 
public class MemberRegisterRequest {
    
    @NotBlank(message = ResponseMessages.MEMBER_NAME_REQUIRED) //不能為空白
    @Size(max = 50, message = ResponseMessages.MEMBER_NAME_MAX)
    private String name;

    @NotBlank(message = ResponseMessages.MEMBER_ACCOUNT_REQUIRED)
    @Size(max = 50, message = ResponseMessages.MEMBER_ACCOUNT_MAX)
    private String account;

    @NotBlank(message = ResponseMessages.MEMBER_PASSWORD_REQUIRED)
    @Size(max = 255, min = 6, message = ResponseMessages.MEMBER_PASSWORD_SIZE) //密碼最大為6~12個字
    private String password;

    @NotBlank(message = ResponseMessages.MEMBER_CONFIRM_PASSWORD_REQUIRED)
    private String confirmPassword; //確認密碼
    //@Email email 必須符合 Email 格式
    @Email(message = ResponseMessages.MEMBER_EMAIL_INVALID) 
    @Size(max = 254, message = ResponseMessages.MEMBER_EMAIL_MAX)
    private String email;

}
