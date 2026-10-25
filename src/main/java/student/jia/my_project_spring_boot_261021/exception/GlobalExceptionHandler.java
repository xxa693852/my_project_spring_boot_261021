package student.jia.my_project_spring_boot_261021.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import student.jia.my_project_spring_boot_261021.constant.ResponseMessages;
import student.jia.my_project_spring_boot_261021.response.ApiResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {

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
        exception.getBindingResult().getFieldErrors().forEach(error ->
                errors.putIfAbsent(error.getField(), resolveMessage(error.getDefaultMessage())));
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


    // Preserve literal messages when no message-code mapping exists.
    private String resolveMessage(String code) {
        if (code == null) {
            return "Invalid value";
        }
        String message = ResponseMessages.getMessage(code);
        return message != null ? message : code;
    }
}
