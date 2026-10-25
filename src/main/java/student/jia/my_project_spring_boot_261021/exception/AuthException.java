package student.jia.my_project_spring_boot_261021.exception;

public class AuthException extends ApiException {

    public AuthException(String errorKey, String messageCode) {
        super(errorKey, messageCode);
    }
}