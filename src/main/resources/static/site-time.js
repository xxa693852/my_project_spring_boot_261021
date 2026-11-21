// API 的無時區日期代表台北時間；僅統一顯示，不更動送往後端的值。
window.DndTime = {
    format(value, fallback = "未設定") {
        if (!value) return fallback;
        const input = String(value);
        const local = /^(\d{4}-\d{2}-\d{2})T(\d{2}:\d{2})(?::(\d{2})(?:\.\d+)?)?$/.exec(input);
        if (local) return local[1] + " " + local[2] + ":" + (local[3] || "00");
        // 有 Z 或明確時差的時間，轉為台北時間，避免依瀏覽器所在地顯示。
        if (!/(Z|[+-]\d{2}:\d{2})$/.test(input)) return fallback;
        const date = new Date(input);
        if (Number.isNaN(date.getTime())) return fallback;
        const parts = new Intl.DateTimeFormat("sv-SE", {
            timeZone: "Asia/Taipei", year: "numeric", month: "2-digit", day: "2-digit",
            hour: "2-digit", minute: "2-digit", second: "2-digit", hourCycle: "h23"
        }).formatToParts(date);
        const values = {};
        for (const part of parts) values[part.type] = part.value;
        return values.year + "-" + values.month + "-" + values.day + " " + values.hour + ":" + values.minute + ":" + values.second;
    }
};
