package student.jia.my_project_spring_boot_261021.exception;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import org.springframework.http.converter.HttpMessageNotReadableException;

import student.jia.my_project_spring_boot_261021.constant.ResponseMessages;
import student.jia.my_project_spring_boot_261021.response.ApiResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RegistrationConflictException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public RegistrationConflictResponse handleRegistrationConflict(RegistrationConflictException exception) {
        return new RegistrationConflictResponse(exception.getMessage(), exception.getConflicts());
    }

    public record RegistrationConflictResponse(String message, List<RegistrationConflictException.Conflict> conflicts) {}

    // Missing member or other resource.
    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse handleResourceNotFound(ResourceNotFoundException exception) {
        return new ApiResponse(resolveMessage(exception.getMessageCode()));
    }

    // Duplicate account/email, blank name, or mismatched passwords.
    @ExceptionHandler(MemberAccountExcption.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse handleMemberAccount(MemberAccountExcption exception) {
        String message = resolveMessage(exception.getMessageCode());
        return new ApiResponse(message, Map.of(exception.getErrorKey(), message));
    }

    // Validation annotations on @Valid request objects.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        // @Valid 驗證失敗會進入此方法；先取出所有欄位錯誤，再逐筆整理成回應資料。
        List<FieldError> fieldErrors = exception.getBindingResult().getFieldErrors();
        for (FieldError error : fieldErrors) {
            String field = error.getField();
            String message = resolveMessage(error.getDefaultMessage());
            // 同一欄位可能有多個錯誤，維持原本只保留第一筆訊息的規則。
            if (!errors.containsKey(field)) {
                errors.put(field, message);
            }
        }
        return new ApiResponse(resolveMessage(ResponseMessages.VALIDATION_FAILED), errors);
    }
    // 處理Auth錯誤。
    @ExceptionHandler(AuthException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse handleAuth(AuthException exception) {
    String message = resolveMessage(exception.getMessageCode());

    return new ApiResponse(
        message,
        Map.of(exception.getErrorKey(), message)
    );
    }
    
    // 處理報名條件不符等參數錯誤
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse handleIllegalArgument(
            IllegalArgumentException exception) {

        String message = resolveMessage(exception.getMessage());

        return new ApiResponse(message);
    }


    // Preserve literal messages when no message-code mapping exists.
    private String resolveMessage(String code) {
        if (code == null) {
            return "Invalid value";
        }
        String message = ResponseMessages.getMessage(code);
        // 找到錯誤代碼對應的中文訊息就回傳；沒有對應時保留原始訊息。
        if (message != null) {
            return message;
        }
        return code;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse handleMessageNotReadable(
            HttpMessageNotReadableException exception) {

        return new ApiResponse(
                "請確認 JSON 語法與欄位型別；日期時間請使用 yyyy-MM-dd'T'HH:mm:ss 格式");
}
}
