package student.jia.my_project_spring_boot_261021.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;
import student.jia.my_project_spring_boot_261021.constant.ResponseMessages;

/**
 * 接收註冊資料；欄位註解檢查格式，帳號／信箱重複與密碼確認由 Service 檢查。
 */
@Getter
@Setter 
public class MemberRegisterRequest {
    
    @NotBlank(message = ResponseMessages.MEMBER_NAME_REQUIRED) //不能為空白
    @Size(max = 30, message = ResponseMessages.MEMBER_NAME_MAX)
    private String name;

    @NotBlank(message = ResponseMessages.MEMBER_ACCOUNT_REQUIRED)
    // 新註冊帳號才套用此格式；登入 Request 不套用，保留舊帳號相容性。
    @Size(min = 4, max = 20, message = ResponseMessages.MEMBER_ACCOUNT_SIZE)
    @Pattern(regexp = "[A-Za-z0-9_.-]+", message = ResponseMessages.MEMBER_ACCOUNT_FORMAT)
    private String account;

    @NotBlank(message = ResponseMessages.MEMBER_PASSWORD_REQUIRED)
    // 調整：新密碼統一 15～30 字元；UTF-8 的 72 bytes 限制由 Service 檢查。
    @Size(min = 15, max = 30, message = ResponseMessages.MEMBER_PASSWORD_SIZE)
    private String password;

    @NotBlank(message = ResponseMessages.MEMBER_CONFIRM_PASSWORD_REQUIRED)
    private String confirmPassword; //確認密碼
    //@Email email 必須符合 Email 格式
    @Email(message = ResponseMessages.MEMBER_EMAIL_INVALID)
    @NotBlank (message = ResponseMessages.MAIL_ADDRESS_REQUIRED )
    // 調整確認：Email 上限與資料庫 VARCHAR(254)、前端一致。
    @Size(max = 254, message = ResponseMessages.MEMBER_EMAIL_MAX)
    private String email;

}
