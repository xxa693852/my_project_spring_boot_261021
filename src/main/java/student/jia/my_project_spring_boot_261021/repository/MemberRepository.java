package student.jia.my_project_spring_boot_261021.repository;
import java.util.Optional;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import student.jia.my_project_spring_boot_261021.entity.Member;

public interface MemberRepository extends JpaRepository<Member, Long> {
    boolean existsByAccount(String account); //帳戶存在

    boolean existsByEmail(String email);  // 透過電子郵件

    boolean existsByEmailAndIdNot(String email, Long id);

    // 找出單一會員By Id
    public Optional<Member> findOneById(@Param("id") long id);


  // 依會員 ID 和狀態查詢
    Optional<Member> findByIdAndStatus(Long id, String status);

    
  Optional<Member> findByAccountAndStatus(String account, String status);

}

