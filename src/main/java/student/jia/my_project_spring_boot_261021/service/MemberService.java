package student.jia.my_project_spring_boot_261021.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import student.jia.my_project_spring_boot_261021.constant.ResponseMessages;
import student.jia.my_project_spring_boot_261021.entity.Member;
import student.jia.my_project_spring_boot_261021.entity.AuthToken;
import student.jia.my_project_spring_boot_261021.exception.MemberAccountExcption;
import student.jia.my_project_spring_boot_261021.exception.ResourceNotFoundException;
import student.jia.my_project_spring_boot_261021.repository.AuthTokenRepository;
import student.jia.my_project_spring_boot_261021.repository.MemberRepository;
import student.jia.my_project_spring_boot_261021.request.MemberLoginRequest;
import student.jia.my_project_spring_boot_261021.request.MemberPasswordUpdateRequest;
import student.jia.my_project_spring_boot_261021.request.MemberRegisterRequest;
import student.jia.my_project_spring_boot_261021.request.MemberUpdateRequest;
import student.jia.my_project_spring_boot_261021.response.TokenResponse;
import student.jia.my_project_spring_boot_261021.constant.AuthOwnerTypes;
import student.jia.my_project_spring_boot_261021.exception.AuthException;


@Service
public class MemberService {
    
private final MemberRepository memberRepository;
private final PasswordEncoder passwordEncoder;
private JwtService jwtService;
private AuthTokenRepository authTokenRepository;
private Byte TOKEN_REVOKED = 1;

//建構子注入
public MemberService(MemberRepository memberRepository,
    PasswordEncoder passwordEncoder,
    JwtService jwtService,
    AuthTokenRepository authTokenRepository
){
    this.memberRepository = memberRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.authTokenRepository = authTokenRepository;
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
        encodedPassword, 
        request.getName(), 
        request.getEmail()
    );

         // 6. 儲存到 DB
        memberRepository.save(member);
}
    //單一會員id查詢
    public Member findOneById(Long id){

        Optional<Member> member = 
                memberRepository.findByIdAndStatus(id, "ACTIVE");
        
        if(member.isEmpty()){
            throw new ResourceNotFoundException(
            "member",
        ResponseMessages.MEMBER_NOT_FOUND
        );
    }
    
    return member.get();
    }
    //修改會員資料
    @Transactional
    public void updateProfile(Long id,MemberUpdateRequest request){
       
        // 1. 給repository找
        Optional<Member> member =  memberRepository.findByIdAndStatus(id, "ACTIVE");
    // 2. 如果找不到
        if(member.isEmpty()){
            throw new ResourceNotFoundException("member", ResponseMessages.MEMBER_NOT_FOUND);
        }

         String email = request.getEmail().trim();
    // 3. 檢查其他會員是否使用這個 Email，排除自己
        if(memberRepository.existsByEmailAndIdNot(email, id)){
            throw new MemberAccountExcption("email",ResponseMessages.MEMBER_EMAIL_EXISTS);
            
        }
            Member targetMember = member.get();

            targetMember.setEmail(email);
            targetMember.setName(request.getName().trim());
        }
/*@Transactional 簡練的寫法，同上面修改會員資料一樣
public void updateProfile(Long id, MemberUpdateRequest request) {
    Member targetMember = memberRepository
        .findByIdAndStatus(id, "ACTIVE")
        .orElseThrow(() -> new ResourceNotFoundException(
            "member", ResponseMessages.MEMBER_NOT_FOUND));

    String email = request.getEmail().trim();

    // 檢查其他會員是否使用這個 Email，排除自己
    if (memberRepository.existsByEmailAndIdNot(email, id)) {
        throw new MemberAccountExcption(
            "email", ResponseMessages.MEMBER_EMAIL_EXISTS);
    }

    targetMember.setName(request.getName().trim());
    targetMember.setEmail(email);
}
*/



    //修改密碼
    @Transactional
    public void updatePassword(Long id,MemberPasswordUpdateRequest request){
        //1. 給repository找
        Optional<Member> member =  memberRepository.findByIdAndStatus(id, "ACTIVE");
           
            
        // 比對傳入的密碼跟確認密碼
        if(!request.getPassword().equals(request.getConfirmPassword())){
            throw new MemberAccountExcption("confirmPassword", ResponseMessages.MEMBER_CONFIRM_PASSWORD_NOT_MATCH);
        }

        //2.如果找不到
        if(member.isEmpty()){
            throw new ResourceNotFoundException("member",ResponseMessages.MEMBER_NOT_FOUND);
        }
        Member targetMember = member.get();
        
        if(!passwordEncoder.matches(request.getOldPassword(),targetMember.getPassword())){
            throw new MemberAccountExcption("oldPassword", "舊密碼不正確");
        }


        // 3. 有找到，就把Member 拿出來
        targetMember.setPassword(this.passwordEncoder.encode(request.getPassword()));
    }

@Transactional
  public TokenResponse login(MemberLoginRequest request) {
    String account = request.getAccount().trim();
     //1. 給repository找
      Member member = memberRepository
        .findByAccountAndStatus(account, "ACTIVE")
        .orElseThrow(() -> new MemberAccountExcption(
            "account", ResponseMessages.MEMBER_LOGIN_FAILED));
        
        if (!passwordEncoder.matches(
            request.getPassword(), member.getPassword())) {

        throw new MemberAccountExcption(
            "password", ResponseMessages.MEMBER_LOGIN_FAILED);
    }
  String ownerType = AuthOwnerTypes.MEMBER;
        Long ownerId = member.getId();
        LocalDateTime accessExpiresAt = jwtService.getAccessExpiresAt();
        LocalDateTime refreshExpiresAt = jwtService.getRefreshExpiresAt();
        String accessToken = jwtService.generateAccessToken(ownerType, ownerId, accessExpiresAt);
        String refreshToken = jwtService.generateRefreshToken(ownerType, ownerId, refreshExpiresAt);

        AuthToken authToken = new AuthToken(
                ownerType,
                ownerId,
                jwtService.hashToken(accessToken),
                jwtService.hashToken(refreshToken),
                accessExpiresAt,
                refreshExpiresAt
        );
        
       authTokenRepository.save(authToken);
    return  new TokenResponse(accessToken, refreshToken, accessExpiresAt, refreshExpiresAt);
  }
    //會員登出
    @Transactional
    public void logout(String refreshToken) {
        String tokenHash = jwtService.hashToken(refreshToken);

        AuthToken authToken = authTokenRepository
            .findActiveByRefreshTokenHashAndOwnerType(
                tokenHash, AuthOwnerTypes.MEMBER)
            .orElseThrow(() -> new AuthException(
                "refreshToken", ResponseMessages.TOKEN_INVALID));

        authToken.setRevoked(TOKEN_REVOKED);
        authToken.setDeletedAt(LocalDateTime.now());
    }
    //停用會員(軟刪除))
    @Transactional 
    public  void disableMember(long id){
        // 1. 給repository找
        Optional<Member> member =  memberRepository.findByIdAndStatus(id, "ACTIVE");
           
        // 2. 如果找不到
        if(member.isEmpty()){
            throw new ResourceNotFoundException(
                "member",
                ResponseMessages.MEMBER_NOT_FOUND);
        }

        Member targetMember = member.get();
      

       // 停用會員，保留原帳號與 Email。
        targetMember.setStatus("DISABLED");
   
    // 撤銷這位會員所有尚未撤銷的登入紀錄
    LocalDateTime now = LocalDateTime.now();

    authTokenRepository
    .findByOwnerTypeAndOwnerIdAndRevoked(
        AuthOwnerTypes.MEMBER, id, (byte) 0)
    .forEach(token -> {
        token.setRevoked(TOKEN_REVOKED);
        token.setDeletedAt(now);
    });
      
    }

}
  








