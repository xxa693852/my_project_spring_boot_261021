package student.jia.my_project_spring_boot_261021.interceptor;



import java.time.LocalDateTime;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.HandlerInterceptor;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import student.jia.my_project_spring_boot_261021.constant.AuthOwnerTypes;
import student.jia.my_project_spring_boot_261021.constant.ResponseMessages;
import student.jia.my_project_spring_boot_261021.entity.AuthToken;
import student.jia.my_project_spring_boot_261021.exception.AuthException;
import student.jia.my_project_spring_boot_261021.repository.AuthTokenRepository;
import student.jia.my_project_spring_boot_261021.response.TokenResponse;
import student.jia.my_project_spring_boot_261021.service.JwtService;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String AUTH_MEMBER_ID_ATTRIBUTE = "authMemberId";

    // 目前先保護 /members/{id} 類型的 API，避免拿自己的 token 操作別人的會員資料。
    private static final Pattern MEMBER_ID_PATH_PATTERN = Pattern.compile("^/members/(\\d+)(/.*)?$");
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String REFRESH_TOKEN_HEADER = "X-Refresh-Token";
    private static final Byte TOKEN_REVOKED = 1;

    private final JwtService jwtService;
    private final AuthTokenRepository authTokenRepository;

    public AuthInterceptor(JwtService jwtService, AuthTokenRepository authTokenRepository) {
        this.jwtService = jwtService;
        this.authTokenRepository = authTokenRepository;
    }

    // 每個受保護 API 進 controller 前都會先進到這裡。
    // 流程：
    // 1. 從 Authorization header 取出 access token。
    // 2. access token 有效時，檢查 token 類型、DB 狀態、會員 id 是否符合路徑。
    // 3. access token 已過期時，改用 X-Refresh-Token 自動換一組新 token。
    // 4. token 格式錯誤或簽章不合法時，直接回 token 不合法。
    @Override
    @Transactional
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) {
        String accessToken = getBearerToken(request);

        try {
            // access token 有效就直接放行。
            Claims accessClaims = jwtService.parse(accessToken);
            // Claims accessClaims = {
            //     "sub": "3",
            //     "ownerType": "MEMBER",
            //     "tokenType": "access",
            //     "iat": 1799397000,
            //     "exp": 1799397900,
            // }
            Long ownerId = Long.valueOf(accessClaims.getSubject());
            try {
                validateAccessToken(accessToken, accessClaims, request);
            } catch (AuthException exception) {
                if (!ResponseMessages.TOKEN_EXPIRED.equals(exception.getMessageCode())) {
                    throw exception;
                }

                // JWT exp 尚未觸發過期，但 DB access_expires_at 已過期時，也走自動 refresh。
                refreshTokenAndSetHeaders(request, response, accessClaims);
            }
            request.setAttribute(AUTH_MEMBER_ID_ATTRIBUTE, ownerId);
            return true;
        } catch (ExpiredJwtException exception) {
            // access token 過期時，改用 X-Refresh-Token 自動換新 token。
            refreshTokenAndSetHeaders(request, response, exception.getClaims());
            request.setAttribute(AUTH_MEMBER_ID_ATTRIBUTE, Long.valueOf(exception.getClaims().getSubject()));
            return true;
        } catch (JwtException | IllegalArgumentException exception) {
            throw new AuthException("token", ResponseMessages.TOKEN_INVALID);
        }
    }

    // 從 Authorization header 取出 Bearer token。
    // 沒有 Authorization、不是 Bearer 格式、或 token 是空字串時，都視為 token 必填。
    private String getBearerToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");

        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new AuthException("token", ResponseMessages.TOKEN_REQUIRED);
        }

        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (token.isBlank()) {
            throw new AuthException("token", ResponseMessages.TOKEN_REQUIRED);
        }

        return token;
    }

    // 驗證 access token 的業務規則。
    // JWT parse 只保證簽章與 exp 正確；這裡額外檢查：
    // 1. ownerType 必須是 MEMBER。
    // 2. tokenType 必須是 access。
    // 3. DB 仍有這顆 active access token，代表沒有被 logout 或 refresh rotation 撤銷。
    // 4. 若 API path 是 /members/{id}，只能操作 token 所屬會員自己的資料。
    private void validateAccessToken(String accessToken, Claims claims, HttpServletRequest request) {
        String ownerType = claims.get("ownerType", String.class);
        String tokenType = claims.get("tokenType", String.class);
        Long ownerId = Long.valueOf(claims.getSubject());

        if (!AuthOwnerTypes.MEMBER.equals(ownerType) || !"access".equals(tokenType)) {
            throw new AuthException("token", ResponseMessages.TOKEN_INVALID);
        }

        // 除了 JWT 本身有效，也要確認 MySQL 中這顆 token 沒被 logout 或 refresh rotation 撤銷。
        Optional<AuthToken> authToken = authTokenRepository.findActiveByAccessTokenHashAndOwnerType(
                jwtService.hashToken(accessToken),
                AuthOwnerTypes.MEMBER
        );

        if (authToken.isEmpty()) {
            throw new AuthException("token", ResponseMessages.TOKEN_INVALID);
        }

        if (authToken.get().getAccessExpiresAt().isBefore(LocalDateTime.now())) {
            throw new AuthException("token", ResponseMessages.TOKEN_EXPIRED);
        }

        validateMemberPathOwner(request, ownerId);
    }

    // access token 過期時的自動 refresh 流程。
    // 前端必須同時帶 X-Refresh-Token，且 refresh token 必須：
    // 1. 是合法 JWT。
    // 2. ownerType 是 MEMBER。
    // 3. tokenType 是 refresh。
    // 4. ownerId 和過期 access token 的 ownerId 相同。
    // 5. DB 中仍是 active 狀態，尚未過期、尚未 logout、尚未被用過。
    // 成功後會撤銷舊 refresh token，建立新 access/refresh token，並放到 response headers。
    private void refreshTokenAndSetHeaders(
            HttpServletRequest request,
            HttpServletResponse response,
            Claims expiredAccessClaims) {
        // 自動 refresh 需要前端同時帶 X-Refresh-Token；如果 refresh 也失效，就要求重新登入。
        String refreshToken = getRefreshToken(request);
        if (refreshToken == null) {
            throw new AuthException("token", ResponseMessages.TOKEN_EXPIRED);
        }

        Long ownerId = Long.valueOf(expiredAccessClaims.getSubject());
        validateMemberPathOwner(request, ownerId);

        Claims refreshClaims = parseRefreshToken(refreshToken);
        String ownerType = refreshClaims.get("ownerType", String.class);
        String tokenType = refreshClaims.get("tokenType", String.class);
        Long refreshOwnerId = Long.valueOf(refreshClaims.getSubject());

        if (!AuthOwnerTypes.MEMBER.equals(ownerType)
                || !"refresh".equals(tokenType)
                || !ownerId.equals(refreshOwnerId)) {
            throw new AuthException("refreshToken", ResponseMessages.TOKEN_INVALID);
        }

        AuthToken oldToken = authTokenRepository
                .findActiveByRefreshTokenHashAndOwnerType(jwtService.hashToken(refreshToken), AuthOwnerTypes.MEMBER)
                .orElseThrow(() -> new AuthException("refreshToken", ResponseMessages.TOKEN_INVALID));

        // refresh token rotation：舊 token 用過後立即撤銷，只能使用新 token。
        if (oldToken.getRefreshExpiresAt().isBefore(LocalDateTime.now())) {
            oldToken.setRevoked(TOKEN_REVOKED);
            throw new AuthException("refreshToken", ResponseMessages.TOKEN_EXPIRED);
        }

        oldToken.setRevoked(TOKEN_REVOKED);
        oldToken.setDeletedAt(LocalDateTime.now());

        TokenResponse tokenResponse = createAndSaveToken(AuthOwnerTypes.MEMBER, ownerId);
        // 新 token 放在 response header，前端收到後要更新本地保存的雙令牌。
        response.setHeader("X-New-Access-Token", tokenResponse.getAccessToken());
        response.setHeader("X-New-Refresh-Token", tokenResponse.getRefreshToken());
        response.setHeader("X-New-Access-Expires-At", tokenResponse.getAccessExpiresAt().toString());
        response.setHeader("X-New-Refresh-Expires-At", tokenResponse.getRefreshExpiresAt().toString());
    }

    // 解析 refresh token，並把 JWT 套件的例外轉成專案自己的 AuthException。
    // 這樣 GlobalExceptionHandler 可以統一回傳 token 已過期或 token 不合法。
    private Claims parseRefreshToken(String refreshToken) {
        try {
            return jwtService.parse(refreshToken);
        } catch (ExpiredJwtException exception) {
            throw new AuthException("refreshToken", ResponseMessages.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new AuthException("refreshToken", ResponseMessages.TOKEN_INVALID);
        }
    }

    // 建立一組新的 access token 和 refresh token。
    // DB 不存原始 JWT，只存 hash 和過期時間，後續驗證時再用 hash 查 DB。
    private TokenResponse createAndSaveToken(String ownerType, Long ownerId) {
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
        return new TokenResponse(accessToken, refreshToken, accessExpiresAt, refreshExpiresAt);
    }

    // 從 X-Refresh-Token header 取出 refresh token。
    // 自動 refresh 才會用到；一般 access token 尚未過期時不會讀這個 header。
    private String getRefreshToken(HttpServletRequest request) {
        String refreshToken = request.getHeader(REFRESH_TOKEN_HEADER);
        if (refreshToken == null || refreshToken.isBlank()) {
            return null;
        }

        return refreshToken;
    }

    // 限制會員只能操作自己的 /members/{id} API。
    // 例如 token subject 是 1，只能呼叫 /members/1，不能呼叫 /members/2。
    // 如果 API path 不是 /members/{id} 格式，這個方法不會阻擋。
    private void validateMemberPathOwner(HttpServletRequest request, Long ownerId) {
        Matcher matcher = MEMBER_ID_PATH_PATTERN.matcher(request.getRequestURI());

        if (matcher.matches() && !ownerId.equals(Long.valueOf(matcher.group(1)))) {
            throw new AuthException("token", ResponseMessages.TOKEN_INVALID);
        }
    }

}