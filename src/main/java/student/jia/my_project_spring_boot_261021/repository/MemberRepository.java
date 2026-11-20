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

/**
 * 會員資料存取介面，由 Spring Data JPA 依方法名稱或 @Query 產生查詢。
 * Optional 代表可能查無資料；exists 開頭的方法回傳是否存在，並不回傳會員物件。
 */
public interface MemberRepository extends JpaRepository<Member, Long> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            UPDATE Member m SET m.notificationLineUserId = :lineUserId
            WHERE m.id = :id AND m.status = 'ACTIVE'
            """)
    // 只更新啟用會員的通知用 LINE ID；回傳受影響筆數，0 代表沒有符合條件的會員。
    int updateNotificationLineUserId(@Param("id") Long id, @Param("lineUserId") String lineUserId);

    // 依帳號、姓名或信箱包含關鍵字搜尋，回傳分頁結果。
    org.springframework.data.domain.Page<Member> findByAccountContainingOrNameContainingOrEmailContaining(
            String account, String name, String email, org.springframework.data.domain.Pageable pageable);

    boolean existsByAccount(String account); //帳戶存在

    boolean existsByEmail(String email);  // 透過電子郵件

    // 判斷信箱是否被其他會員使用；排除目前修改資料的會員自己。
    boolean existsByEmailAndIdNot(String email, Long id);

    // 找出單一會員By Id
    public Optional<Member> findOneById(@Param("id") long id);


  // 依會員 ID 和狀態查詢
    Optional<Member> findByIdAndStatus(Long id, String status);

    // 查詢並鎖定啟用會員，讓同一會員的並行修改或報名依序執行。
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Member m WHERE m.id = :id AND m.status = 'ACTIVE'")
    Optional<Member> findActiveByIdForUpdate(@Param("id") Long id);

  //會員忘記密碼用
  Optional<Member> findByEmailAndStatus(String email, String status);  

  // 登入用帳號與狀態查詢；只找會員資料，密碼比對仍由 Service 負責。
  Optional<Member> findByAccountAndStatus(String account, String status);

    @Query(
        value ="SELECT * FROM members WHERE line_uid = :lineUid AND status = 1",nativeQuery  = true
    )
    Optional<Member> findOneByLineUidAndStatus(
        @Param("lineUid") String lineUid
    );

}

