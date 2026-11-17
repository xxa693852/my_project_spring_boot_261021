for (const form of document.querySelectorAll("form[data-search-history]")) {
    const input = form.querySelector('input[type="search"]');
    const suggestions = input.list;
    const key = form.dataset.searchHistory;
    const maxLength = input.maxLength;
    let history = [];

    function populateSuggestions() {
        suggestions.replaceChildren();
        for (const term of history) {
            const option = document.createElement("option");
            option.value = term;
            suggestions.append(option);
        }
    }

    try {
        const saved = JSON.parse(localStorage.getItem(key) || "[]");
        if (Array.isArray(saved)) {
            history = saved.filter(term => typeof term === "string" && term.trim()
                && (maxLength < 0 || term.length <= maxLength)).slice(0, 10);
        }
    } catch {
        // Search remains available when local storage is unavailable.
    }
    populateSuggestions();

    form.addEventListener("submit", () => {
        const term = input.value.trim();
        if (!term) return;
        history = [term, ...history.filter(saved => saved !== term)].slice(0, 10);
        populateSuggestions();
        try {
            localStorage.setItem(key, JSON.stringify(history));
        } catch {
            // Keep this page's suggestions available for the current visit.
        }
    });
}
