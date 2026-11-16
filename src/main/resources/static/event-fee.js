// 報名費統一顯示新台幣整數，列表、詳情及付款確認共用。
function eventFeeLabel(fee) {
    if (!fee) return "免費";
    return "報名費 NT$" + new Intl.NumberFormat("zh-TW").format(fee) + "／人";
}

function showEventFee(element, fee) {
    element.textContent = eventFeeLabel(fee);
    element.className = "event-fee " + (fee > 0 ? "event-fee-paid" : "event-fee-free");
}

// 藍新需要以 POST 接收加密表單。固定檢查目的地為藍新測試站。
async function openEventPayment(eventId, expectedFee) {
    const query = new URLSearchParams({ expectedFee });
    const response = await DndAuth.request("/registration/events/" + eventId + "/payment-form?" + query, { method: "POST" });
    if (!response.ok) return response;
    const data = await response.json();
    if (data.gatewayUrl !== "https://ccore.newebpay.com/MPG/mpg_gateway") {
        throw new Error("無法確認付款頁網址。");
    }
    const form = document.createElement("form");
    form.method = "POST";
    form.action = data.gatewayUrl;
    const fields = { MerchantID: data.merchantId, Version: data.version, TradeInfo: data.tradeInfo, TradeSha: data.tradeSha };
    for (const [name, value] of Object.entries(fields)) {
        const input = document.createElement("input");
        input.type = "hidden";
        input.name = name;
        input.value = value;
        form.append(input);
    }
    document.body.append(form);
    form.submit();
    return response;
}
