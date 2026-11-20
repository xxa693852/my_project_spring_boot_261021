package student.jia.my_project_spring_boot_261021.repository;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import student.jia.my_project_spring_boot_261021.entity.AuthToken;
import java.util.List;
/**
 * 登入紀錄查詢介面，以 Token 雜湊查詢，資料庫不保存原始 Token。
 * 下方 active 查詢只排除已撤銷與已刪除紀錄；期限仍須由呼叫端驗證。
 */
public interface AuthTokenRepository extends JpaRepository<AuthToken, Long> {

    // 檢查 access token 是否仍存在、未登出、未被 refresh rotation 撤銷。
    @Query(
            value = """
                    SELECT * FROM auth_tokens
                    WHERE access_token_hash = :accessTokenHash
                      AND owner_type = :ownerType
                      AND revoked = 0
                      AND deleted_at IS NULL
                    """,
            nativeQuery = true
    )
    public Optional<AuthToken> findActiveByAccessTokenHashAndOwnerType(
            @Param("accessTokenHash") String accessTokenHash,
            @Param("ownerType") String ownerType
    );

    // refresh/logout 時用 refresh token hash 找目前有效的登入紀錄。
    // ownerType 用來區分 MEMBER / ADMIN，避免不同 table 的登入互相影響。
    @Query(
            value = """
                    SELECT * FROM auth_tokens
                    WHERE refresh_token_hash = :refreshTokenHash
                      AND owner_type = :ownerType
                      AND revoked = 0
                      AND deleted_at IS NULL
                    """,
            nativeQuery = true
    )
    public Optional<AuthToken> findActiveByRefreshTokenHashAndOwnerType(
            @Param("refreshTokenHash") String refreshTokenHash,
            @Param("ownerType") String ownerType
    );
    // 列出指定持有者與撤銷狀態的登入紀錄，供停用會員時逐筆撤銷使用。
    List<AuthToken> findByOwnerTypeAndOwnerIdAndRevoked(
        String ownerType, Long ownerId, Byte revoked);

        
}