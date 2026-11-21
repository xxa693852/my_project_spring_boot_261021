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

/**
 * 集中處理 Controller 與 Service 向外拋出的指定例外。
 * @ExceptionHandler 決定接住哪一類錯誤；@ResponseStatus 決定 HTTP 狀態碼。
 * 方法回傳的物件會轉成 JSON；這裡沒有涵蓋所有未預期例外。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 接住報名時段衝突例外，回傳提示訊息與衝突活動清單。
    @ExceptionHandler(RegistrationConflictException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public RegistrationConflictResponse handleRegistrationConflict(RegistrationConflictException exception) {
        return new RegistrationConflictResponse(exception.getMessage(), exception.getConflicts());
    }

    public record RegistrationConflictResponse(String message, List<RegistrationConflictException.Conflict> conflicts) {}

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public org.springframework.http.ResponseEntity<ApiResponse> handleStatus(org.springframework.web.server.ResponseStatusException exception) {
        return org.springframework.http.ResponseEntity.status(exception.getStatusCode())
                .body(new ApiResponse(exception.getReason() == null ? "操作失敗，請稍後再試。" : exception.getReason()));
    }

    // 找不到會員或其他指定資料，回傳 HTTP 404。
    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse handleResourceNotFound(ResourceNotFoundException exception) {
        return new ApiResponse(resolveMessage(exception.getMessageCode()));
    }

    // 帳號／信箱重複、姓名空白或密碼不符等會員驗證錯誤，回傳 HTTP 400 與欄位訊息。
    @ExceptionHandler(MemberAccountExcption.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse handleMemberAccount(MemberAccountExcption exception) {
        String message = resolveMessage(exception.getMessageCode());
        return new ApiResponse(message, Map.of(exception.getErrorKey(), message));
    }

    // Request 的 @Valid 欄位驗證失敗時，整理各欄位錯誤並回傳 HTTP 400。
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
    // 登入憑證缺少、無效或過期等 AuthException，回傳 HTTP 401 與錯誤欄位。
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


    // 將錯誤代碼轉成 ResponseMessages 的中文訊息；沒有對應時保留原文字。
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

    // JSON 格式或欄位型別無法轉成 Request 時進入這裡，回傳格式提示與 HTTP 400。
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse handleMessageNotReadable(
            HttpMessageNotReadableException exception) {

        return new ApiResponse(
                "請確認 JSON 語法與欄位型別；日期時間請使用 yyyy-MM-dd'T'HH:mm:ss 格式");
}
}
