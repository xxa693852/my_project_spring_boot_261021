package student.jia.my_project_spring_boot_261021.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import student.jia.my_project_spring_boot_261021.entity.PasswordResetToken;

/**
 * 密碼重設 Token 查詢與作廢介面；使用雜湊比對，不以原始 Token 查詢。
 */
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    @Modifying
    @Query(
        value = """
                UPDATE password_reset_tokens
                SET used = 1,
                deleted_at = :now,
                updated_at = :now
                WHERE member_id = :memberId
                AND used = 0
                AND deleted_at IS NULL
                """,
        nativeQuery = true
    )
    // 將會員尚未使用且未刪除的舊 Token 標為已使用；@Modifying 表示執行更新而非查詢。
    public void revokeActiveByMemberId(
            @Param("memberId") Long memberId,
            @Param("now") LocalDateTime now
    );


    @Query(
        value ="""
                SELECT * FROM password_reset_tokens
                WHERE token_hash = :tokenHash
                AND used = 0
                AND expires_at > :now
                AND deleted_at IS NULL                   
        """,
        nativeQuery = true             
    )

    // 只回傳雜湊相符、未使用、未刪除且尚未過期的重設 Token。
    public Optional<PasswordResetToken> findValidByTokenHash(
            @Param("tokenHash") String tokenHash,
            @Param("now") LocalDateTime now
    );

}