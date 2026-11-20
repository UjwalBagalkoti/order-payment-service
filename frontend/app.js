(() => {
  const params = new URLSearchParams(location.search);
  const API = (params.get("api") || window.API_BASE || "").replace(/\/$/, "");
  const $ = (s) => document.querySelector(s);

  const state = { token: safeGet("token"), me: null, keys: {} };

  function safeGet(k) { try { return localStorage.getItem(k); } catch { return null; } }
  function safeSet(k, v) { try { v == null ? localStorage.removeItem(k) : localStorage.setItem(k, v); } catch { /* ignore */ } }

  // Build DOM with textContent only, so API data can never inject markup.
  function el(tag, props = {}, ...kids) {
    const n = document.createElement(tag);
    for (const [k, v] of Object.entries(props)) {
      if (k === "class") n.className = v;
      else if (k.startsWith("on")) n.addEventListener(k.slice(2), v);
      else n.setAttribute(k, v);
    }
    for (const kid of kids) n.append(kid);
    return n;
  }

  const money = (v) => "₹" + Number(v).toFixed(2);
  const uuid = () => (crypto.randomUUID ? crypto.randomUUID() : String(Date.now()) + Math.random().toString(16).slice(2));

  function log(method, path, status, note = "") {
    const line = `${new Date().toLocaleTimeString()}  ${method.padEnd(6)} ${path}  →  ${status || "ERR"} ${note}\n`;
    const box = $("#log");
    box.textContent = line + box.textContent.slice(0, 6000);
  }

  function banner(msg) {
    const b = $("#banner");
    b.hidden = !msg;
    b.textContent = msg || "";
  }

  async function rawFetch(path, { method = "GET", body, headers = {} } = {}) {
    const h = { ...headers };
    if (body !== undefined) h["Content-Type"] = "application/json";
    if (state.token) h.Authorization = "Bearer " + state.token;
    // Render's free tier sleeps when idle; tell the visitor why the first call is slow.
    const slow = setTimeout(() => banner("Waking up the API (free hosting sleeps when idle, this can take ~50s)…"), 3000);
    try {
      const res = await fetch(API + path, { method, headers: h, body: body === undefined ? undefined : JSON.stringify(body) });
      const text = await res.text();
      let data = null;
      try { data = text ? JSON.parse(text) : null; } catch { data = text; }
      return { status: res.status, ok: res.ok, data };
    } finally {
      clearTimeout(slow);
      banner("");
    }
  }

  async function api(path, opts = {}) {
    const method = opts.method || "GET";
    let res;
    try {
      res = await rawFetch(path, opts);
    } catch (e) {
      log(method, path, 0, "network error");
      throw new Error("Cannot reach the API. It may still be starting; try again in a moment.");
    }
    log(method, path, res.status);
    if (!res.ok) {
      const err = new Error((res.data && res.data.message) || "Request failed (" + res.status + ")");
      err.status = res.status;
      throw err;
    }
    return res.data;
  }

  function note(node, text, cls = "") {
    node.textContent = text;
    node.className = "result " + cls;
  }

  // ---------- session ----------
  async function loadMe() {
    if (!state.token) { state.me = null; return renderSession(); }
    try {
      state.me = await api("/api/users/me");
    } catch {
      state.token = null; safeSet("token", null); state.me = null;
    }
    renderSession();
  }

  function renderSession() {
    const box = $("#session");
    box.replaceChildren();
    $("#auth-card").hidden = !!state.me;
    $("#admin-card").hidden = !(state.me && state.me.role === "ADMIN");
    $("#orders-card").hidden = !state.me;
    if (state.me) {
      box.append(
        el("span", {}, state.me.email + " (" + state.me.role + ")"),
        el("button", { class: "ghost small", type: "button", onclick: logout }, "Log out"),
      );
      loadOrders();
    }
  }

  function logout() {
    state.token = null; state.me = null; state.keys = {};
    safeSet("token", null);
    renderSession();
    loadProducts();
  }

  $("#auth-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    const mode = e.submitter ? e.submitter.dataset.mode : "login";
    const buttons = e.target.querySelectorAll("button");
    buttons.forEach((b) => (b.disabled = true));
    try {
      const out = await api("/api/auth/" + mode, {
        method: "POST",
        body: { email: $("#email").value, password: $("#password").value },
      });
      state.token = out.token; safeSet("token", out.token);
      await loadMe();
      loadProducts();
    } catch (err) {
      alert(err.message);
    } finally {
      buttons.forEach((b) => (b.disabled = false));
    }
  });

  // ---------- products ----------
  async function loadProducts() {
    const list = $("#products");
    try {
      const page = await api("/api/products?size=50&q=" + encodeURIComponent($("#search").value));
      list.replaceChildren();
      if (!page.content.length) {
        list.append(el("li", { class: "meta" }, "No products yet. Log in as the admin to add some."));
        return;
      }
      for (const p of page.content) list.append(productRow(p));
    } catch (err) {
      list.replaceChildren(el("li", { class: "bad" }, err.message));
    }
  }

  function productRow(p) {
    const result = el("div", { class: "result" });
    const buy = el("button", { class: "small", type: "button" }, "Buy 1");
    buy.disabled = !state.me || p.stock < 1;
    buy.title = state.me ? "" : "Log in to place an order";
    buy.addEventListener("click", async () => {
      buy.disabled = true;
      try {
        const o = await api("/api/orders", { method: "POST", body: { items: [{ productId: p.id, quantity: 1 }] } });
        note(result, "Order #" + o.id + " placed.", "ok");
        loadOrders();
        loadProducts();
      } catch (err) {
        note(result, err.message, "bad");
        buy.disabled = false;
      }
    });
    return el("li", {},
      el("div", {}, el("strong", {}, p.name), el("div", { class: "meta" }, money(p.price) + " · " + p.stock + " in stock")),
      el("div", { class: "actions" }, buy),
      result);
  }

  $("#search").addEventListener("input", debounce(loadProducts, 300));
  $("#refresh").addEventListener("click", loadProducts);

  $("#product-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    try {
      await api("/api/admin/products", {
        method: "POST",
        body: { name: $("#p-name").value, description: "", price: Number($("#p-price").value), stock: Number($("#p-stock").value) },
      });
      e.target.reset();
      loadProducts();
    } catch (err) {
      alert(err.message);
    }
  });

  // ---------- orders & payments ----------
  async function loadOrders() {
    const list = $("#orders");
    try {
      const page = await api("/api/orders?size=20");
      list.replaceChildren();
      if (!page.content.length) list.append(el("li", { class: "meta" }, "No orders yet. Buy something above."));
      for (const o of page.content) list.append(orderRow(o));
    } catch (err) {
      list.replaceChildren(el("li", { class: "bad" }, err.message));
    }
  }

  function payRequest(orderId, key) {
    return rawFetch("/api/orders/" + orderId + "/pay", { method: "POST", headers: { "Idempotency-Key": key } });
  }

  function orderRow(o) {
    const result = el("div", { class: "result" });
    const actions = el("div", { class: "actions" });

    if (o.status === "PENDING") {
      const pay = el("button", { class: "small", type: "button" }, "Pay");
      pay.addEventListener("click", async () => {
        pay.disabled = true;
        const key = (state.keys[o.id] = state.keys[o.id] || uuid());
        try {
          const r = await payRequest(o.id, key);
          log("POST", "/api/orders/" + o.id + "/pay", r.status);
          if (!r.ok) throw new Error((r.data && r.data.message) || "Payment failed");
          note(result, "Payment #" + r.data.id + " " + r.data.status + " (key " + key.slice(0, 8) + "…)", "ok");
          loadOrders();
        } catch (err) {
          note(result, err.message, "bad");
          pay.disabled = false;
        }
      });

      const race = el("button", { class: "ghost small", type: "button", title: "Sends 10 payments at once, each with a different key" }, "Race 10 payments");
      race.addEventListener("click", async () => {
        race.disabled = true; pay.disabled = true;
        note(result, "Firing 10 simultaneous payment requests…");
        const rs = await Promise.allSettled(Array.from({ length: 10 }, () => payRequest(o.id, uuid())));
        const codes = rs.map((x) => (x.status === "fulfilled" ? x.value.status : 0));
        const accepted = codes.filter((c) => c === 200).length;
        log("POST", "/api/orders/" + o.id + "/pay ×10", "", "statuses: " + codes.join(","));
        note(result, accepted + " accepted, " + (10 - accepted) + " rejected. The database allows exactly one active payment per order.", accepted === 1 ? "ok" : "bad");
        loadOrders();
      });
      actions.append(pay, race);
    } else if (o.status === "PAID") {
      const key = state.keys[o.id];
      if (key) {
        const retry = el("button", { class: "ghost small", type: "button", title: "Replays the same Idempotency-Key" }, "Retry same key");
        retry.addEventListener("click", async () => {
          const r = await payRequest(o.id, key);
          log("POST", "/api/orders/" + o.id + "/pay", r.status, "(replay)");
          note(result, r.ok ? "Same payment #" + r.data.id + " returned, no second charge." : (r.data && r.data.message) || "Failed", r.ok ? "ok" : "bad");
        });
        actions.append(retry);
      }
    }

    return el("li", {},
      el("div", {},
        el("strong", {}, "Order #" + o.id + " "), el("span", { class: "badge " + o.status }, o.status),
        el("div", { class: "meta" }, money(o.totalAmount) + " · " + o.items.length + " item(s)")),
      actions, result);
  }

  function debounce(fn, ms) {
    let t;
    return (...a) => { clearTimeout(t); t = setTimeout(() => fn(...a), ms); };
  }

  // ---------- boot ----------
  $("#docs").href = API + "/swagger-ui.html";
  loadMe().then(loadProducts);
})();
