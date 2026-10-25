(() => {
  "use strict";
  const grid = document.querySelector("#provider-grid");
  const search = document.querySelector("#provider-search");
  const empty = document.querySelector("#empty-state");
  const total = document.querySelector("#provider-count");
  const filterButtons = Array.from(document.querySelectorAll("[data-filter]"));
  const state = { filter: "all", query: "", providers: [] };
  const T = key => typeof window.siteT === "function" ? window.siteT(key) : key;
  const escapeHtml = value => String(value ?? "").replace(/[&<>\"']/g, ch => ({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[ch]));
  const addUri = provider => "generative://provider?" + new URLSearchParams({
    name: provider.name,
    url: provider.api,
    provider_type: provider.appType || "chat-completions",
    auth_type: "platform",
    icon_url: provider.logo || ""
  }).toString();
  const logo = provider => provider.logo
    ? '<img class="provider-logo" src="' + escapeHtml(provider.logo) + '" alt="" loading="lazy" referrerpolicy="no-referrer" onerror="this.hidden=true;this.nextElementSibling.hidden=false">'
      + '<span class="provider-logo-fallback" hidden>' + escapeHtml((provider.name || "?").slice(0, 2).toUpperCase()) + '</span>'
    : '<span class="provider-logo-fallback">' + escapeHtml((provider.name || "?").slice(0, 2).toUpperCase()) + '</span>';

  function card(provider) {
    const canAdd = Boolean(provider.canAdd && provider.api);
    const endpoint = provider.api
      ? '<div class="endpoint-block"><span>' + escapeHtml(T("API ENDPOINT")) + '</span><code title="' + escapeHtml(provider.api) + '">' + escapeHtml(provider.api) + '</code></div>'
      : '<div class="endpoint-block endpoint-missing"><span>' + escapeHtml(T("API ENDPOINT")) + '</span><code>' + escapeHtml(T("See provider documentation")) + '</code></div>';
    const docs = provider.docs
      ? '<a class="provider-doc-link" href="' + escapeHtml(provider.docs) + '" target="_blank" rel="noopener noreferrer">' + escapeHtml(T("Official docs")) + ' <span>↗</span></a>'
      : '';
    const add = canAdd
      ? '<a class="button button-add" href="' + escapeHtml(addUri(provider)) + '" aria-label="' + escapeHtml(T("Add {name} to Generative").replace("{name}", provider.name)) + '">' + escapeHtml(T("Add Provider")) + ' <span>↗</span></a>'
      : '<span class="button button-add button-add-disabled" title="' + escapeHtml(T("This provider needs a different or manual setup flow")) + '">' + escapeHtml(T("Manual setup")) + '</span>';
    const badge = canAdd
      ? '<span class="provider-tag tag-api">' + escapeHtml(T("One-tap setup")) + '</span>'
      : '<span class="provider-tag">' + escapeHtml(T("Manual setup")) + '</span>';
    return '<article class="provider-card"><div class="provider-card-top"><div class="provider-branding">' + logo(provider) + '<div class="provider-brand-copy"><h3>' + escapeHtml(provider.name) + '</h3><span>' + Number(provider.modelCount || 0).toLocaleString() + ' ' + escapeHtml(T("models listed")) + '</span></div></div>' + badge + '</div>' + endpoint + '<div class="provider-card-footer"><div>' + docs + '</div>' + add + '</div></article>';
  }

  function render() {
    const query = state.query.trim().toLowerCase();
    const matches = state.providers.filter(p => {
      const oneTap = Boolean(p.canAdd && p.api);
      return (state.filter === "all" || (state.filter === "one-tap" ? oneTap : !oneTap))
        && (!query || [p.name, p.id, p.api, p.docs, p.npm].join(" ").toLowerCase().includes(query));
    });
    grid.innerHTML = matches.map(card).join("");
    grid.hidden = matches.length === 0;
    empty.hidden = matches.length !== 0;
    total.textContent = state.providers.length.toLocaleString();
    document.querySelector("#count-all").textContent = state.providers.length.toLocaleString();
    document.querySelector("#count-one-tap").textContent = state.providers.filter(p => p.canAdd && p.api).length.toLocaleString();
    document.querySelector("#provider-one-tap-count").textContent = state.providers.filter(p => p.canAdd && p.api).length.toLocaleString();
    document.querySelector("#count-manual").textContent = state.providers.filter(p => !(p.canAdd && p.api)).length.toLocaleString();
    document.querySelector("#result-count").textContent = matches.length.toLocaleString();
  }

  filterButtons.forEach(button => button.addEventListener("click", () => {
    state.filter = button.dataset.filter;
    filterButtons.forEach(item => {
      const active = item === button;
      item.classList.toggle("active", active);
      item.setAttribute("aria-pressed", String(active));
    });
    render();
  }));
  search.addEventListener("input", () => { state.query = search.value; render(); });
  document.querySelector("#clear-filters").addEventListener("click", () => {
    state.filter = "all"; state.query = ""; search.value = "";
    filterButtons.forEach(item => {
      const active = item.dataset.filter === "all";
      item.classList.toggle("active", active);
      item.setAttribute("aria-pressed", String(active));
    });
    render(); search.focus();
  });
  document.addEventListener("keydown", event => {
    if (event.key === "/" && !["INPUT", "TEXTAREA"].includes(document.activeElement.tagName)) { event.preventDefault(); search.focus(); }
    if (event.key === "Escape" && document.activeElement === search) { search.value = ""; state.query = ""; render(); search.blur(); }
  });

  fetch("./providers-data.json", { cache: "no-cache" })
    .then(response => { if (!response.ok) throw new Error("Could not load provider data"); return response.json(); })
    .then(data => {
      state.providers = Array.isArray(data.providers) ? data.providers : [];
      if (!state.providers.length) throw new Error("Provider data is empty");
      const stamp = document.querySelector("#provider-updated");
      if (stamp && data.updatedAt) stamp.textContent = new Date(data.updatedAt).toLocaleDateString(undefined, {year:"numeric",month:"short",day:"numeric"});
      render();
    })
    .catch(() => {
      grid.innerHTML = '<div class="provider-loading">' + escapeHtml(T("The provider catalog could not be loaded. Please refresh this page or open")) + ' <a href="https://models.dev/providers/">Models.dev</a>.</div>';
      total.textContent = "—";
    });
})();
