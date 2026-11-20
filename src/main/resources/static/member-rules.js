// 註冊與會員設定共用提示；後端仍獨立驗證，登入頁不套用新帳號限制。
window.DndMemberRules = {
    name: function (name) {
        if (!name.trim()) return "姓名或暱稱必填";
        if (name.length > 30) return "姓名或暱稱最多 30 字元";
        return "";
    },
    account: function (account) {
        if (!account.trim()) return "會員帳號必填";
        if (account.length < 4 || account.length > 20) return "帳號長度必須是 4～20 字元";
        if (!/^[A-Za-z0-9_.-]+$/.test(account)) return "帳號僅可使用英文字母、數字、底線（_）、連字號（-）與半形句點（.）";
        return "";
    }
};
