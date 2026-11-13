const taipeiDate = new Intl.DateTimeFormat("sv-SE", {
    timeZone: "Asia/Taipei", year: "numeric", month: "2-digit", day: "2-digit"
});

function dateOptions(select, start, end, selected, unit) {
    select.replaceChildren();
    for (let value = start; value <= end; value++) {
        select.add(new Option(value + unit, String(value)));
    }
    select.value = String(Math.min(end, Math.max(start, selected)));
}

function createDatePicker(input, label) {
    const key = input.id;
    const group = document.createElement("div");
    group.className = "event-date-picker";
    group.setAttribute("role", "group");
    group.setAttribute("aria-label", label.textContent);
    const year = document.createElement("select");
    const month = document.createElement("select");
    const day = document.createElement("select");
    const time = document.createElement("input");
    time.type = "time";
    time.step = "1";
    time.required = true;
    time.id = key + "-time";
    const error = input.getAttribute("aria-describedby");
    if (error) time.setAttribute("aria-describedby", error);
    const controls = [year, month, day, time];
    const names = ["年", "月", "日", "時間"];
    for (let i = 0; i < controls.length; i++) {
        controls[i].setAttribute("aria-label", label.textContent + " " + names[i]);
        group.append(controls[i]);
    }
    input.after(group);

    let lastYear;
    let selectedYear;

    function update(today) {
        const [firstYear, firstMonth, firstDay] = today.split("-").map(Number);
        if (year.value === "more") lastYear = Math.min(9999, lastYear + 5);
        else if (year.value) selectedYear = Number(year.value);
        lastYear = Math.max(lastYear || 0, firstYear + 4);
        dateOptions(year, firstYear, lastYear, selectedYear || firstYear, " 年");
        selectedYear = Number(year.value);
        if (lastYear < 9999) year.add(new Option("顯示更多年份", "more"));

        const earliestYear = selectedYear === firstYear;
        dateOptions(month, earliestYear ? firstMonth : 1, 12, Number(month.value), " 月");
        const selectedMonth = Number(month.value);
        const earliestMonth = earliestYear && selectedMonth === firstMonth;
        const lastDay = new Date(Date.UTC(selectedYear, selectedMonth, 0)).getUTCDate();
        dateOptions(day, earliestMonth ? firstDay : 1, lastDay, Number(day.value), " 日");
        sync();
    }

    function sync() {
        const date = [year.value, month.value.padStart(2, "0"), day.value.padStart(2, "0")].join("-");
        input.value = time.value ? date + "T" + time.value : "";
        return date;
    }

    for (const control of controls) {
        control.addEventListener("change", () => update(taipeiDate.format(new Date())));
    }

    return {
        time, update, sync,
        setMode(isNew) {
            input.hidden = isNew;
            input.disabled = isNew;
            group.hidden = !isNew;
            time.disabled = !isNew;
            label.htmlFor = isNew ? time.id : key;
            if (isNew) {
                year.replaceChildren();
                month.replaceChildren();
                day.replaceChildren();
                time.value = "";
                selectedYear = undefined;
                lastYear = undefined;
            }
        }
    };
}

