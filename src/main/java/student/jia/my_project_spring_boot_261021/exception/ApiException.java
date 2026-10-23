package student.jia.my_project_spring_boot_261021.exception;

public class ApiException extends RuntimeException {

    private final String errorKey;
    private final String messageCode;

    public ApiException(String errorKey, String messageCode) {
        super(messageCode);
        this.errorKey = errorKey;
        this.messageCode = messageCode;
    }

    public String getErrorKey() {
        return errorKey;
    }

    public String getMessageCode() {
        return messageCode;
    }
}