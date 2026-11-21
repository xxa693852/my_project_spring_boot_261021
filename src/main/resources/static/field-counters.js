// 只計算明確指定的文字欄位。密碼即使切換成文字顯示也不計數。
(() => {
 const counters = new WeakMap();
 function sensitive(input) {
  return input.type === "password" || /password/i.test([input.name, input.id, input.autocomplete].join(" "));
 }
 function attach(input, limit, tags = false) {
  if (!input || sensitive(input) || input.readOnly) return;
  if (counters.has(input)) { counters.get(input)(); return; }
  const note = document.createElement("p");
  note.className = "field-help character-count";
  note.style.textAlign = "right";
  note.id = (input.id || "field-count-" + document.querySelectorAll(".character-count").length) + "-count";
  input.after(note);
  input.setAttribute("aria-describedby", ((input.getAttribute("aria-describedby") || "") + " " + note.id).trim());
  const update = () => {
   if (tags) {
    const values = input.value.split("\n").map(value => value.trim()).filter(Boolean);
    note.textContent = "標籤 " + values.length + "／5" + (values.length ? " · " + values.map((value,index) => "第 " + (index+1) + " 個：" + value.length + "／16").join("；") : "");
   } else note.textContent = input.value.length + "／" + limit;
  };
  counters.set(input, update);
  input.addEventListener("input", update);
  input.addEventListener("change", update);
  update();
 }
 function refresh(scope = document) {
  for (const input of scope.querySelectorAll("[data-char-limit]")) attach(input, Number(input.dataset.charLimit), input.dataset.countTags === "true");
 }
 window.DndCounters = {attach, refresh};
 document.addEventListener("reset", () => setTimeout(() => refresh(), 0));
 refresh();
})();
