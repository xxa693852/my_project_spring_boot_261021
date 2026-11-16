package student.jia.my_project_spring_boot_261021.service;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import student.jia.my_project_spring_boot_261021.entity.Payment;

@Service
public class NewebPayService {
    private static final String GATEWAY = "https://ccore.newebpay.com/MPG/mpg_gateway";
    private static final String VERSION = "2.0";
    private final RegistrationService registrations;
    private final PaymentLifecycle lifecycle;
    private final String merchantId;
    private final String key;
    private final String iv;
    private final String baseUrl;

    public NewebPayService(RegistrationService registrations, PaymentLifecycle lifecycle,
            @Value("${app.newebpay.merchant-id:}") String merchantId,
            @Value("${app.newebpay.hash-key:}") String key,
            @Value("${app.newebpay.hash-iv:}") String iv,
            @Value("${app.newebpay.callback-base-url:}") String baseUrl) {
        this.registrations = registrations;
        this.lifecycle = lifecycle;
        this.merchantId = merchantId;
        this.key = key;
        this.iv = iv;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    public boolean isReady() {
        try {
            URI callback = URI.create(baseUrl);
            return merchantId.matches("[A-Za-z0-9]{1,32}")
                    && key.getBytes(StandardCharsets.UTF_8).length == 32
                    && iv.getBytes(StandardCharsets.UTF_8).length == 16
                    && "https".equals(callback.getScheme()) && callback.getHost() != null
                    && callback.getRawQuery() == null && callback.getRawFragment() == null;
        } catch (IllegalArgumentException invalidUrl) {
            return false;
        }
    }

    // 商店金額由資料庫的付款快照決定，表單只傳給藍新測試站。
    public PaymentForm createForm(Long memberId, Long eventId, Integer expectedFee) {
        if (!isReady()) {
            throw new IllegalArgumentException("藍新測試環境尚未設定。");
        }
        RegistrationService.PaymentReservation reservation = registrations.reserveForPayment(memberId, eventId, merchantId, expectedFee);
        Payment payment = reservation.payment();
        long seconds = Duration.between(PaymentLifecycle.now(), reservation.expiresAt()).toSeconds();
        if (seconds <= 0) {
            throw new IllegalArgumentException("付款保留已結束。");
        }
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("MerchantID", payment.getMerchantId());
        parameters.put("MerchantOrderNo", payment.getMerchantOrderNo());
        parameters.put("Amt", payment.getAmount().toString());
        parameters.put("ItemDesc", "D&D 活動報名");
        parameters.put("TimeStamp", Long.toString(Instant.now().getEpochSecond()));
        parameters.put("Version", VERSION);
        parameters.put("RespondType", "String");
        // 本地保留期限仍由後端判斷。藍新信用卡付款頁支援 60 至 900 秒。
        parameters.put("TradeLimit", Long.toString(Math.clamp(seconds, 60, 900)));
        parameters.put("CREDIT", "1");
        parameters.put("InstFlag", "0");
        parameters.put("WEBATM", "0");
        parameters.put("VACC", "0");
        parameters.put("CVS", "0");
        parameters.put("BARCODE", "0");
        parameters.put("NotifyURL", baseUrl + "/payments/newebpay/notify");
        parameters.put("ReturnURL", baseUrl + "/payments/newebpay/return");
        parameters.put("ClientBackURL", baseUrl + "/my-registrations.html");

        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            if (!body.isEmpty()) {
                body.append('&');
            }
            body.append(entry.getKey()).append('=')
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        String tradeInfo = HexFormat.of().formatHex(crypt(Cipher.ENCRYPT_MODE,
                body.toString().getBytes(StandardCharsets.UTF_8)));
        return new PaymentForm(GATEWAY, payment.getMerchantId(), VERSION, tradeInfo, digest(tradeInfo));
    }

    // 先驗證摘要才解密。付款結果取自已驗證的 TradeInfo。
    public void handleNotify(String tradeInfo, String tradeSha) {
        if (!isReady() || tradeInfo == null || tradeSha == null
                || tradeInfo.length() > 16000 || !tradeSha.matches("[A-Fa-f0-9]{64}")
                || !MessageDigest.isEqual(HexFormat.of().parseHex(digest(tradeInfo)),
                        HexFormat.of().parseHex(tradeSha))) {
            throw new IllegalArgumentException("付款通知驗證失敗。");
        }
        String decoded = new String(decryptNotice(tradeInfo),
                StandardCharsets.UTF_8);
        Map<String, String> fields = new LinkedHashMap<>();
        for (String pair : decoded.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2) {
                String name = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
                String value = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
                if (fields.putIfAbsent(name, value) != null) {
                    throw new IllegalArgumentException("付款通知含有重複欄位。");
                }
            }
        }
        String orderNo = fields.getOrDefault("MerchantOrderNo", "");
        String tradeNo = fields.getOrDefault("TradeNo", "");
        String code = fields.getOrDefault("RespondCode", "");
        String status = fields.getOrDefault("Status", "");
        if (!merchantId.equals(fields.get("MerchantID")) || !orderNo.matches("[A-Za-z0-9_]{1,30}")
                || status.isBlank() || code.length() > 32) {
            throw new IllegalArgumentException("付款通知缺少有效的訂單資料。");
        }
        boolean success = "SUCCESS".equals(status);
        if (success && (!"CREDIT".equals(fields.get("PaymentType")) || !"00".equals(code) || !tradeNo.matches("[A-Za-z0-9_-]{1,64}"))) {
            throw new IllegalArgumentException("付款通知缺少信用卡授權結果。");
        }
        int amount = Integer.parseInt(fields.get("Amt"));
        lifecycle.applyNotice(orderNo, merchantId, amount, tradeNo, code, success);
    }

    // 藍新回呼可能使用 32 位元組補位，先解密原始區塊再驗證並移除補位。
    private byte[] decryptNotice(String tradeInfo) {
        byte[] plain = crypt(Cipher.DECRYPT_MODE, HexFormat.of().parseHex(tradeInfo));
        if (plain.length == 0) {
            throw new IllegalArgumentException("付款通知缺少解密資料。");
        }
        int padding = Byte.toUnsignedInt(plain[plain.length - 1]);
        if (padding < 1 || padding > 32 || padding > plain.length) {
            throw new IllegalArgumentException("付款通知的補位格式不正確。");
        }
        for (int index = plain.length - padding; index < plain.length; index++) {
            if (Byte.toUnsignedInt(plain[index]) != padding) {
                throw new IllegalArgumentException("付款通知的補位格式不正確。");
            }
        }
        return Arrays.copyOf(plain, plain.length - padding);
    }

    private byte[] crypt(int mode, byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance(mode == Cipher.DECRYPT_MODE ? "AES/CBC/NoPadding" : "AES/CBC/PKCS5Padding");
            cipher.init(mode, new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES"),
                    new IvParameterSpec(iv.getBytes(StandardCharsets.UTF_8)));
            return cipher.doFinal(data);
        } catch (GeneralSecurityException failure) {
            throw new IllegalArgumentException("無法處理藍新交易資料。", failure);
        }
    }

    private String digest(String tradeInfo) {
        try {
            String source = "HashKey=" + key + "&" + tradeInfo + "&HashIV=" + iv;
            return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("無法產生付款通知摘要。", failure);
        }
    }

    public record PaymentForm(String gatewayUrl, String merchantId, String version,
            String tradeInfo, String tradeSha) {}
}
