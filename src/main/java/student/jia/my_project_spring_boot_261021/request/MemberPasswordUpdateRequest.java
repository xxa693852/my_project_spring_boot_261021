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
    // 調整：新密碼統一 15～72 字元；UTF-8 的 72 bytes 限制由 Service 檢查。
    @Size(min = 15, max = 72, message = ResponseMessages.MEMBER_PASSWORD_SIZE)
    private  String password;

    @NotBlank (message = ResponseMessages.MEMBER_CONFIRM_PASSWORD_REQUIRED)
    private  String confirmPassword;

    @NotBlank(message = "舊密碼必填")
    private String oldPassword;
}
