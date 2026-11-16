package student.jia.my_project_spring_boot_261021.constant;

public final class PaymentStatus {

    // 付款紀錄已建立，但尚未產生或送出藍新付款資料。
    public static final String INIT = "INIT";

    // 付款資料已送出，等待使用者完成付款或等待藍新回呼結果。
    public static final String PENDING = "PENDING";

    // 藍新確認付款成功。
    public static final String PAID = "PAID";

    // 藍新回傳付款失敗，或系統確認付款流程失敗。
    public static final String FAILED = "FAILED";

    // 付款流程已取消，例如使用者未完成付款或訂單取消。
    public static final String CANCELLED = "CANCELLED";

    // 付款已完成退款；後續串接退款 API 時使用。
    public static final String REFUNDED = "REFUNDED";

    public static final String REFUND_PENDING = "REFUND_PENDING";
    public static final String REVIEW_REQUIRED = "REVIEW_REQUIRED";

    private PaymentStatus() {
    }
}