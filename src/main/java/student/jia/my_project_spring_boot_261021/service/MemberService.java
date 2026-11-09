package student.jia.my_project_spring_boot_261021.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.hibernate.boot.models.MemberResolutionException;
import org.springframework.beans.factory.annotation.Value;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import student.jia.my_project_spring_boot_261021.constant.ResponseMessages;
import student.jia.my_project_spring_boot_261021.entity.Member;
import student.jia.my_project_spring_boot_261021.entity.PasswordResetToken;
import student.jia.my_project_spring_boot_261021.entity.AuthToken;
import student.jia.my_project_spring_boot_261021.exception.MemberAccountExcption;
import student.jia.my_project_spring_boot_261021.exception.ResourceNotFoundException;
import student.jia.my_project_spring_boot_261021.repository.AuthTokenRepository;
import student.jia.my_project_spring_boot_261021.repository.MemberRepository;
import student.jia.my_project_spring_boot_261021.repository.PasswordResetTokenRepository;
import student.jia.my_project_spring_boot_261021.request.MemberForgotPasswordRequest;
import student.jia.my_project_spring_boot_261021.request.MemberLoginRequest;
import student.jia.my_project_spring_boot_261021.request.MemberPasswordResetRequest;
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
private MailService mailService;
private AuthTokenRepository authTokenRepository;
private Byte TOKEN_REVOKED = 1;
private PasswordResetTokenRepository passwordResetTokenRepository;
private static final SecureRandom SECURE_RANDOM = new SecureRandom(); //SECURE_RANDOM:安全隨機
private String appBaseUrl;
private long passwordResetTokenMinutes;
//建構子注入
public MemberService(MemberRepository memberRepository,
    PasswordEncoder passwordEncoder,
    JwtService jwtService,
    MailService mailService,
    AuthTokenRepository authTokenRepository,
    PasswordResetTokenRepository passwordResetTokenRepository,
    @Value("${app.base-url}") String appBaseUrl,
    @Value("${member.password-reset-token-minutes}") long passwordResetTokenMinutes
){
    this.memberRepository = memberRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.mailService = mailService;
    this.authTokenRepository = authTokenRepository;
    this.passwordResetTokenRepository = passwordResetTokenRepository;
    this.appBaseUrl = appBaseUrl;
    this.passwordResetTokenMinutes = passwordResetTokenMinutes;
}
//新增會員
@Transactional 
public  void register(MemberRegisterRequest request){
        // 調整：三個新密碼入口共用規則，避免繞過畫面驗證。
        validateNewPassword(request.getPassword(), request.getConfirmPassword());
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
// 會員資料修改以保留在上方的傳統 Optional 判斷為準。



    //修改密碼
    @Transactional
    public void updatePassword(Long id,MemberPasswordUpdateRequest request){
        // 調整：三個新密碼入口共用規則，避免繞過畫面驗證。
        validateNewPassword(request.getPassword(), request.getConfirmPassword());
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
        
        // 調整：舊密碼只檢查 BCrypt 上限，保留既有短密碼的驗證能力。
        validatePasswordBytes(request.getOldPassword(), "oldPassword");
        if(!passwordEncoder.matches(request.getOldPassword(),targetMember.getPassword())){
            throw new MemberAccountExcption("oldPassword", "舊密碼不正確");
        }


        // 3. 有找到，就把Member 拿出來
        targetMember.setPassword(this.passwordEncoder.encode(request.getPassword()));
    }

@Transactional
  public TokenResponse login(MemberLoginRequest request) {
    // 調整：登入保留既有最短長度，僅阻擋超過 BCrypt 上限的輸入。
    validatePasswordBytes(request.getPassword(), "password");
    String account = request.getAccount().trim();
     //1. 給repository找
    // 查詢回傳 Optional，可能有會員，也可能查不到；先判斷才能取出資料。
    Optional<Member> optionalMember =
            memberRepository.findByAccountAndStatus(account, "ACTIVE");
    if (optionalMember.isEmpty()) {
        // 查無會員時交給 GlobalExceptionHandler.handleMemberAccount 回傳原本的登入錯誤。
        throw new MemberAccountExcption(
                "account", ResponseMessages.MEMBER_LOGIN_FAILED);
    }
    Member member = optionalMember.get();
        
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

        // 以 refresh token 的雜湊查詢有效登入紀錄，Optional 為空表示沒有可登出的紀錄。
        Optional<AuthToken> optionalAuthToken =
                authTokenRepository.findActiveByRefreshTokenHashAndOwnerType(
                        tokenHash, AuthOwnerTypes.MEMBER);
        if (optionalAuthToken.isEmpty()) {
            // 驗證失敗時由 GlobalExceptionHandler.handleAuth 回傳原本的 401 錯誤。
            throw new AuthException(
                    "refreshToken", ResponseMessages.TOKEN_INVALID);
        }
        AuthToken authToken = optionalAuthToken.get();

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

    // 先取得此會員尚未撤銷的登入紀錄，再逐筆設定撤銷狀態與同一個刪除時間。
    List<AuthToken> activeTokens =
            authTokenRepository.findByOwnerTypeAndOwnerIdAndRevoked(
                    AuthOwnerTypes.MEMBER, id, (byte) 0);
    for (AuthToken token : activeTokens) {
        token.setRevoked(TOKEN_REVOKED);
        token.setDeletedAt(now);
    }
    // 這些紀錄在同一個 @Transactional 交易中查出，JPA 會在交易提交時更新資料。
    }
    //忘記密碼
     @Transactional
    public void forgotPassword(MemberForgotPasswordRequest request) {

        String email = request.getEmail().trim();

        Optional<Member> member = this.memberRepository.findByEmailAndStatus(email, "ACTIVE");

        if(member.isEmpty()){
            return ;
        }

        //有找到就繼續
        Member targetMember = member.get();

          // 若會員沒有 email，就無法寄送重設密碼連結。
        // 這裡仍然直接 return，維持和「會員不存在」相同的對外回應。
        if (targetMember.getEmail() == null || targetMember.getEmail().isBlank()) {
            return;
        }

         // 2. 產生rawToken 是真正寄給使用者的 token，只會出現在 email 連結中。

          LocalDateTime now = LocalDateTime.now();

        // rawToken 是真正寄給使用者的 token，只會出現在 email 連結中。
        // DB 不存 rawToken，只存 SHA-256 hash，降低資料庫外洩時 token 被直接拿去使用的風險。
        String rawToken = generatePasswordResetToken();
        String tokenHash = jwtService.hashToken(rawToken);

    // token 有效時間由 application.properties 的 member.password-reset-token-minutes 控制。
        // 預設是 30 分鐘，避免很久以前的信件連結還能重設密碼。
        LocalDateTime expiresAt = now.plusMinutes(passwordResetTokenMinutes);

        // 同一位會員重新申請忘記密碼時，先把舊的未使用 token 作廢。
        // 這樣信箱裡只有最新那封信的連結有效，減少多個有效連結並存的風險。
        passwordResetTokenRepository.revokeActiveByMemberId(targetMember.getId(), now);



        // 3. 把產出的rawToken存在password_reset_tokens資料表
        // 儲存 token hash、會員 id、過期時間與未使用狀態。
        // save 成功後，後續 resetPassword 才能用 token hash 查回這筆紀錄。
        passwordResetTokenRepository.save(new PasswordResetToken(targetMember.getId(), tokenHash, expiresAt));


        // 4. 把link = app_url + token 組合起來, 寄信給使用者
        // ex: http://localhost:8080/members-resset-password?token=xxxxxxxx

        // appBaseUrl 是網站對外網址，例如 http://localhost:8080 或正式站 https://example.com。
        // email 必須放完整 URL，使用者點擊後才會回到本系統的重設密碼頁。
        // String resetLink = appBaseUrl + "/page/reset-password?token=" + rawToken;

        String resetLink =
        appBaseUrl + "/reset-password.html?token=" + rawToken;

        // 信件內容只放重設連結與有效時間。
        // 不把密碼、會員 id 或 token hash 放進信中。
        String content = """
                您好，

                請點擊下方連結重設密碼：
                %s

                此連結將在 %d 分鐘後失效，若您沒有申請重設密碼，請忽略此信。
                """.formatted(resetLink, passwordResetTokenMinutes);

        // 寄信失敗時 MailService 會丟 MailException，GlobalExceptionHandler 會轉成統一 API 錯誤回應。
        mailService.sendEmail(targetMember.getEmail(), "重設會員密碼", content);
    }
      // 重設密碼頁面驗證token
    public boolean isPasswordResetTokenValid(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
         return passwordResetTokenRepository
                .findValidByTokenHash(jwtService.hashToken(token.trim()), LocalDateTime.now())
                .isPresent();
    }
    // 產生密碼重設令牌
    private String generatePasswordResetToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    public  Optional<Member> findByLineUid(String lindUid){
        return memberRepository.findOneByLineUidAndStatus(lindUid);
    }
    //重設密碼
    @Transactional
    public void resetPassword(MemberPasswordResetRequest request) {
        // 調整：三個新密碼入口共用規則，避免繞過畫面驗證。
        validateNewPassword(request.getPassword(), request.getConfirmPassword());
        // 1. 檢查Token

        //(1.1) 給repository找
        String token = request.getToken().trim();
        LocalDateTime  now = LocalDateTime.now();
        Optional<PasswordResetToken> passwordResetToken = passwordResetTokenRepository.findValidByTokenHash(jwtService.hashToken(token), now);

        //  (1.2). 如果資料庫找不到token
        if(passwordResetToken.isEmpty()){
            throw new  MemberAccountExcption("token", ResponseMessages.PASSWORD_RESET_TOKEN_INVALID);//PASSWORD_RESET_TOKEN_INVALID:重設密碼 token 不合法或已過期    
        }

        // 有找到就繼續
        PasswordResetToken targetTokenHash = passwordResetToken.get();

        // 2. 找target member
        Long memberId = targetTokenHash.getMemberId();
        Optional<Member> member = this.memberRepository.findOneById(memberId);
        if(member.isEmpty()){
                throw new ResourceNotFoundException(
                    "member",
                     ResponseMessages.MEMBER_NOT_FOUND);
        }

        Member  targetMember = member.get();
        //3. 修改密碼
         targetMember.setPassword(this.passwordEncoder.encode(request.getPassword()));

        // 4. 註銷token
        // token 使用後立刻標記已使用並 soft delete。
        // 這可以防止同一封 email 連結被重複點擊後再次修改密碼。
        Byte PASSWORD_RESET_TOKEN_USED = 1;
        targetTokenHash.setUsed(PASSWORD_RESET_TOKEN_USED);
        targetTokenHash.setDeletedAt(LocalDateTime.now());

    }
    
    // 調整：傳統 if 集中驗證，錯誤交給既有 GlobalExceptionHandler。
    private void validateNewPassword(String password, String confirmPassword) {
        if (password == null || password.isBlank()) {
            throw new MemberAccountExcption("password", ResponseMessages.MEMBER_PASSWORD_REQUIRED);
        }
        if (password.length() < 15 || password.length() > 72) {
            throw new MemberAccountExcption("password", ResponseMessages.MEMBER_PASSWORD_SIZE);
        }
        validatePasswordBytes(password, "password");
        if (confirmPassword == null || confirmPassword.isBlank()) {
            throw new MemberAccountExcption("confirmPassword", ResponseMessages.MEMBER_CONFIRM_PASSWORD_REQUIRED);
        }
        if (!password.equals(confirmPassword)) {
            throw new MemberAccountExcption("confirmPassword", ResponseMessages.MEMBER_CONFIRM_PASSWORD_NOT_MATCH);
        }
    }

    // 調整：@Size 計算 Java 字串長度；BCrypt 限制 UTF-8 編碼後的 bytes。
    private void validatePasswordBytes(String password, String field) {
        if (password != null && password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new MemberAccountExcption(field, ResponseMessages.MEMBER_PASSWORD_BYTES);
        }
    }

}
  








