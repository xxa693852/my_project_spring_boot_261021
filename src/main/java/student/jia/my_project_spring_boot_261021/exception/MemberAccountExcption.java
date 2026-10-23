package student.jia.my_project_spring_boot_261021.exception;

public class MemberAccountExcption extends ApiException {

    public MemberAccountExcption(String errorKey, String messageCode) {
        super(errorKey, messageCode);
    }
}