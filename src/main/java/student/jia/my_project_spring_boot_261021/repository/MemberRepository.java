package student.jia.my_project_spring_boot_261021.repository;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import student.jia.my_project_spring_boot_261021.entity.Member;

public interface MemberRepository extends JpaRepository<Member, Long> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            UPDATE Member m SET m.notificationLineUserId = :lineUserId
            WHERE m.id = :id AND m.status = 'ACTIVE'
            """)
    int updateNotificationLineUserId(@Param("id") Long id, @Param("lineUserId") String lineUserId);

    boolean existsByAccount(String account); //帳戶存在

    boolean existsByEmail(String email);  // 透過電子郵件

    boolean existsByEmailAndIdNot(String email, Long id);

    // 找出單一會員By Id
    public Optional<Member> findOneById(@Param("id") long id);


  // 依會員 ID 和狀態查詢
    Optional<Member> findByIdAndStatus(Long id, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Member m WHERE m.id = :id AND m.status = 'ACTIVE'")
    Optional<Member> findActiveByIdForUpdate(@Param("id") Long id);

  //會員忘記密碼用
  Optional<Member> findByEmailAndStatus(String email, String status);  

  Optional<Member> findByAccountAndStatus(String account, String status);

    @Query(
        value ="SELECT * FROM members WHERE line_uid = :lineUid AND status = 1",nativeQuery  = true
    )
    Optional<Member> findOneByLineUidAndStatus(
        @Param("lineUid") String lineUid
    );

}

