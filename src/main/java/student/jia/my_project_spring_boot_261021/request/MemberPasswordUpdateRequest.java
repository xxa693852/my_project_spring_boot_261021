package student.jia.my_project_spring_boot_261021.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import student.jia.my_project_spring_boot_261021.constant.ResponseMessages;
import lombok.Getter;
import lombok.Setter;

/**
 * 接收舊密碼、新密碼與確認密碼；Service 驗證舊密碼後才更新密碼雜湊。
 */
@Getter
@Setter
public class MemberPasswordUpdateRequest {
    @NotBlank (message = ResponseMessages.MEMBER_PASSWORD_REQUIRED)
    // 調整：新密碼統一 15～30 字元；UTF-8 的 72 bytes 限制由 Service 檢查。
    @Size(min = 15, max = 30, message = ResponseMessages.MEMBER_PASSWORD_SIZE)
    private  String password;

    @NotBlank (message = ResponseMessages.MEMBER_CONFIRM_PASSWORD_REQUIRED)
    private  String confirmPassword;

    @NotBlank(message = "舊密碼必填")
    private String oldPassword;
}
