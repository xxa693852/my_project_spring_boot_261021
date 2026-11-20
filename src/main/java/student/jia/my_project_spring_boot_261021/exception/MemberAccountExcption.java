package student.jia.my_project_spring_boot_261021.exception;

/**
 * 會員欄位或帳密驗證失敗時使用；handleMemberAccount 回傳 HTTP 400 與欄位錯誤。
 */
public class MemberAccountExcption extends ApiException {

    public MemberAccountExcption(String errorKey, String messageCode) {
        super(errorKey, messageCode);
    }
}