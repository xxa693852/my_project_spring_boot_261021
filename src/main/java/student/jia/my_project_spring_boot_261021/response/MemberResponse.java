package student.jia.my_project_spring_boot_261021.response;


import  java.time.LocalDateTime;
import  student.jia.my_project_spring_boot_261021.entity.Member;
import lombok.Getter;

@Getter 
public class MemberResponse {
    
    private  Long id;
    private  String name;
    private  String account;
    private  String email;

    public MemberResponse(Member member){
        this.id  =  member.getId();
        this.name =  member.getName();
        this.account = member.getAccount();
        this.email = member.getEmail();



    }

}
