package student.jia.my_project_spring_boot_261021.response;


import  java.time.LocalDateTime;
import  student.jia.my_project_spring_boot_261021.entity.Member;
import lombok.Getter;

/**
 * 將 Member 轉為對外會員資料，避免直接將含有密碼雜湊的 Entity 回傳。
 */
@Getter 
public class MemberResponse {
    
    private String role;
    private  Long id;
    private  String name;
    private  String account;
    private  String email;

    public MemberResponse(Member member){
        this.role = member.getRole();
        this.id  =  member.getId();
        this.name =  member.getName();
        this.account = member.getAccount();
        this.email = member.getEmail();



    }

}
