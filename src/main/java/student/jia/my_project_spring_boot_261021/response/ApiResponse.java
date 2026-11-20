package student.jia.my_project_spring_boot_261021.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

// 統一 API 回應格式：message 放大項目訊息，errors 放欄位錯誤訊息。
/**
 * 提供成功或錯誤訊息的回應物件；errors 可附上欄位名稱與錯誤訊息。
 * 它不決定 HTTP 狀態碼，狀態由 Controller 或 ExceptionHandler 指定。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse {

    private String message;

    private Map<String, String> errors;

    public ApiResponse(String message) {
        this.message = message;
    }

    public ApiResponse(String message, Map<String, String> errors) {
        this.message = message;
        this.errors = errors;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, String> getErrors() {
        return errors;
    }

}