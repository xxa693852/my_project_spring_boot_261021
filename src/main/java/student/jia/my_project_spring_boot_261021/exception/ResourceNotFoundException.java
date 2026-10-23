package student.jia.my_project_spring_boot_261021.exception;

// 查不到指定資料時使用的通用例外。
public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String errorKey, String messageCode) {
        super(errorKey, messageCode);
    }

}