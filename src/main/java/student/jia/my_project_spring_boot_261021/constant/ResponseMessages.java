package student.jia.my_project_spring_boot_261021.constant;

import java.util.HashMap;
import java.util.Map;

// 集中管理 API 訊息；程式用錯誤碼找 message，之後要支援多語系時可以依語系換不同 Map。
public final class ResponseMessages {

    // 00000 區間：成功訊息。
    public static final String CREATE_SUCCESS           = "00000";
    public static final String UPDATE_SUCCESS           = "00001";
    public static final String DELETE_SUCCESS           = "00002";

    // 10000 區間：request validation 相關錯誤。錯誤碼用數字字串，因為 validation annotation 的 message 只能放 String。
    public static final String VALIDATION_FAILED        = "10000"; // validation 大項目錯誤。
    public static final String BOOK_NAME_REQUIRED       = "10001"; // 書名未填。
    public static final String BOOK_PRICE_REQUIRED      = "10002"; // 價格未填。
    public static final String BOOK_PRICE_MIN           = "10003"; // 價格小於允許的最小值。
    public static final String MAIL_ADDRESS_REQUIRED    = "10004"; // email 未填。
    public static final String MAIL_ADDRESS_INVALID     = "10005"; // email 格式錯誤。
    public static final String MAIL_SUBJECT_REQUIRED    = "10006"; // 信件標題未填。
    public static final String MAIL_SUBJECT_MAX         = "10007"; // 信件標題超過長度限制。
    public static final String MAIL_CONTENT_REQUIRED    = "10008"; // 信件內容未填。
    public static final String MAIL_CONTENT_MAX         = "10009"; // 信件內容超過長度限制。
    public static final String MEMBER_NAME_REQUIRED     = "10010"; // 會員姓名未填。
    public static final String MEMBER_NAME_MAX          = "10011"; // 會員姓名超過長度限制。
    public static final String MEMBER_GENDER_REQUIRED   = "10012"; // 會員性別未填。
    public static final String MEMBER_GENDER_INVALID    = "10013"; // 會員性別不合法。
    public static final String MEMBER_ACCOUNT_REQUIRED  = "10014"; // 會員帳號未填。
    public static final String MEMBER_ACCOUNT_MAX       = "10015"; // 會員帳號超過長度限制。
    public static final String MEMBER_EMAIL_INVALID     = "10016"; // 會員 email 格式錯誤。
    public static final String MEMBER_EMAIL_MAX         = "10017"; // 會員 email 超過長度限制。
    public static final String MEMBER_PASSWORD_REQUIRED = "10018"; // 會員密碼未填。
    public static final String MEMBER_PASSWORD_SIZE     = "10019"; // 會員密碼長度不合法。
    public static final String MEMBER_CONFIRM_PASSWORD_REQUIRED = "10020"; // 會員確認密碼未填。
    public static final String MEMBER_CONFIRM_PASSWORD_NOT_MATCH = "10021"; // 會員確認密碼不一致。
    public static final String MEMBER_ACCOUNT_EXISTS    = "10022"; // 會員帳號已存在。
    public static final String REFRESH_TOKEN_REQUIRED = "10023"; // refresh token 未填。
    public static final String MEMBER_ACCOUNT_OR_EMAIL_REQUIRED = "10024"; // 忘記密碼帳號或 email 未填。
    public static final String MEMBER_ACCOUNT_OR_EMAIL_MAX = "10025"; // 忘記密碼帳號或 email 過
    public static final String PASSWORD_RESET_TOKEN_REQUIRED = "10026"; // 重設密碼 token 未填。
    public static final String MEMBER_EMAIL_EXISTS     = "10027"; //會員email已存在。
    // 20000 區間：資料庫寫入相關錯誤。
    public static final String DATABASE_WRITE_FAILED    = "20000";

    // 30000 區間：HTTP request 相關錯誤。
    public static final String HTTP_REQUEST_FAILED      = "30000";
    public static final String METHOD_NOT_ALLOWED       = "30001"; // HTTP method 不支援。
    public static final String NOT_FOUND                = "30002"; // API 路徑不存在。
    public static final String RESOURCE_NOT_FOUND       = "30003"; // 資料不存在。

    public static final String BOOK_NOT_FOUND           = "40001"; // 找不到書籍。
    public static final String MEMBER_NOT_FOUND         = "40002"; // 找不到會員。
    public static final String MEMBER_LOGIN_FAILED      = "40003"; // 會員登入失敗。
    public static final String TOKEN_INVALID            = "40004"; // token 不合法。
    public static final String TOKEN_EXPIRED            = "40005"; // token 已過期。
    public static final String TOKEN_REQUIRED           = "40006"; // token 未填。
    public static final String PASSWORD_RESET_TOKEN_INVALID = "40007"; // 重設密碼 token 不合法或已過期。
    public static final String BOOK_ALREADY_SOLD        = "40008"; // 書籍已賣出。
    // 活動與報名相關錯誤
    public static final String EVENT_NOT_FOUND          = "40009"; //找不到活動
    public static final String EVENT_NOT_OPEN           = "40010"; //活動未開放報名
    public static final String EVENT_REGISTERED_REPEAT  = "40011"; //你已經報名這場活動
    public static final String EVENT_REGISTRATION_DEADLINE_PASSED = "40012"; //活動報名已截止
    public static final String EVENT_FULL               = "40013"; //活動報名人數已滿
    // 50000 區間：外接第三方服務通知相關錯誤。
    public static final String MAIL_SEND_FAILED         = "50000"; // 電子郵件寄送失敗。

    private static final Map<String, String> ZH_TW_MESSAGES = createZhTwMessages();

    private static Map<String, String> createZhTwMessages() {
        Map<String, String> messages = new HashMap<>();
        messages.put(CREATE_SUCCESS,        "新增成功");
        messages.put(UPDATE_SUCCESS,        "修改成功");
        messages.put(DELETE_SUCCESS,        "刪除成功");
        messages.put(VALIDATION_FAILED,     "資料驗證失敗");
        messages.put(BOOK_NAME_REQUIRED,    "書名必填");
        messages.put(BOOK_PRICE_REQUIRED,   "價格必填");
        messages.put(BOOK_PRICE_MIN,        "價格必須大於等於 1");
        messages.put(MAIL_ADDRESS_REQUIRED, "email 必填");
        messages.put(MAIL_ADDRESS_INVALID,  "email 格式錯誤");
        messages.put(MAIL_SUBJECT_REQUIRED, "信件標題必填");
        messages.put(MAIL_SUBJECT_MAX,      "信件標題不可超過 60 個字");
        messages.put(MAIL_CONTENT_REQUIRED, "信件內容必填");
        messages.put(MAIL_CONTENT_MAX,      "信件內容不可超過 1000 個字");
        messages.put(MEMBER_NAME_REQUIRED,  "會員姓名必填");
        messages.put(MEMBER_NAME_MAX,       "會員姓名不可超過 30 個字");
        messages.put(MEMBER_GENDER_REQUIRED, "會員性別必填");
        messages.put(MEMBER_GENDER_INVALID, "會員性別只能是 0、1、2");
        messages.put(MEMBER_ACCOUNT_REQUIRED, "會員帳號必填");
        messages.put(MEMBER_ACCOUNT_MAX,    "會員帳號不可超過 30 個字");
        messages.put(MEMBER_EMAIL_INVALID,  "會員 email 格式錯誤");
        messages.put(MEMBER_EMAIL_MAX,      "會員 email 不可超過 128 個字");
        messages.put(MEMBER_PASSWORD_REQUIRED, "會員密碼必填");
        messages.put(MEMBER_PASSWORD_SIZE,  "會員密碼長度必須是 6 到 12 個字");
        messages.put(MEMBER_CONFIRM_PASSWORD_REQUIRED, "確認密碼必填");
        messages.put(MEMBER_CONFIRM_PASSWORD_NOT_MATCH, "確認密碼不一致");
        messages.put(MEMBER_ACCOUNT_EXISTS, "會員帳號已存在");
        messages.put(REFRESH_TOKEN_REQUIRED, "refresh token 必填");
        messages.put(DATABASE_WRITE_FAILED, "資料寫入失敗");
        messages.put(HTTP_REQUEST_FAILED,   "HTTP 其他相關的錯誤");
        messages.put(METHOD_NOT_ALLOWED,    "HTTP 方法不支援");
        messages.put(NOT_FOUND,             "找不到 API");
        messages.put(BOOK_NOT_FOUND,        "找不到書籍");
        messages.put(MEMBER_NOT_FOUND,      "找不到會員");
        messages.put(MEMBER_LOGIN_FAILED,   "帳號或密碼錯誤");
        messages.put(TOKEN_INVALID,         "token 不合法");
        messages.put(TOKEN_EXPIRED,         "token 已過期，請重新登入");
        messages.put(TOKEN_REQUIRED,        "token 必填");
        messages.put(RESOURCE_NOT_FOUND,    "資料不存在");
        messages.put(BOOK_ALREADY_SOLD,     "書籍已賣出");
        messages.put(MAIL_SEND_FAILED, "電子郵件寄送失敗");
        messages.put(MEMBER_ACCOUNT_OR_EMAIL_REQUIRED, "帳號或 email 必填");
        messages.put(MEMBER_ACCOUNT_OR_EMAIL_MAX, "帳號或 email 不可超過 128 個字");
        messages.put(PASSWORD_RESET_TOKEN_REQUIRED, "重設密碼 token 必填");
        messages.put(PASSWORD_RESET_TOKEN_INVALID, "重設密碼連結無效或已過期");
        messages.put(MEMBER_EMAIL_EXISTS,     "會員email已存在");
        messages.put(EVENT_NOT_FOUND,       "找不到活動");
        messages.put(EVENT_NOT_OPEN,        "活動未開放報名");
        messages.put(EVENT_REGISTERED_REPEAT, "你已經報名這場活動");
        messages.put(EVENT_REGISTRATION_DEADLINE_PASSED, "活動報名已截止");
        messages.put(EVENT_FULL,            "活動報名人數已滿");
        return messages;
    }

    public static String getMessage(String code) {
        return ZH_TW_MESSAGES.get(code);
    }

}