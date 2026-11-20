package student.jia.my_project_spring_boot_261021.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import student.jia.my_project_spring_boot_261021.entity.Payment;

import java.util.Optional;

/**
 * 付款訂單查詢介面，連接商店訂單編號、報名與活動資料。
 */
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // jpaRepository 會自動實作這個方法，透過 merchantOrderNo 查詢 Payment。
    Optional<Payment> findByMerchantOrderNo(String merchantOrderNo);

    // 以付款 ID 由大到小取得指定報名的最新付款紀錄；沒有付款時回傳空 Optional。
    Optional<Payment> findFirstByRegistrationIdOrderByIdDesc(Long registrationId);

    @org.springframework.data.jpa.repository.Query("""
            SELECT r.eventId FROM Registration r, Payment p
            WHERE p.registrationId = r.id AND p.merchantOrderNo = :orderNo
            """)
    // 由商店訂單編號查出所屬活動 ID，供後續先鎖定活動再處理付款。
    Optional<Long> findEventIdByMerchantOrderNo(@org.springframework.data.repository.query.Param("orderNo") String orderNo);

    // 收款後與取消操作都先鎖活動，再重新查付款，採用相同鎖定順序。
    @org.springframework.data.jpa.repository.Query("SELECT p FROM Payment p WHERE p.registrationId IN "
            + "(SELECT r.id FROM Registration r WHERE r.eventId = :eventId) ORDER BY p.id DESC")
    java.util.List<Payment> findForEvent(@org.springframework.data.repository.query.Param("eventId") Long eventId);
}