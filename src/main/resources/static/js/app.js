/* Progressive enhancement only: every form still works without JavaScript.
   Marks submission buttons busy so long-running actions (bounded investigation,
   review, assistant reply) give immediate, accessible feedback instead of a
   silently pending page. */
(function () {
    "use strict";

    document.addEventListener("submit", function (event) {
        var form = event.target;
        if (!(form instanceof HTMLFormElement)) {
            return;
        }
        var submitter = event.submitter || form.querySelector("[data-loading]");
        if (!(submitter instanceof HTMLElement) || submitter.disabled) {
            return;
        }
        submitter.disabled = true;
        submitter.setAttribute("aria-busy", "true");
        var label = submitter.getAttribute("data-loading-label") || "Working…";
        if (submitter instanceof HTMLButtonElement || submitter instanceof HTMLInputElement) {
            if ("value" in submitter && submitter.tagName === "INPUT") {
                submitter.value = label;
            } else {
                submitter.textContent = label;
            }
        }
    }, true);
})();
