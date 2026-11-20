package student.jia.my_project_spring_boot_261021.exception;

// 查不到指定資料時使用的通用例外。
/**
 * 找不到指定會員、活動或報名資料時使用；handleResourceNotFound 回傳 HTTP 404。
 */
public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String errorKey, String messageCode) {
        super(errorKey, messageCode);
    }

}