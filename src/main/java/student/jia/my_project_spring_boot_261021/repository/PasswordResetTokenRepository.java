package student.jia.my_project_spring_boot_261021.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import student.jia.my_project_spring_boot_261021.entity.PasswordResetToken;

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

    public Optional<PasswordResetToken> findValidByTokenHash(
            @Param("tokenHash") String tokenHash,
            @Param("now") LocalDateTime now
    );

}