package student.jia.my_project_spring_boot_261021.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import student.jia.my_project_spring_boot_261021.constant.ResponseMessages;
import student.jia.my_project_spring_boot_261021.entity.Member;
import student.jia.my_project_spring_boot_261021.exception.MemberAccountExcption;
import student.jia.my_project_spring_boot_261021.repository.MemberRepository;
import student.jia.my_project_spring_boot_261021.request.MemberRegisterRequest;
@Service
public class MemberService {
    
private final MemberRepository memberRepository;
private final PasswordEncoder passwordEncoder;

//建構子注入
public MemberService(MemberRepository memberRepository,PasswordEncoder passwordEncoder){
    this.memberRepository = memberRepository;
    this.passwordEncoder = passwordEncoder;
}
//新增會員
@Transactional 
public  void register(MemberRegisterRequest request){
     // 1. 檢查兩次密碼是否相同
     if(!request.getPassword().equals(request.getConfirmPassword())){
        throw new MemberAccountExcption("confirmPassword", ResponseMessages.MEMBER_CONFIRM_PASSWORD_NOT_MATCH);
     }
    // 2. 檢查帳號是否已被使用
     if (memberRepository.existsByAccount(request.getAccount())) {
        throw  new MemberAccountExcption("account", ResponseMessages.MEMBER_ACCOUNT_EXISTS);
        }
        // 3. 檢查 Email 是否已被使用
    if(memberRepository.existsByEmail(request.getEmail())){
        throw new MemberAccountExcption("email", ResponseMessages.MEMBER_EMAIL_EXISTS);
    }

        // 4. 將使用者輸入的密碼轉成雜湊值
       String encodedPassword = passwordEncoder.encode(request.getPassword());


        // 5. 建立會員物件，目前還沒有存入 DB
    Member member = new Member(
        request.getAccount(),
        request.getPassword(), 
        request.getName(), 
        request.getEmail()
    );

         // 6. 儲存到 DB
        memberRepository.save(member);
}




// 1. 檢查帳號是否重複

// 2. 檢查 Email 是否重複

// 3. 將密碼轉成雜湊值

// 4. 使用 Member 建構子建立會員

// 5. 儲存會員

// 6. 轉成 MemberResponse 並回傳

}
