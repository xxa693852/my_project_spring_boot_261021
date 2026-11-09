// 調整：密碼表單共用 UTF-8 bytes 檢查；登入與舊密碼保留既有長度規則。
window.DndPasswordRules = {
    message: function (password, isNew) {
        if (isNew && (password.length < 15 || password.length > 72)) {
            return "新密碼長度必須是 15 到 72 個字元。";
        }
        if (new TextEncoder().encode(password).length > 72) {
            return "密碼以 UTF-8 編碼後不可超過 72 bytes；中文通常每字占 3 bytes。";
        }
        return "";
    }
};
