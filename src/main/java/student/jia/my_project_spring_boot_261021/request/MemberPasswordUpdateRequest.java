package student.jia.my_project_spring_boot_261021.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import student.jia.my_project_spring_boot_261021.constant.ResponseMessages;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MemberPasswordUpdateRequest {
    @NotBlank (message = ResponseMessages.MEMBER_PASSWORD_REQUIRED)
    @Size(max =255, min = 6 , message = ResponseMessages.MEMBER_PASSWORD_SIZE)
    private  String password;

    @NotBlank (message = ResponseMessages.MEMBER_CONFIRM_PASSWORD_REQUIRED)
    private  String confirmPassword;

    @NotBlank(message = "舊密碼必填")
    private String oldPassword;
}
