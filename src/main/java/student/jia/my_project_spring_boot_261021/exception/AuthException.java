package student.jia.my_project_spring_boot_261021.exception;

/**
 * 登入憑證驗證失敗時使用；GlobalExceptionHandler.handleAuth 會回傳 HTTP 401。
 */
public class AuthException extends ApiException {

    public AuthException(String errorKey, String messageCode) {
        super(errorKey, messageCode);
    }
}