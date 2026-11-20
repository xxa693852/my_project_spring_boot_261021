package student.jia.my_project_spring_boot_261021.service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import student.jia.my_project_spring_boot_261021.constant.AuthOwnerTypes;
import static org.junit.jupiter.api.Assertions.*;

// 使用測試金鑰，不讀取正式設定、不連線資料庫。
class JwtTokenUniquenessTests {
    private static final String SECRET = "jwt-uniqueness-test-only-secret-key-at-least-32-bytes";
    private final JwtService service = new JwtService(SECRET, 900, 604800);

    @Test
    void sameOwnerAndExpiryAlwaysReceiveIndependentAccessAndRefreshIdentifiers() {
        LocalDateTime expiry = LocalDateTime.now().plusHours(1).withNano(0);
        Set<String> identifiers = new HashSet<>();
        Set<String> hashes = new HashSet<>();
        // 固定相同會員與到期時間，直接驗證每次簽發的識別碼，不靠等待跨秒。
        for (int index = 0; index < 100; index++) {
            String access = service.generateAccessToken(AuthOwnerTypes.MEMBER, 7L, expiry);
            String refresh = service.generateRefreshToken(AuthOwnerTypes.MEMBER, 7L, expiry);
            for (String token : new String[] {access, refresh}) {
                Claims claims = service.parse(token);
                assertNotNull(claims.getId());
                assertEquals(claims.getId(), UUID.fromString(claims.getId()).toString());
                assertTrue(identifiers.add(claims.getId()), "每個 Token 都必須有獨立識別碼");
                assertTrue(hashes.add(service.hashToken(token)), "資料庫 Token 雜湊不得重複");
                assertEquals("7", claims.getSubject());
                assertEquals(AuthOwnerTypes.MEMBER, claims.get("ownerType", String.class));
                assertEquals(expiry, service.getExpiresAt(claims));
            }
            assertEquals("access", service.parse(access).get("tokenType", String.class));
            assertEquals("refresh", service.parse(refresh).get("tokenType", String.class));
        }
        assertEquals(200, identifiers.size());
        assertEquals(200, hashes.size());
    }

    @Test
    void existingSignedTokensWithoutIdentifierRemainValid() {
        LocalDateTime expiry = LocalDateTime.now().plusHours(1).withNano(0);
        // 模擬更新前已簽發、沒有 jti 的登入憑證；新增識別碼不應強制登出既有使用者。
        for (String tokenType : new String[] {"access", "refresh"}) {
            String legacy = Jwts.builder().subject("7")
                    .claim("ownerType", AuthOwnerTypes.MEMBER).claim("tokenType", tokenType)
                    .issuedAt(new Date())
                    .expiration(Date.from(expiry.atZone(ZoneId.systemDefault()).toInstant()))
                    .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
            Claims claims = service.parse(legacy);
            assertNull(claims.getId());
            assertEquals("7", claims.getSubject());
            assertEquals(tokenType, claims.get("tokenType", String.class));
            assertEquals(expiry, service.getExpiresAt(claims));
        }
    }
}
