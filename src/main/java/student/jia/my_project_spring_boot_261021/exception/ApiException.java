package student.jia.my_project_spring_boot_261021.exception;

/**
 * 帶有錯誤欄位與訊息代碼的業務例外基底。
 * 本類別只保存資料；HTTP 狀態與回應內容由子類別對應的 ExceptionHandler 決定。
 */
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