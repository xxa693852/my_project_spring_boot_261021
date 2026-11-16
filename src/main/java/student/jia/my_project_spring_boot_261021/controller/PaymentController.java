package student.jia.my_project_spring_boot_261021.controller;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;
import student.jia.my_project_spring_boot_261021.interceptor.AuthInterceptor;
import student.jia.my_project_spring_boot_261021.service.NewebPayService;
import student.jia.my_project_spring_boot_261021.service.PaymentLifecycle;

@RestController
public class PaymentController {
    private final NewebPayService newebPay;
    private final PaymentLifecycle payments;

    public PaymentController(NewebPayService newebPay, PaymentLifecycle payments) {
        this.newebPay = newebPay;
        this.payments = payments;
    }

    // 放在既有登入攔截路徑，會員 ID 取自登入狀態。
    @PostMapping("/registration/events/{eventId}/payment-form")
    public NewebPayService.PaymentForm createForm(
            @RequestAttribute(AuthInterceptor.AUTH_MEMBER_ID_ATTRIBUTE) Long memberId,
            @PathVariable Long eventId, @RequestParam Integer expectedFee) {
        return newebPay.createForm(memberId, eventId, expectedFee);
    }

    // 伺服器間通知由摘要驗證。瀏覽器返回頁只導回報名紀錄。
    @PostMapping(value = "/payments/newebpay/notify", produces = "text/plain")
    public String notify(@RequestParam("TradeInfo") String tradeInfo, @RequestParam("TradeSha") String tradeSha) {
        newebPay.handleNotify(tradeInfo, tradeSha);
        return "SUCCESS";
    }

    @RequestMapping(value = "/payments/newebpay/return", method = {RequestMethod.GET, RequestMethod.POST})
    public RedirectView returnFromPayment() {
        RedirectView view = new RedirectView("/my-registrations.html");
        view.setStatusCode(org.springframework.http.HttpStatus.SEE_OTHER);
        return view;
    }

    @GetMapping("/admin/events/{eventId}/payments")
    public List<PaymentLifecycle.PaymentRecord> list(
            @RequestAttribute(AuthInterceptor.AUTH_MEMBER_ID_ATTRIBUTE) Long memberId,
            @PathVariable Long eventId) {
        return payments.list(memberId, eventId);
    }

    @PostMapping("/admin/events/{eventId}/payments/{paymentId}/refund")
    public void refund(@RequestAttribute(AuthInterceptor.AUTH_MEMBER_ID_ATTRIBUTE) Long memberId,
            @PathVariable Long eventId, @PathVariable Long paymentId, @Valid @RequestBody RefundRequest request) {
        payments.recordRefund(memberId, eventId, paymentId, request.note().strip());
    }

    public record RefundRequest(@NotBlank @Size(max = 255) String note) {}
}
