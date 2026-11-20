package student.jia.my_project_spring_boot_261021.service;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import student.jia.my_project_spring_boot_261021.entity.Member;
import student.jia.my_project_spring_boot_261021.repository.MemberRepository;
import student.jia.my_project_spring_boot_261021.request.MemberLoginRequest;

/**
 * 後台獨立使用伺服器端 Session，不接受會員 JWT，也不簽發可長期換發的 Token。
 * lastActivity 只在實際操作時更新；單純讀取資料不會延長 30 分鐘閒置期限。
 */
@Service
public class AdminAuthService {
    private static final String KEY = AdminAuthService.class.getName();
    public static final int IDLE_SECONDS = 30 * 60;
    private final MemberRepository members;
    private final PasswordEncoder passwords;
    private final Clock clock;

    @Autowired
    public AdminAuthService(MemberRepository members, PasswordEncoder passwords) {
        this(members, passwords, Clock.systemUTC());
    }

    // 測試可傳入固定時鐘，精確驗證 30 分鐘邊界，不必真的等待。
    public AdminAuthService(MemberRepository members, PasswordEncoder passwords, Clock clock) {
        this.members = members;
        this.passwords = passwords;
        this.clock = clock;
    }

    public AdminIdentity login(MemberLoginRequest input, HttpServletRequest request) {
        // 自訂 Header 不能透過一般跨站 HTML 表單送出；此處不開放跨來源 CORS。
        if (!"1".equals(request.getHeader("X-Admin-Login"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "請使用管理員登入頁。");
        }
        String password = input.getPassword();
        if (password == null || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw unauthorized();
        }
        Optional<Member> result = members.findByAccountAndStatus(input.getAccount().trim(), "ACTIVE");
        if (result.isEmpty()) {
            throw unauthorized();
        }
        Member member = result.get();
        if (!"ADMIN".equals(member.getRole()) || member.isSuspendedAt(nowInTaipei())
                || !passwords.matches(password, member.getPassword())) {
            throw unauthorized();
        }
        HttpSession session = request.getSession(true);
        // 登入成功後更換 Session ID，避免沿用登入前的識別碼。
        request.changeSessionId();
        session.setMaxInactiveInterval(IDLE_SECONDS);
        State state = new State(member.getId(), UUID.randomUUID().toString(), clock.instant());
        synchronized (session) {
            session.setAttribute(KEY, state);
        }
        return identity(member, state);
    }

    public AdminIdentity require(HttpServletRequest request, boolean touch) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw unauthorized();
        }
        synchronized (session) {
            Object stored = session.getAttribute(KEY);
            if (!(stored instanceof State)) {
                throw unauthorized();
            }
            State state = (State) stored;
            // 等於截止時間就算逾時，不能靠過期後的操作重新延長登入。
            if (!clock.instant().isBefore(state.lastActivity().plusSeconds(IDLE_SECONDS))) {
                session.removeAttribute(KEY);
                throw unauthorized();
            }
            Optional<Member> result = members.findByIdAndStatus(state.memberId(), "ACTIVE");
            if (result.isEmpty()) {
                session.removeAttribute(KEY);
                throw unauthorized();
            }
            Member member = result.get();
            if (!"ADMIN".equals(member.getRole()) || member.isSuspendedAt(nowInTaipei())) {
                session.removeAttribute(KEY);
                throw unauthorized();
            }
            if (touch) {
                // Cookie 會自動附帶，因此所有寫入操作都必須額外驗證 CSRF Token。
                if (!state.csrfToken().equals(request.getHeader("X-Admin-CSRF"))) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "後台驗證失敗，請重新登入。");
                }
                state = new State(state.memberId(), state.csrfToken(), clock.instant());
                session.setAttribute(KEY, state);
            }
            return identity(member, state);
        }
    }

    public void logout(HttpServletRequest request) {
        // 移除後台狀態；會員登入／前台登出也會呼叫，避免換帳號繼承權限。
        // 與活動續期使用同一把鎖，防止續期中的舊請求在登出後把狀態寫回。
        HttpSession session = request.getSession(false);
        if (session != null) {
            synchronized (session) {
                session.removeAttribute(KEY);
            }
        }
    }

    private java.time.LocalDateTime nowInTaipei() {
        return java.time.LocalDateTime.ofInstant(clock.instant(), ZoneId.of("Asia/Taipei"));
    }

    private AdminIdentity identity(Member member, State state) {
        return new AdminIdentity(member.getId(), member.getAccount(), member.getName(), "ADMIN",
                state.csrfToken(), state.lastActivity().plusSeconds(IDLE_SECONDS).toEpochMilli());
    }

    private ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "管理員登入無效或已逾時，請重新登入。");
    }

    private record State(Long memberId, String csrfToken, Instant lastActivity) implements Serializable {}
    public record AdminIdentity(Long id, String account, String name, String role,
            String csrfToken, long idleExpiresAt) {}
}
