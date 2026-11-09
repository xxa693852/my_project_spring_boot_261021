package student.jia.my_project_spring_boot_261021;

import java.util.Optional;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import student.jia.my_project_spring_boot_261021.constant.ResponseMessages;
import student.jia.my_project_spring_boot_261021.entity.Member;
import student.jia.my_project_spring_boot_261021.exception.MemberAccountExcption;
import student.jia.my_project_spring_boot_261021.repository.*;
import student.jia.my_project_spring_boot_261021.request.*;
import student.jia.my_project_spring_boot_261021.service.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// 調整驗證：只用模擬物件，測試不寄信、不修改資料庫。
class MemberInputValidationTests {
    private final MemberRepository members = mock(MemberRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final PasswordResetTokenRepository resetTokens = mock(PasswordResetTokenRepository.class);
    private final MemberService service = new MemberService(members, encoder,
            mock(JwtService.class), mock(MailService.class), mock(AuthTokenRepository.class),
            resetTokens, "http://localhost:8081", 30L);

    private MemberRegisterRequest registration(String password) {
        MemberRegisterRequest request = new MemberRegisterRequest();
        request.setName("Tester");
        request.setAccount("tester");
        request.setEmail("tester@example.com");
        request.setPassword(password);
        request.setConfirmPassword(password);
        return request;
    }

    @Test
    void newPasswordBoundariesAreConsistentAcrossRequests() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            for (int length : new int[] {14, 15, 72, 73}) {
                String password = "a".repeat(length);
                MemberPasswordUpdateRequest update = new MemberPasswordUpdateRequest();
                update.setPassword(password);
                MemberPasswordResetRequest reset = new MemberPasswordResetRequest();
                reset.setPassword(password);
                boolean valid = length >= 15 && length <= 72;
                assertEquals(valid, factory.getValidator().validateProperty(registration(password), "password").isEmpty());
                assertEquals(valid, factory.getValidator().validateProperty(update, "password").isEmpty());
                assertEquals(valid, factory.getValidator().validateProperty(reset, "password").isEmpty());
            }
        }
    }

    @Test
    void emailBoundaryIsConsistentAcrossRequests() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            String email254 = "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(61);
            for (String email : new String[] {email254, "a" + email254}) {
                MemberRegisterRequest register = registration("a".repeat(15));
                register.setEmail(email);
                MemberUpdateRequest update = new MemberUpdateRequest();
                update.setEmail(email);
                MemberForgotPasswordRequest forgot = new MemberForgotPasswordRequest();
                forgot.setEmail(email);
                for (Object request : new Object[] {register, update, forgot}) {
                    boolean lengthError = false;
                    for (var violation : factory.getValidator().validateProperty(request, "email")) {
                        if (ResponseMessages.MEMBER_EMAIL_MAX.equals(violation.getMessage())) {
                            lengthError = true;
                        }
                    }
                    assertEquals(email.length() > 254, lengthError);
                }
            }
        }
    }

    @Test
    void acceptsExactly72Utf8BytesIncludingChinese() {
        for (String password : new String[] {"a".repeat(72), "中".repeat(24)}) {
            service.register(registration(password));
            verify(encoder).encode(password);
        }
        verify(members, times(2)).save(any(Member.class));
    }

    @Test
    void rejectsChinesePasswordOver72BytesBeforeEncoding() {
        MemberAccountExcption error = assertThrows(MemberAccountExcption.class,
                () -> service.register(registration("中".repeat(25))));
        assertEquals(ResponseMessages.MEMBER_PASSWORD_BYTES, error.getMessageCode());
        verifyNoInteractions(encoder, members);
    }

    @Test
    void updateAlsoRejectsPasswordOverByteLimit() {
        MemberPasswordUpdateRequest request = new MemberPasswordUpdateRequest();
        request.setPassword("中".repeat(25));
        request.setConfirmPassword(request.getPassword());
        MemberAccountExcption error = assertThrows(MemberAccountExcption.class,
                () -> service.updatePassword(1L, request));
        assertEquals(ResponseMessages.MEMBER_PASSWORD_BYTES, error.getMessageCode());
        verifyNoInteractions(encoder, members);
    }

    @Test
    void resetRejectsMismatchBeforeConsumingToken() {
        MemberPasswordResetRequest request = new MemberPasswordResetRequest();
        request.setPassword("a".repeat(15));
        request.setConfirmPassword("b".repeat(15));
        MemberAccountExcption error = assertThrows(MemberAccountExcption.class,
                () -> service.resetPassword(request));
        assertEquals(ResponseMessages.MEMBER_CONFIRM_PASSWORD_NOT_MATCH, error.getMessageCode());
        verifyNoInteractions(resetTokens, encoder);
    }

    @Test
    void loginStillAcceptsExistingShortPasswordForVerification() {
        MemberLoginRequest request = new MemberLoginRequest();
        request.setAccount("legacy");
        request.setPassword("short1");
        when(members.findByAccountAndStatus("legacy", "ACTIVE")).thenReturn(Optional.empty());
        MemberAccountExcption error = assertThrows(MemberAccountExcption.class, () -> service.login(request));
        assertEquals(ResponseMessages.MEMBER_LOGIN_FAILED, error.getMessageCode());
        verify(members).findByAccountAndStatus("legacy", "ACTIVE");
    }

    @Test
    void loginRejectsTooManyBytesBeforeBcrypt() {
        MemberLoginRequest request = new MemberLoginRequest();
        request.setPassword("a".repeat(73));
        MemberAccountExcption error = assertThrows(MemberAccountExcption.class, () -> service.login(request));
        assertEquals(ResponseMessages.MEMBER_PASSWORD_BYTES, error.getMessageCode());
        verifyNoInteractions(encoder);
    }
}
