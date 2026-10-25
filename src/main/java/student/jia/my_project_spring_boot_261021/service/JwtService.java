package student.jia.my_project_spring_boot_261021.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    // 取得目前伺服器的預設時區，例如 Asia/Taipei。
    // LocalDateTime 本身沒有時區概念，但 JWT 的過期時間使用 Date，代表一個明確時間點。
    // 所以 LocalDateTime 和 Date 互相轉換時，需要指定同一個時區，避免時間換算錯誤。
    private static final ZoneId ZONE_ID = ZoneId.systemDefault();

    private final SecretKey secretKey;
    private final long accessTokenSeconds;
    private final long refreshTokenSeconds;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-seconds}") long accessTokenSeconds,
            @Value("${jwt.refresh-token-seconds}") long refreshTokenSeconds) {
        // 初始化 JWT 簽章用的 secret key 與 token 有效秒數。
        // JJWT 的 HMAC key 長度要足夠；正式環境請用 JWT_SECRET 覆蓋預設值。
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenSeconds = accessTokenSeconds;
        this.refreshTokenSeconds = refreshTokenSeconds;
    }

    // 產生 access token，過期時間使用系統設定的 jwt.access-token-seconds。
    public String generateAccessToken(String ownerType, Long ownerId) {
        return generateToken(ownerType, ownerId, "access", getAccessExpiresAt());
    }

    // 產生 access token，過期時間由呼叫端指定。
    // AuthInterceptor 自動 refresh 時會先算好 expiresAt，再呼叫這個 overload。
    public String generateAccessToken(String ownerType, Long ownerId, LocalDateTime expiresAt) {
        return generateToken(ownerType, ownerId, "access", expiresAt);
    }

    // 產生 refresh token，過期時間使用系統設定的 jwt.refresh-token-seconds。
    public String generateRefreshToken(String ownerType, Long ownerId) {
        return generateToken(ownerType, ownerId, "refresh", getRefreshExpiresAt());
    }

    // 產生 refresh token，過期時間由呼叫端指定。
    // 建立 token response 時會用同一個 expiresAt 寫入 JWT 與 auth_tokens 資料表。
    public String generateRefreshToken(String ownerType, Long ownerId, LocalDateTime expiresAt) {
        return generateToken(ownerType, ownerId, "refresh", expiresAt);
    }

    // 解析 JWT 並驗證簽章與 exp。
    // token 過期時 JJWT 會丟 ExpiredJwtException，格式或簽章錯誤時會丟 JwtException。
    public Claims parse(String token) {
        // 解析時會同時驗證簽章與 exp，token 過期會丟 ExpiredJwtException。
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // 將原始 JWT 做 SHA-256 hash。
    // auth_tokens 資料表只存 hash，不存原始 token，避免 DB 外洩時 token 可直接被拿去使用。
    public String hashToken(String token) {
        // DB 只存 token hash，不存原始 JWT，降低 token 外洩風險。
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();

            for (byte value : encoded) {
                hex.append(String.format("%02x", value));
            }

            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    // 回傳 access token 的實際過期時間。
    // 這個時間會同時寫進 JWT exp 與 auth_tokens.access_expires_at。
    public LocalDateTime getAccessExpiresAt() {
        return LocalDateTime.now().plusSeconds(accessTokenSeconds);
    }

    // 回傳 refresh token 的實際過期時間。
    // 這個時間會同時寫進 JWT exp 與 auth_tokens.refresh_expires_at。
    public LocalDateTime getRefreshExpiresAt() {
        return LocalDateTime.now().plusSeconds(refreshTokenSeconds);
    }

    // 取得 access token 設定的有效秒數，方便其他地方需要讀設定值時使用。
    public long getAccessTokenSeconds() {
        return accessTokenSeconds;
    }

    // 取得 refresh token 設定的有效秒數，方便其他地方需要讀設定值時使用。
    public long getRefreshTokenSeconds() {
        return refreshTokenSeconds;
    }

    // 從已解析的 JWT claims 取出 exp，轉成專案內使用的 LocalDateTime。
    public LocalDateTime getExpiresAt(Claims claims) {
        return LocalDateTime.ofInstant(claims.getExpiration().toInstant(), ZONE_ID);
    }

    // 實際建立 JWT 的共用方法。
    // subject 放 ownerId；ownerType 用來區分 MEMBER / ADMIN；tokenType 用來區分 access / refresh。
    private String generateToken(String ownerType, Long ownerId, String tokenType, LocalDateTime expiresAt) {
        // ownerType 用來支援 MEMBER / ADMIN 各自登入；tokenType 用來區分 access / refresh。
        return Jwts.builder()
                .subject(String.valueOf(ownerId))
                .claim("ownerType", ownerType)
                .claim("tokenType", tokenType)
                .issuedAt(new Date()) //發行日期
                .expiration(toDate(expiresAt)) //到期日
                .signWith(secretKey)
                .compact();
    }

    // JJWT 的 expiration 需要 java.util.Date，所以把 LocalDateTime 依系統時區轉成 Date。
    private Date toDate(LocalDateTime localDateTime) {
        return Date.from(localDateTime.atZone(ZONE_ID).toInstant());
    }
}