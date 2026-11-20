// 活動表單共用驗證；API 的 Request 與 Service 仍會獨立檢查，不能只信任瀏覽器。
window.DndEventRules = {
    validate: function (body, isNew, now = Date.now()) {
        const errors = {};
        const textFields = [
            ["title", "活動名稱", 100], ["description", "活動介紹", 5000],
            ["hostName", "主持人名稱", 50], ["location", "地點", 255]
        ];
        for (const [key, label, max] of textFields) {
            const value = body[key];
            if (typeof value !== "string" || !value.trim()) errors[key] = label + "不能空白";
            else if (value.length > max) errors[key] = label + "最多 " + max + " 字元";
            else if (key !== "description" && /[\r\n]/.test(value)) errors[key] = label + "不能包含換行";
        }
        if (body.capacity == null) errors.capacity = "名額上限不能空白";
        else if (!Number.isInteger(body.capacity) || body.capacity < 1 || body.capacity > 50) {
            errors.capacity = "名額上限必須是 1～50 人的整數";
        }
        if (body.registrationFee != null && (!Number.isInteger(body.registrationFee) || body.registrationFee < 0 || body.registrationFee > 2147483647)) {
            errors.registrationFee = "報名費必須是 0～2147483647 元的整數";
        }
        if (body.coverTheme != null && !["tavern", "forest", "dungeon", "castle"].includes(body.coverTheme)) errors.coverTheme = "請選擇有效的預設封面";
        if (body.tags != null) {
            if (!Array.isArray(body.tags) || body.tags.length > 5) errors.tags = "最多可填寫 5 個自訂標籤";
            else for (const tag of body.tags) {
                if (typeof tag !== "string" || !tag.trim()) { errors.tags = "自訂標籤不能空白"; break; }
                if (tag.length > 16) { errors.tags = "每個自訂標籤最多 16 字元"; break; }
                if (/[\r\n]/.test(tag)) { errors.tags = "自訂標籤不能包含換行"; break; }
            }
        }
        const times = {};
        for (const [key, label] of [["startAt", "開始時間"], ["endAt", "結束時間"], ["registrationDeadline", "報名截止時間"]]) {
            const value = body[key];
            if (!value) { errors[key] = label + "不能空白"; continue; }
            // 先檢查實際年月日，避免 JavaScript 把 2 月 30 日自動轉成 3 月。
            const date = new Date(value + "Z");
            if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$/.test(value) || !Number.isFinite(date.getTime()) || date.toISOString().slice(0, 19) !== value) {
                errors[key] = "日期時間請使用 yyyy-MM-dd'T'HH:mm:ss 格式";
            } else if (Number(value.slice(0, 4)) < 1000) errors[key] = "活動日期年份必須介於 1000～9999 年";
            else times[key] = Date.parse(value + "+08:00");
        }
        if (!Object.keys(errors).some(key => ["startAt", "endAt", "registrationDeadline"].includes(key))) {
            if (isNew && times.registrationDeadline <= now) errors.registrationDeadline = "活動報名已截止";
            if (!isNew && times.startAt <= now) errors.startAt = "活動開始時間必須晚於現在";
            if (times.registrationDeadline >= times.startAt) errors.registrationDeadline = "報名截止時間必須早於活動開始時間";
            if (times.startAt >= times.endAt) errors.endAt = "活動開始時間必須早於結束時間";
        }
        return errors;
    }
};
