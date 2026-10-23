package student.jia.my_project_spring_boot_261021.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import student.jia.my_project_spring_boot_261021.constant.ResponseMessages;
import student.jia.my_project_spring_boot_261021.response.ApiResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 處理「找不到資料」
    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse handleResourceNotFound(
            ResourceNotFoundException exception) {

        String message =
                ResponseMessages.getMessage(exception.getMessage());

        return new ApiResponse(message);
    }
}