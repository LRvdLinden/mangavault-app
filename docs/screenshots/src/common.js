// Shared building blocks for the Manga Vault README screenshots.
// Each screen HTML calls MV.screen({...}) with its own content.
(function () {
  const f = (d) => `<path d="${d}" fill="currentColor"/>`;
  const s = (inner, w = 2) => `<g fill="none" stroke="currentColor" stroke-width="${w}" stroke-linecap="round" stroke-linejoin="round">${inner}</g>`;
  const STAR = "M12 2.8l2.8 5.9 6.4.8-4.7 4.4 1.2 6.4L12 17.2l-5.7 3.1 1.2-6.4-4.7-4.4 6.4-.8z";
  const HEART = "M12 20.3S3.5 15.2 3.5 9.2A4.6 4.6 0 0 1 12 6.6a4.6 4.6 0 0 1 8.5 2.6c0 6-8.5 11.1-8.5 11.1z";
  const BOOKS = (fill) => fill
    ? '<rect x="2.6" y="3.6" width="5" height="16.8" rx="1.1" fill="currentColor"/><rect x="8.9" y="3.6" width="5" height="16.8" rx="1.1" fill="currentColor"/><rect x="15.6" y="4.3" width="4.6" height="16.2" rx="1.1" transform="rotate(-14 17.9 12.4)" fill="currentColor"/>'
    : s('<rect x="3.2" y="4.2" width="4.2" height="15.6" rx="1"/><rect x="9.4" y="4.2" width="4.2" height="15.6" rx="1"/><rect x="15.8" y="4.8" width="3.9" height="15" rx="1" transform="rotate(-14 17.75 12.3)"/>', 1.6);

  const ICONS = {
    "house.fill": f("M12 2.8 1.9 11.3a1 1 0 0 0 1.3 1.5l.9-.8v8.2c0 .9.7 1.6 1.6 1.6H9.6v-6.2h4.8v6.2h3.9c.9 0 1.6-.7 1.6-1.6V12l.9.8a1 1 0 0 0 1.3-1.5z"),
    "book.fill": f("M11.2 5.6C9 4.2 5.6 3.6 2.6 4.1c-.5.1-.8.5-.8 1v13.1c0 .6.6 1.1 1.2 1 2.7-.4 5.7.1 8.2 1.5zM12.8 5.6c2.2-1.4 5.6-2 8.6-1.5.5.1.8.5.8 1v13.1c0 .6-.6 1.1-1.2 1-2.7-.4-5.7.1-8.2 1.5z"),
    "book": s('<path d="M12 6.2C9.8 4.7 6.4 4.2 3 4.7v13.6c3.4-.5 6.8 0 9 1.5 2.2-1.5 5.6-2 9-1.5V4.7c-3.4-.5-6.8 0-9 1.5zM12 6.2v13.6"/>', 1.7),
    "books.vertical.fill": BOOKS(true),
    "books.vertical": BOOKS(false),
    "calendar.badge.clock": '<path d="M5.2 4.2h9.6A3.2 3.2 0 0 1 18 7.4v2.1a7 7 0 0 0-7.4 11H5.2A3.2 3.2 0 0 1 2 17.3V7.4a3.2 3.2 0 0 1 3.2-3.2z" fill="currentColor"/><path d="M2.5 8.6H17.5" stroke="#121212" stroke-width="1.3"/><circle cx="17.4" cy="16.6" r="5" fill="none" stroke="currentColor" stroke-width="1.8"/><path d="M17.4 14.1v2.7l1.8 1.1" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>',
    "ellipsis.circle.fill": '<circle cx="12" cy="12" r="10" fill="currentColor"/><circle cx="7.3" cy="12" r="1.55" fill="#121212"/><circle cx="12" cy="12" r="1.55" fill="#121212"/><circle cx="16.7" cy="12" r="1.55" fill="#121212"/>',
    "ellipsis.circle": s('<circle cx="12" cy="12" r="9.3"/>', 1.6) + '<circle cx="7.6" cy="12" r="1.35" fill="currentColor"/><circle cx="12" cy="12" r="1.35" fill="currentColor"/><circle cx="16.4" cy="12" r="1.35" fill="currentColor"/>',
    "bell": s('<path d="M6 16.8V11a6 6 0 0 1 12 0v5.8l1.8 1.8H4.2z"/><path d="M10 21a2.2 2.2 0 0 0 4 0"/>', 1.8),
    "slider.horizontal.3": s('<path d="M3 6h18M3 12h18M3 18h18"/>', 1.8) + '<circle cx="8" cy="6" r="2.4" fill="#121212" stroke="currentColor" stroke-width="1.8"/><circle cx="16" cy="12" r="2.4" fill="#121212" stroke="currentColor" stroke-width="1.8"/><circle cx="10" cy="18" r="2.4" fill="#121212" stroke="currentColor" stroke-width="1.8"/>',
    "sparkles": f("M10 3l1.6 4.9 4.9 1.6-4.9 1.6L10 16l-1.6-4.9L3.5 9.5l4.9-1.6zM18 13l.9 2.6 2.6.9-2.6.9L18 20l-.9-2.6-2.6-.9 2.6-.9zM18 2.5l.6 1.7 1.7.6-1.7.6-.6 1.7-.6-1.7-1.7-.6 1.7-.6z"),
    "flame.fill": f("M12.3 2c.4 3.2 2.3 4.7 3.9 6.6 1.5 1.8 2.6 3.7 2.6 6.2A6.8 6.8 0 0 1 12 21.6a6.8 6.8 0 0 1-6.8-6.8c0-2.6 1.3-4.8 3.2-6.3-.1 1.7.5 3.2 1.7 3.9-.4-3.9.8-7.6 2.2-10.4z"),
    "checkmark.circle": s('<circle cx="12" cy="12" r="9.3"/><path d="M7.8 12.3l2.8 2.8 5.6-6"/>', 1.8),
    "checkmark.circle.fill": '<circle cx="12" cy="12" r="10" fill="currentColor"/><path d="M7.6 12.3l3 3 5.8-6.2" fill="none" stroke="#141414" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/>',
    "bookmark": s('<path d="M6.5 3.5h11v17L12 16.6l-5.5 3.9z"/>', 1.8),
    "pause.circle": s('<circle cx="12" cy="12" r="9.3"/><path d="M10 8.5v7M14 8.5v7"/>', 1.8),
    "heart": s(`<path d="${HEART}"/>`, 1.8),
    "heart.fill": f(HEART),
    "eurosign.circle": s('<circle cx="12" cy="12" r="9.3"/><path d="M15.4 8.3a4.3 4.3 0 1 0 0 7.4M7.3 10.8h6.2M7.3 13.3h6.2"/>', 1.7),
    "star": s(`<path d="${STAR}"/>`, 1.6),
    "star.fill": f(STAR),
    "star.circle.fill": `<circle cx="12" cy="12" r="10" fill="currentColor"/><path d="${STAR}" fill="#141414" transform="translate(12 12) scale(.56) translate(-12 -12)"/>`,
    "rectangle.stack.fill": '<rect x="3" y="8.5" width="18" height="12.5" rx="2.5" fill="currentColor"/>' + s('<path d="M5.5 5.6h13M8 2.9h8"/>', 1.8),
    "rectangle.stack": s('<rect x="3.5" y="9" width="17" height="11.5" rx="2.3"/><path d="M5.5 5.8h13M8 3h8"/>', 1.7),
    "trophy.fill": f("M7 3h10v1.5h3.2v2.8a4.3 4.3 0 0 1-4 4.3A5.3 5.3 0 0 1 13 14.7V17h3v4H8v-4h3v-2.3a5.3 5.3 0 0 1-3.2-3.1 4.3 4.3 0 0 1-4-4.3V4.5H7zM5.6 6.3v1a2.5 2.5 0 0 0 1.5 2.3A9 9 0 0 1 7 8V6.3zM18.4 6.3H17V8a9 9 0 0 1-.1 1.6 2.5 2.5 0 0 0 1.5-2.3z"),
    "shippingbox.fill": '<path d="M12 2.3l9 4.4v10.6l-9 4.4-9-4.4V6.7z" fill="currentColor"/><path d="M3.4 6.9 12 11.2l8.6-4.3M12 11.2v10M7.4 4.5l8.8 4.4" fill="none" stroke="#141414" stroke-width="1.3"/>',
    "target": s('<circle cx="12" cy="12" r="9.3"/><circle cx="12" cy="12" r="5.5"/>', 1.7) + '<circle cx="12" cy="12" r="1.9" fill="currentColor"/>',
    "plus": s('<path d="M12 5v14M5 12h14"/>', 2.4),
    "chevron.left": s('<path d="M15 4 7 12l8 8"/>', 2.7),
    "chevron.right": s('<path d="M9 4.5 16.5 12 9 19.5"/>', 2.4),
    "chevron.down": s('<path d="M5 8.5l7 7 7-7"/>', 2.6),
    "checkmark": s('<path d="M4.5 12.5l5 5L19.5 7"/>', 2.8),
    "xmark": s('<path d="M6 6l12 12M18 6 6 18"/>', 2.4),
    "safari": s('<circle cx="12" cy="12" r="9.3"/>', 1.7) + '<path d="M16 8l-2.4 5.6L8 16l2.4-5.6z" fill="currentColor"/>',
    "magnifyingglass": s('<circle cx="10.5" cy="10.5" r="6.6"/><path d="M15.4 15.4 20.6 20.6"/>', 2.2),
    "arrow.up.arrow.down": s('<path d="M7.5 20V4M3.8 7.7 7.5 4l3.7 3.7M16.5 4v16M12.8 16.3l3.7 3.7 3.7-3.7"/>', 1.9),
    "square.grid.2x2": s('<rect x="3.5" y="3.5" width="7" height="7" rx="1.6"/><rect x="13.5" y="3.5" width="7" height="7" rx="1.6"/><rect x="3.5" y="13.5" width="7" height="7" rx="1.6"/><rect x="13.5" y="13.5" width="7" height="7" rx="1.6"/>', 1.8),
    "square.and.arrow.up": s('<path d="M12 3v12M8 6.8 12 3l4 3.8"/><path d="M8.5 10H6.5A1.5 1.5 0 0 0 5 11.5v8A1.5 1.5 0 0 0 6.5 21h11a1.5 1.5 0 0 0 1.5-1.5v-8a1.5 1.5 0 0 0-1.5-1.5h-2"/>', 1.8),
    "lock.fill": '<rect x="5" y="10.5" width="14" height="10.5" rx="2.2" fill="currentColor"/>' + s('<path d="M8 10.5V7.5a4 4 0 0 1 8 0v3"/>', 2.1),
    "gift.fill": '<rect x="3" y="8" width="18" height="4.6" rx="1" fill="currentColor"/><rect x="4.4" y="13.6" width="15.2" height="7.6" rx="1" fill="currentColor"/>' + s('<path d="M12 8C10.4 4.6 6.6 4 6.6 6.4 6.6 8 12 8 12 8zm0 0c1.6-3.4 5.4-4 5.4-1.6C17.4 8 12 8 12 8z"/>', 1.6),
    "calendar.badge.plus": s('<path d="M14 20.5H6A2.5 2.5 0 0 1 3.5 18V7.5A2.5 2.5 0 0 1 6 5h11a2.5 2.5 0 0 1 2.5 2.5V11M3.5 9.8h16M8 3v4M15 3v4M18.5 14.5v6M15.5 17.5h6"/>', 1.7),
    "calendar": s('<rect x="3.5" y="5" width="17" height="15.5" rx="2.5"/><path d="M3.5 9.8h17M8 3v4M16 3v4"/>', 1.7),
    "clock.arrow.circlepath": s('<path d="M4.6 12a7.5 7.5 0 1 0 2.2-5.3L4.3 9.2"/><path d="M4.1 4.6v4.6h4.6"/><path d="M12 8v4.3l2.8 1.7"/>', 1.8),
    "bolt.fill": f("M13.5 2 4.5 13.5h6.2L9.8 22l9.2-11.6h-6.3z"),
    "dot.radiowaves.left.and.right": '<circle cx="12" cy="12" r="2" fill="currentColor"/>' + s('<path d="M8.2 8.2a5.4 5.4 0 0 0 0 7.6M15.8 8.2a5.4 5.4 0 0 1 0 7.6M5.2 5.2a9.6 9.6 0 0 0 0 13.6M18.8 5.2a9.6 9.6 0 0 1 0 13.6"/>', 1.9),
    "globe": s('<circle cx="12" cy="12" r="9.3"/><path d="M2.7 12h18.6M12 2.7c2.6 2.6 3.8 5.7 3.8 9.3s-1.2 6.7-3.8 9.3M12 2.7C9.4 5.3 8.2 8.4 8.2 12s1.2 6.7 3.8 9.3"/>', 1.6),
    "person.badge.minus": '<circle cx="9.5" cy="7.8" r="4" fill="currentColor"/><path d="M2 20.5c0-4 3.4-6.6 7.5-6.6s7.5 2.6 7.5 6.6z" fill="currentColor"/>' + s('<path d="M17 10h5.2"/>', 2.2),
    "bubble.left.fill": f("M12 3.5c5.2 0 9.3 3.4 9.3 7.7s-4.1 7.7-9.3 7.7c-1 0-2-.1-2.9-.4L4.6 20.6c-.4.2-.9-.2-.7-.6l1-3.3C3.6 15.3 2.7 13.4 2.7 11.2 2.7 6.9 6.8 3.5 12 3.5z"),
    "bubble.left.and.bubble.right": s('<path d="M14.5 4H5a2 2 0 0 0-2 2v6a2 2 0 0 0 2 2h1v3l3.5-3h5a2 2 0 0 0 2-2V6a2 2 0 0 0-2-2z"/><path d="M18 8.5h1a2 2 0 0 1 2 2v5.5a2 2 0 0 1-2 2h-1V21l-3.5-3h-3"/>', 1.6),
    "arrow.up.right.square": s('<rect x="3.5" y="3.5" width="17" height="17" rx="3"/><path d="M9.3 14.7 15.4 8.6M10.4 8.6h5v5"/>', 1.8),
    "chart.pie": s('<path d="M11 3.7a8.4 8.4 0 1 0 9.3 9.3H11z"/><path d="M14 2.6v7.4h7.4A7.4 7.4 0 0 0 14 2.6z"/>', 1.7),
    "arrow.clockwise": s('<path d="M19.5 12a7.5 7.5 0 1 1-2.2-5.3"/><path d="M19.6 3.5v4.2h-4.2"/>', 1.9),
    "list.number": s('<path d="M10 6h10.5M10 12h10.5M10 18h10.5"/>', 1.9) + '<text x="2.6" y="8.2" font-size="6.5" font-weight="700" fill="currentColor" font-family="Inter">1</text><text x="2.6" y="14.2" font-size="6.5" font-weight="700" fill="currentColor" font-family="Inter">2</text><text x="2.6" y="20.2" font-size="6.5" font-weight="700" fill="currentColor" font-family="Inter">3</text>',
    "photo": s('<rect x="3" y="5" width="18" height="14" rx="2.5"/><path d="M3.5 17l5-5 4 4 2.5-2.5 5.5 5"/>', 1.7) + '<circle cx="15.5" cy="9.2" r="1.7" fill="currentColor"/>',
    "person.fill": '<circle cx="12" cy="7.8" r="4.3" fill="currentColor"/><path d="M3.5 21c0-4.4 3.8-7.3 8.5-7.3s8.5 2.9 8.5 7.3z" fill="currentColor"/>',
    "clock": s('<circle cx="12" cy="12" r="9.3"/><path d="M12 7v5.3l3.3 2"/>', 1.8),
    "mappin.and.ellipse": s('<path d="M12 16.5s5-4.6 5-8.6a5 5 0 0 0-10 0c0 4 5 8.6 5 8.6z"/><ellipse cx="12" cy="19.3" rx="7" ry="2.2"/>', 1.6) + '<circle cx="12" cy="8" r="1.8" fill="currentColor"/>',
  };

  function icon(name, cls = "", style = "") {
    return `<svg class="ic ${cls}" viewBox="0 0 24 24" style="${style}" aria-hidden="true">${ICONS[name] || ""}</svg>`;
  }

  // ---- GeneratedCover: same palette + string hash as CoverViews.swift ----
  const PALETTE = [
    ["#ff6b00", "#1a0a00"], ["#ff3d00", "#0d0d0d"], ["#ff8f00", "#0a0a0a"], ["#e65100", "#000000"],
    ["#ff6b00", "#0a0500"], ["#bf360c", "#050505"], ["#ff7000", "#100808"], ["#cc4400", "#080808"],
  ];
  function hash(str) {
    let t = 0;
    for (let i = 0; i < str.length; i++) t = ((t << 5) - t + str.charCodeAt(i)) | 0; // Int32 wrapping, UTF-16 units
    return Math.abs(t);
  }
  function badge(text, o = {}) {
    const st = `color:${o.fg || "#fff"};background:${o.bg || "var(--primary)"};${o.ring ? `box-shadow:inset 0 0 0 1px ${o.ring};` : ""}${o.style || ""}`;
    return `<span class="badge" style="${st}">${o.icon ? icon(o.icon) : ""}${text}</span>`;
  }
  function cover(o) {
    // Thumbnails (< 100 px) are drawn at a 120 px base and scaled down so the title stays legible.
    if (o.width && o.width < 100 && !o._inner) {
      const k = o.width / 120;
      const inner = cover({ ...o, _inner: true, width: 120, radius: (o.radius ?? 10) / k });
      return `<div style="width:${o.width}px;height:${o.width * 1.5}px;flex:none;position:relative"><div style="position:absolute;left:0;top:0;transform:scale(${k});transform-origin:0 0">${inner}</div></div>`;
    }
    const key = o.seed || o.title || "manga";
    const p = PALETTE[hash(key) % PALETTE.length];
    const r = o.radius ?? 10;
    let top = "";
    if (o.omnibus) top = badge("OMNIBUS", { fg: "var(--primary)", bg: "#000", ring: "rgba(255,122,0,.5)" });
    else if (o.sj) top = badge("SJ", { fg: "#000", bg: "#fff" });
    const vols = o.volumesText ?? (o.omnibus ? (o.included || "OMNIBUS") : `${o.total || 1} VOLUMES`);
    const w = o.width ? `width:${o.width}px;` : "width:100%;";
    return `<div class="cover" style="${w}border-radius:${r}px;background:radial-gradient(circle 120px at 30% 15%, rgba(255,255,255,.3), rgba(255,255,255,0) 100%), linear-gradient(to bottom right, ${p[0]}, ${p[1]});${o.style || ""}">
      <div class="cin"><div class="ctop">${top}<span class="cpub">${o.publisher || ""}</span></div>
      <div class="cbottom"><div class="cbar"></div><div class="ctitle">${o.title}</div><div class="cvols">${vols}</div></div></div>
      ${o.overlay || ""}</div>`;
  }

  // ---- small components ----
  const statusColor = { reading: "var(--primary)", completed: "var(--emerald)", plan_to_read: "var(--sky)", on_hold: "var(--onhold)", dropped: "var(--muted)" };
  const statusLabel = { reading: "Reading", completed: "Completed", plan_to_read: "Plan to Read", on_hold: "On Hold", dropped: "Dropped" };
  function statCard(ic, label, value, accent = "var(--primary)") {
    return `<div class="card statcard"><div class="head">${icon(ic, "", `color:${accent}`)}<span>${label}</span></div><div class="val">${value}</div></div>`;
  }
  function chip(title, on = false, ic = "", color = "") {
    return `<span class="chip ${on ? "on" : ""}" style="${on && color ? `background:${color}` : ""}">${ic ? icon(ic) : ""}${title}</span>`;
  }
  function progress(v, h = 8, color = "var(--primary)", glow = "rgba(255,122,0,.5)") {
    return `<div class="progress" style="height:${h}px"><i style="width:${Math.max(0, Math.min(1, v)) * 100}%;background:${color};box-shadow:0 0 6px ${glow}"></i></div>`;
  }
  function stars(v, size = 13) {
    let out = "";
    for (let i = 1; i <= 5; i++) {
      const full = v >= i * 2, half = !full && v >= i * 2 - 1;
      if (full) out += icon("star.fill", "", `width:${size}px;height:${size}px`);
      else if (half) out += `<svg class="ic" viewBox="0 0 24 24" style="width:${size}px;height:${size}px"><defs><clipPath id="h${i}${size}"><rect x="0" y="0" width="12" height="24"/></clipPath></defs><path d="${STAR}" fill="currentColor" clip-path="url(#h${i}${size})"/><path d="${STAR}" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/></svg>`;
      else out += icon("star", "off", `width:${size}px;height:${size}px`);
    }
    return `<span class="stars">${out}</span>`;
  }
  function header(o) {
    return `<div class="screenheader vstack" style="gap:4px">
      ${o.eyebrow ? `<div class="eyebrow">${o.eyebrowIcon ? icon(o.eyebrowIcon) : ""}${o.eyebrow}</div>` : ""}
      <div class="big"><span class="fg">${o.lead} </span><span style="color:${o.accentColor || "var(--primary)"}">${o.accent}</span></div>
      ${o.subtitle ? `<div class="subtitle">${o.subtitle}</div>` : ""}</div>`;
  }
  function wordmark(size = 18) {
    return `<span style="font-size:${size}px;font-weight:900;letter-spacing:-1px"><span class="fg">MANGA</span><span class="pri">VAULT</span></span>`;
  }
  function bell(n) {
    return `<span class="bell">${icon("bell")}${n ? `<span class="count">${n}</span>` : ""}</span>`;
  }
  function countdown(d, h, m, sec) {
    const u = (v, l) => `<div style="min-width:34px;padding:4px 0;background:rgba(0,0,0,.45);border-radius:8px;display:flex;flex-direction:column;align-items:center;gap:1px"><span class="mono" style="font-size:16px;line-height:19px;font-weight:900;color:#fff">${String(v).padStart(2, "0")}</span><span style="font-size:8px;line-height:10px;font-weight:700;color:var(--muted)">${l}</span></div>`;
    return `<div class="hstack" style="gap:4px">${u(d, "DAYS")}${u(h, "HRS")}${u(m, "MIN")}${u(sec, "SEC")}</div>`;
  }
  // EventBannerView (live event, no hero image → accent gradient)
  function eventBanner(e) {
    return `<div style="position:relative;min-height:210px;border-radius:16px;overflow:hidden;box-shadow:inset 0 0 0 1px ${e.accent}80;background:linear-gradient(to bottom right, ${e.accent}, var(--bg))">
      <div style="position:absolute;inset:0;background:linear-gradient(to right, rgba(0,0,0,.92), rgba(0,0,0,.45))"></div>
      ${e.art || ""}
      <div style="position:relative;padding:16px;display:flex;flex-direction:column;gap:6px">
        <div class="c1 w9" style="color:${e.accent}">● Live now</div>
        <div class="t3 w9 white">${e.title}</div>
        <div class="c1 clamp2" style="color:rgba(255,255,255,.75)">${e.subtitle}</div>
        <div class="c2 w7" style="color:rgba(255,255,255,.6)">${e.start} → ${e.end}</div>
        <div class="hstack c2 w7" style="color:rgba(255,255,255,.8)"><span>Your points</span><span class="spacer"></span><span class="w9" style="color:${e.accent}">${e.points}/${e.max}</span></div>
        ${progress(e.points / e.max, 5, e.accent, e.accent + "80")}
        <div class="c2 w9" style="color:rgba(255,255,255,.7);padding-top:2px">ENDS IN</div>
        ${countdown(...e.countdown)}
      </div></div>`;
  }

  // ---- screen chrome ----
  function statusBar() {
    return `<div class="statusbar"><div class="time">9:41</div><div class="right">
      <svg width="19" height="12" viewBox="0 0 19 12"><rect x="0" y="8" width="3.4" height="4" rx="1" fill="#fff"/><rect x="5" y="5.5" width="3.4" height="6.5" rx="1" fill="#fff"/><rect x="10" y="3" width="3.4" height="9" rx="1" fill="#fff"/><rect x="15" y="0" width="3.4" height="12" rx="1" fill="#fff"/></svg>
      <svg width="17" height="12" viewBox="0 0 17 12"><path d="M8.5 2.3c2.4 0 4.6.9 6.3 2.5l1.2-1.2A10.6 10.6 0 0 0 8.5.6 10.6 10.6 0 0 0 1 3.6l1.2 1.2a9 9 0 0 1 6.3-2.5z" fill="#fff"/><path d="M8.5 5.6c1.5 0 2.9.6 4 1.6l1.2-1.2a7.3 7.3 0 0 0-10.4 0l1.2 1.2c1.1-1 2.5-1.6 4-1.6z" fill="#fff"/><path d="M8.5 8.9c.6 0 1.2.2 1.6.7L8.5 11.3 6.9 9.6c.4-.5 1-.7 1.6-.7z" fill="#fff"/></svg>
      <svg width="28" height="13" viewBox="0 0 28 13"><rect x="0.5" y="0.5" width="24" height="12" rx="3.8" fill="none" stroke="rgba(255,255,255,.4)"/><rect x="2" y="2" width="21" height="9" rx="2.4" fill="#fff"/><path d="M26 4.3v4.4c.9-.3 1.5-1.2 1.5-2.2s-.6-1.9-1.5-2.2z" fill="rgba(255,255,255,.45)"/></svg>
    </div></div>`;
  }
  function navBar(o) {
    const leading = o.back ? `<span class="back">${icon("chevron.left")}<span>${o.back}</span></span>` : (o.leading || "");
    const title = o.principal || `<span class="nowrap">${o.title || ""}</span>`;
    return `<div class="navbar ${o.search ? "" : "hairline"}"><div class="leading">${leading}</div><div class="title">${title}</div><div class="trailing">${o.trailing || ""}</div></div>`;
  }
  function searchDrawer(placeholder) {
    return `<div class="searchdrawer"><div class="field">${icon("magnifyingglass")}<span>${placeholder}</span></div></div>`;
  }
  function tabBar(active, moreBadge) {
    const tabs = [["home", "Home", "house.fill"], ["catalog", "Catalog", "book.fill"], ["collection", "Collection", "books.vertical.fill"], ["events", "Events", "calendar.badge.clock"], ["more", "More", "ellipsis.circle.fill"]];
    return `<div class="tabbar">${tabs.map(([k, l, i]) => `<div class="tab ${k === active ? "active" : ""}">${icon(i)}<span>${l}</span>${k === "more" && moreBadge ? `<span class="tbadge">${moreBadge}</span>` : ""}</div>`).join("")}</div><div class="homeind"></div>`;
  }

  function screen(o) {
    const top = 98 + (o.search ? 52 : 0);
    document.body.innerHTML = `<div id="phone">
      <div class="scrollarea" style="top:${top}px"><div class="content" style="transform:translateY(${-(o.scroll || 0)}px)">${o.content}</div></div>
      ${statusBar()}${navBar(o.nav)}${o.search ? searchDrawer(o.search) : ""}${tabBar(o.tab, o.moreBadge)}
    </div>`;
  }

  window.MV = { icon, cover, badge, statCard, chip, progress, stars, header, wordmark, bell, countdown, eventBanner, screen, statusColor, statusLabel, hash, PALETTE };
})();
