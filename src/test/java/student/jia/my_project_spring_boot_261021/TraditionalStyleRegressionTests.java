package student.jia.my_project_spring_boot_261021;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import student.jia.my_project_spring_boot_261021.repository.PasswordResetTokenRepository;
import student.jia.my_project_spring_boot_261021.constant.AuthOwnerTypes;
import student.jia.my_project_spring_boot_261021.constant.ResponseMessages;
import student.jia.my_project_spring_boot_261021.entity.AuthToken;
import student.jia.my_project_spring_boot_261021.entity.Member;
import student.jia.my_project_spring_boot_261021.exception.AuthException;
import student.jia.my_project_spring_boot_261021.exception.GlobalExceptionHandler;
import student.jia.my_project_spring_boot_261021.exception.MemberAccountExcption;
import student.jia.my_project_spring_boot_261021.repository.AuthTokenRepository;
import student.jia.my_project_spring_boot_261021.repository.MemberRepository;
import student.jia.my_project_spring_boot_261021.request.MemberLoginRequest;
import student.jia.my_project_spring_boot_261021.response.ApiResponse;
import student.jia.my_project_spring_boot_261021.service.JwtService;
import student.jia.my_project_spring_boot_261021.service.MemberService;
import student.jia.my_project_spring_boot_261021.service.MailService;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// 只使用模擬 Repository 驗證行為，不連接或修改實際資料庫。
class TraditionalStyleRegressionTests {
    private final MemberRepository members = mock(MemberRepository.class);
    private final AuthTokenRepository tokens = mock(AuthTokenRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final PasswordResetTokenRepository resetTokens =
        mock(PasswordResetTokenRepository.class);
    private final MailService mailService = mock(MailService.class);
    private final MemberService service = 
        new MemberService(
             members,
             passwordEncoder, 
             jwtService,
             mailService,
              tokens, 
              resetTokens,  
              "http://localhost:8080",
              30L);
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    
    @Test
    void validationKeepsFirstMessageAndFieldOrder() {
        // 同一欄位有多個錯誤時，仍只回傳第一筆，欄位排列也維持原順序。
        BeanPropertyBindingResult binding =
                new BeanPropertyBindingResult(new Object(), "request");
        binding.addError(new FieldError("request", "email", ResponseMessages.MEMBER_EMAIL_EXISTS));
        binding.addError(new FieldError("request", "email", "第二筆錯誤"));
        binding.addError(new FieldError("request", "name", "姓名錯誤"));
        MethodArgumentNotValidException exception =
                new MethodArgumentNotValidException(null, binding);

        ApiResponse response = handler.handleValidation(exception);

        assertEquals(ResponseMessages.getMessage(ResponseMessages.VALIDATION_FAILED), response.getMessage());
        assertEquals(ResponseMessages.getMessage(ResponseMessages.MEMBER_EMAIL_EXISTS),
                response.getErrors().get("email"));
        assertEquals("姓名錯誤", response.getErrors().get("name"));
        assertEquals(List.of("email", "name"), new ArrayList<>(response.getErrors().keySet()));
    }

    @Test
    void messageResolutionPreservesLiteralAndNullFallback() {
        // 有代碼則轉中文；沒有對應代碼則保留文字；null 則維持預設訊息。
        assertEquals(ResponseMessages.getMessage(ResponseMessages.EVENT_FULL),
                handler.handleIllegalArgument(new IllegalArgumentException(ResponseMessages.EVENT_FULL)).getMessage());
        assertEquals("自訂錯誤", handler.handleIllegalArgument(new IllegalArgumentException("自訂錯誤")).getMessage());
        assertEquals("Invalid value", handler.handleIllegalArgument(new IllegalArgumentException()).getMessage());
    }

    @Test
    void missingLoginMemberKeepsOriginalErrorAndDoesNotIssueTokens() {
        MemberLoginRequest request = new MemberLoginRequest();
        request.setAccount(" missing ");
        request.setPassword("password");
        when(members.findByAccountAndStatus("missing", "ACTIVE")).thenReturn(Optional.empty());

        // 用明確的 try/catch 檢查例外，方便對照 Service 中的 throw。
        try {
            service.login(request);
            fail("查無會員時應該拒絕登入");
        } catch (MemberAccountExcption exception) {
            assertEquals("account", exception.getErrorKey());
            assertEquals(ResponseMessages.MEMBER_LOGIN_FAILED, exception.getMessageCode());
        }
        verifyNoInteractions(passwordEncoder, jwtService, tokens);
    }

    @Test
    void missingLogoutTokenKeepsOriginalError() {
        when(jwtService.hashToken("refresh")).thenReturn("hash");
        when(tokens.findActiveByRefreshTokenHashAndOwnerType("hash", AuthOwnerTypes.MEMBER))
                .thenReturn(Optional.empty());

        try {
            service.logout("refresh");
            fail("查無有效 Token 時應該拒絕登出");
        } catch (AuthException exception) {
            assertEquals("refreshToken", exception.getErrorKey());
            assertEquals(ResponseMessages.TOKEN_INVALID, exception.getMessageCode());
        }
    }

    @Test
    void logoutRevokesExistingToken() {
        AuthToken token = token("one");
        when(jwtService.hashToken("refresh")).thenReturn("hash");
        when(tokens.findActiveByRefreshTokenHashAndOwnerType("hash", AuthOwnerTypes.MEMBER))
                .thenReturn(Optional.of(token));

        service.logout("refresh");

        assertEquals((byte) 1, token.getRevoked());
        assertNotNull(token.getDeletedAt());
    }

    @Test
    void disablingMemberRevokesEveryActiveTokenAtSameTime() {
        Member member = new Member("account", "hash", "Name", "member@example.com");
        AuthToken first = token("first");
        AuthToken second = token("second");
        when(members.findByIdAndStatus(7L, "ACTIVE")).thenReturn(Optional.of(member));
        when(tokens.findByOwnerTypeAndOwnerIdAndRevoked(AuthOwnerTypes.MEMBER, 7L, (byte) 0))
                .thenReturn(List.of(first, second));

        service.disableMember(7L);

        // 確認迴圈處理所有登入紀錄，且沿用同一個撤銷時間。
        assertEquals("DISABLED", member.getStatus());
        assertEquals((byte) 1, first.getRevoked());
        assertEquals((byte) 1, second.getRevoked());
        assertNotNull(first.getDeletedAt());
        assertEquals(first.getDeletedAt(), second.getDeletedAt());
    }

    private AuthToken token(String suffix) {
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(1);
        return new AuthToken(AuthOwnerTypes.MEMBER, 7L, "access-" + suffix,
                "refresh-" + suffix, expiresAt, expiresAt);
    }
}
