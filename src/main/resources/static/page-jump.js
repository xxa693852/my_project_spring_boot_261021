function renderPageJump(nav, page, total, goToPage) {
    const numbers = nav.querySelector(".page-numbers");
    const jump = nav.querySelector(".page-jump");
    const input = jump.querySelector("input");
    numbers.replaceChildren();
    const visible = new Set([1, total]);
    for (let number = Math.max(1, page - 2); number <= Math.min(total, page + 2); number++) {
        visible.add(number);
    }
    let prior = 0;
    for (const number of [...visible].sort((a, b) => a - b)) {
        if (number - prior > 1) {
            const gap = document.createElement("span");
            gap.textContent = "…";
            numbers.append(gap);
        }
        const button = document.createElement("button");
        button.type = "button";
        button.textContent = number;
        button.setAttribute("aria-label", "第 " + number + " 頁");
        if (number === page) {
            button.setAttribute("aria-current", "page");
        }
        button.onclick = () => { if (number !== page) goToPage(number); };
        numbers.append(button);
        prior = number;
    }
    input.max = total;
    input.value = "";
    jump.onsubmit = event => {
        event.preventDefault();
        const target = input.valueAsNumber;
        if (Number.isInteger(target) && target >= 1 && target <= total && target !== page) {
            goToPage(target);
        }
    };
}
