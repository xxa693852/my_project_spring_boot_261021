package student.jia.my_project_spring_boot_261021.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import student.jia.my_project_spring_boot_261021.constant.ResponseMessages;

@Getter
@Setter 
public class MemberUpdateRequest {
    
    @NotBlank(message = ResponseMessages.MEMBER_NAME_REQUIRED) //不能為空白
    @Size(max = 50, message = ResponseMessages.MEMBER_NAME_MAX)
    private String name;

    //@Email email 必須符合 Email 格式
    @Email(message = ResponseMessages.MEMBER_EMAIL_INVALID)
    @NotBlank (message = ResponseMessages.MAIL_ADDRESS_REQUIRED )
    @Size(max = 254, message = ResponseMessages.MEMBER_EMAIL_MAX)
    private String email;


}
