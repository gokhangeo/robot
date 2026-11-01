// Makes the robot on the main screen feel alive: lip sync from the spoken text, moods with brows and eyes,
// natural blinking and glances, a head that follows the finger with spring motion, swaying accessories,
// a mic-driven antenna glow, yawns when bored, a soft shadow and a time-of-day sky.
// index.html calls Alive.willSay / mouthStart / mouthStop / mood / level / noteHeard / recentNote / rateFor.
(function () {
  // index.html draws the robot after this file loads, so start once the page is parsed.
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", init); else init();
  function init() {
  const disc = document.getElementById("disc");
  const svg = disc && disc.querySelector("svg.face");
  if (!svg) return;
  const body = document.body;
  const q = (s) => svg.querySelector(s);
  const mouth = q(".mouth"), mclip = q(".mouth-clip"), tongue = q(".tongue");
  const pupilsAll = [...svg.querySelectorAll(".pupils")], pupilDots = [...svg.querySelectorAll(".pupil")], eyes = q(".eyes"), look = q(".head-look"), antenna = q(".antenna");
  const tip = antenna && antenna.querySelector(".antenna-tip"), tipY = tip ? +tip.getAttribute("cy") || 40 : 40;
  const browL = q(".brow-l"), browR = q(".brow-r"), brows = q(".brows");
  const accs = [...svg.querySelectorAll(".acc-sway")], glare = [...svg.querySelectorAll(".glare")];
  const glareBase = glare.map((g) => g.getAttribute("transform") || "");
  const reduce = matchMedia("(prefers-reduced-motion: reduce)").matches;
  const store = {
    get(k, d) { try { return (typeof A !== "undefined" ? A.get(k, "") : localStorage.getItem(k)) || d; } catch (e) { return d; } },
    set(k, v) { try { typeof A !== "undefined" ? A.set(k, v) : localStorage.setItem(k, v); } catch (e) { } }
  };
  const st = () => (typeof state !== "undefined" ? state : "idle");
  const sheetOpen = () => !!document.querySelector(".sheet.open");

  // ---------- styles ----------
  const css = document.createElement("style");
  css.textContent = `
    #disc .eyes { animation: none !important; }
    #disc .pupils { transition: none; }
    #disc .eye { transform-box: fill-box; transform-origin: center; transition: transform .28s cubic-bezier(.3,1.4,.5,1); }
    body.listening #disc .head-group { animation-play-state: paused; }
    .botShadow { width: min(42vw, 200px, 27vh); height: 14px; margin: -8px 0 -6px; border-radius: 50%; pointer-events: none;
      background: radial-gradient(closest-side, rgba(0,0,0,.55), rgba(0,0,0,0)); animation: botShadow 4s ease-in-out infinite; }
    @keyframes botShadow { 0%,100% { transform: scale(1); opacity: .9 } 50% { transform: scale(.82); opacity: .55 } }
    body.sleeping .botShadow { animation-duration: 7s; }
    body.arOn .botShadow, body.partying .botShadow { display: none; }
    :where(body.mood-mutlu #disc) .eye, :where(body.mood-gururlu #disc) .eye { transform: scaleY(.55); }
    :where(body.mood-heyecanli #disc) .eye { transform: scale(1.15); }
    :where(body.mood-saskin #disc) .eye { transform: scale(1.28); }
    :where(body.mood-uzgun #disc) .eye { transform: scaleY(.78) translateY(1.5px); }
    :where(body.mood-dusunceli #disc) .eye { transform: scaleY(.8); }
    :where(body.mood-utangac #disc) .eye { transform: scaleY(.6) translateY(1px); }
    :where(body.mood-sakin #disc) .eye { transform: scaleY(.72); }
    :where(body.mood-uykulu #disc) .eye { transform: scaleY(.3) translateY(2px); }
    body.mood-utangac #disc .cheeks, body.mood-heyecanli #disc .cheeks, body.mood-gururlu #disc .cheeks { opacity: .85; }
    body.mood-uzgun .glow { opacity: .35; }
    /* time-of-day light behind everything */
    body.tod-morning { background-image: radial-gradient(120% 60% at 50% -10%, rgba(255,214,150,.22), transparent 70%); }
    body.tod-day { background-image: radial-gradient(120% 60% at 50% -10%, rgba(120,200,255,.18), transparent 70%); }
    body.tod-evening { background-image: radial-gradient(130% 70% at 50% -10%, rgba(255,140,90,.22), rgba(170,90,200,.10) 45%, transparent 75%); }
    body.tod-night { background-image: radial-gradient(120% 60% at 50% -10%, rgba(90,110,255,.16), transparent 70%); }
    body.tod-night .sky { opacity: .45; }
    body.night .sky { opacity: 1; }
  `;
  document.head.appendChild(css);

  const bot = document.getElementById("bot");
  if (bot && !document.querySelector(".botShadow")) {
    const sh = document.createElement("div"); sh.className = "botShadow";
    bot.insertAdjacentElement("afterend", sh);
  }

  function timeOfDay() {
    const h = new Date().getHours();
    const t = h >= 6 && h < 11 ? "morning" : h >= 11 && h < 17 ? "day" : h >= 17 && h < 20 ? "evening" : "night";
    ["morning", "day", "evening", "night"].forEach((x) => body.classList.toggle("tod-" + x, x === t));
  }
  timeOfDay(); setInterval(timeOfDay, 60000);

  // ---------- springs ----------
  function spring(v, k, z) { return { x: v, v: 0, t: v, k, z }; }
  function stepSpring(s, dt) {
    const c = 2 * Math.sqrt(s.k) * s.z;
    s.v += ((s.t - s.x) * s.k - s.v * c) * dt;
    s.x += s.v * dt;
  }

  // ---------- mouth shapes: corner y, half width, upper and lower lip control points, tilt ----------
  const V = {
    rest: { y: 135, w: 12, up: 143, lo: 143, t: 0 },
    a: { y: 134, w: 12, up: 138, lo: 153, t: 0 },
    e: { y: 134, w: 14, up: 139, lo: 148, t: 0 },
    i: { y: 135, w: 14, up: 140, lo: 145, t: 0 },
    o: { y: 136, w: 7.5, up: 131, lo: 147, t: 0 },
    u: { y: 137, w: 5.5, up: 133, lo: 143, t: 0 },
    m: { y: 136, w: 10, up: 138.5, lo: 138.5, t: 0 },
    f: { y: 135, w: 11, up: 138, lo: 141, t: 0 },
    c: { y: 135, w: 11, up: 139, lo: 143, t: 0 },
    yawn: { y: 137, w: 8, up: 129, lo: 155, t: 0 }
  };
  const MOODS = {
    notr: { rest: V.rest, rate: 1 },
    mutlu: { rest: { y: 133, w: 14, up: 140, lo: 150, t: 0 }, brow: [[0, -2], [0, -2]], glow: "#3ddba4", rate: 1.03 },
    heyecanli: { rest: { y: 133, w: 13.5, up: 139, lo: 153, t: 0 }, brow: [[-3, -4], [-3, -4]], glow: "#ffd43b", rate: 1.08 },
    saskin: { rest: { y: 137, w: 6, up: 130, lo: 147, t: 0 }, brow: [[4, -5], [4, -5]], glow: "#4fb3ff", rate: 1.02 },
    uzgun: { rest: { y: 140, w: 10, up: 134, lo: 134, t: 0 }, brow: [[14, 0], [14, 0]], glow: "#7c8cff", rate: .92 },
    dusunceli: { rest: { y: 137, w: 8, up: 136, lo: 136, t: 1.5, dx: 5 }, brow: [[2, -4], [-8, 1]], glow: "#ffc857", rate: .96 },
    utangac: { rest: { y: 137, w: 7, up: 141, lo: 141, t: 0 }, brow: [[9, 0], [9, 0]], glow: "#ff7eb6", rate: .96 },
    gururlu: { rest: { y: 134, w: 13, up: 143, lo: 143, t: -2 }, brow: [[-6, -1], [-6, -1]], glow: "#ffd43b", rate: 1 },
    sakin: { rest: { y: 135, w: 10, up: 140, lo: 140, t: 0 }, brow: [[0, 0], [0, 0]], glow: "#a78bfa", rate: .93 },
    uykulu: { rest: V.yawn, brow: [[7, 2], [7, 2]], glow: "#a78bfa", rate: .9 }
  };
  const MOOD_ALIAS = { "mutlu": "mutlu", "neşeli": "mutlu", "heyecanlı": "heyecanli", "heyecanli": "heyecanli", "şaşkın": "saskin", "saskin": "saskin",
    "üzgün": "uzgun", "uzgun": "uzgun", "düşünceli": "dusunceli", "dusunceli": "dusunceli", "utangaç": "utangac", "utangac": "utangac",
    "gururlu": "gururlu", "sakin": "sakin", "uykulu": "uykulu", "nötr": "notr", "notr": "notr" };

  const M = {};
  ["y", "w", "up", "lo", "t", "dx"].forEach((k) => (M[k] = spring(V.rest[k] || 0, 900, .72)));
  function setMouth(shape, open) {
    const f = open == null ? 1 : open;
    M.y.t = shape.y; M.w.t = shape.w; M.t.t = shape.t || 0; M.dx.t = shape.dx || 0;
    // open < 1 shrinks the gap between the lips (quieter syllables)
    const mid = (shape.up + shape.lo) / 2;
    M.up.t = mid + (shape.up - mid) * f; M.lo.t = mid + (shape.lo - mid) * f;
  }
  function drawMouth() {
    const x = 100 + M.dx.x, y = M.y.x, w = Math.max(3, M.w.x), t = M.t.x;
    let up = M.up.x, lo = M.lo.x;
    if (lo < up) lo = up; // never let the lips cross
    const d = `M${(x - w).toFixed(1)} ${(y + t).toFixed(1)} Q${x.toFixed(1)} ${up.toFixed(1)} ${(x + w).toFixed(1)} ${(y - t).toFixed(1)} Q${x.toFixed(1)} ${lo.toFixed(1)} ${(x - w).toFixed(1)} ${(y + t).toFixed(1)} Z`;
    mouth.setAttribute("d", d);
    if (mclip) mclip.setAttribute("d", d);
    if (tongue) {
      const gap = lo - up;
      tongue.setAttribute("cx", x.toFixed(1));
      tongue.setAttribute("cy", (y + (lo - y) * .55).toFixed(1));
      tongue.setAttribute("rx", gap > 6 ? (w * .55).toFixed(1) : "0");
      tongue.setAttribute("ry", gap > 6 ? (gap * .28).toFixed(1) : "0");
    }
  }

  // ---------- moods ----------
  let mood = "notr", moodUntil = 0;
  function curMood() {
    if (yawnUntil > performance.now()) return MOODS.uykulu;
    if (mood !== "notr" && moodUntil > performance.now()) return MOODS[mood];
    const s = st();
    if (s === "thinking") return MOODS.dusunceli;
    return MOODS.notr;
  }
  function curMoodName() {
    if (yawnUntil > performance.now()) return "uykulu";
    if (mood !== "notr" && moodUntil > performance.now()) return mood;
    return st() === "thinking" ? "dusunceli" : "notr";
  }
  let shownMood = "";
  function applyMood() {
    const name = curMoodName();
    if (name === shownMood) return;
    shownMood = name;
    Object.keys(MOODS).forEach((k) => body.classList.toggle("mood-" + k, k === name && k !== "notr"));
    const m = MOODS[name];
    if (brows) brows.style.opacity = m.brow ? ".9" : "0";
    if (m.brow) {
      // [angle, lift]: a positive angle raises the inner ends (worried, sad), a negative one lowers them (proud, determined)
      browL.style.transform = `translateY(${m.brow[0][1]}px) rotate(${-m.brow[0][0]}deg)`;
      browR.style.transform = `translateY(${m.brow[1][1]}px) rotate(${m.brow[1][0]}deg)`;
    }
    if (!talking) setMouth(m.rest);
  }

  // ---------- lip sync ----------
  // Turkish TTS speed differs per phone, so the syllable length learns from how long each sentence really took.
  let pace = parseFloat(store.get("lipPace", "1")) || 1;
  let pendingSay = null, lip = null, talking = false;
  const VOWEL_TR = { a: "a", e: "e", ı: "i", i: "i", o: "o", ö: "o", u: "u", ü: "u", â: "a", î: "i", û: "u" };
  const VOWEL_EN = { a: "a", e: "e", i: "i", y: "i", o: "o", u: "u" };
  function buildTimeline(text, lang, rate) {
    const vow = lang === "en" ? VOWEL_EN : VOWEL_TR;
    const s = text.toLocaleLowerCase(lang === "en" ? "en" : "tr");
    const k = pace / Math.max(.5, rate || 1);
    const tl = []; let t = 0;
    const push = (v, d, open) => { tl.push({ at: t, v, open }); t += d * k; };
    for (let i = 0; i < s.length; i++) {
      const ch = s[i];
      if (vow[ch]) push(vow[ch], .115, .75 + Math.random() * .3);
      else if ("mbp".includes(ch)) push("m", .06);
      else if ("fv".includes(ch)) push("f", .055);
      else if (/[0-9]/.test(ch)) { push("e", .1, .8); push("i", .1, .7); }
      else if (/[\p{L}]/u.test(ch)) push("c", .05, .8);
      else if (ch === " ") push("c", .03, .5);
      else if (/[,;:]/.test(ch)) push("rest", .22);
      else if (/[.!?…]/.test(ch)) push("rest", .34);
    }
    tl.push({ at: t, v: "rest" });
    return { tl, est: t, idx: 0 };
  }
  const BABBLE = ["a", "e", "o", "i", "m", "a", "u", "e"];

  const Alive = {
    willSay(text, lang, rate) { pendingSay = { text: String(text || ""), lang, rate: parseFloat(rate) || 1 }; },
    mouthStart() {
      const now = performance.now();
      if (pendingSay) { lip = buildTimeline(pendingSay.text, pendingSay.lang, pendingSay.rate); pendingSay = null; }
      else lip = { tl: null, est: 0 };
      lip.start = now; talking = true; activity();
    },
    mouthStop() {
      const now = performance.now();
      // only a sentence that finished on its own (still "speaking") teaches the pace, not one she interrupted
      if (lip && lip.tl && talking && st() === "speaking") {
        const actual = (now - lip.start) / 1000;
        // learn the phone's speaking speed from complete sentences only
        if (lip.est > .8 && actual > .5) {
          const ratio = actual / lip.est;
          if (ratio > .5 && ratio < 2) { pace = Math.max(.6, Math.min(1.8, pace * (.75 + .25 * ratio))); store.set("lipPace", pace.toFixed(3)); }
        }
      }
      lip = null; talking = false;
      setMouth(curMood().rest);
    },
    mood(raw) {
      const name = MOOD_ALIAS[String(raw || "").trim()] || null;
      if (!name) return;
      mood = name;
      // stays through the reply, then fades back a few seconds after speaking ends
      moodUntil = performance.now() + (name === "notr" ? 0 : 60000);
      shownMood = ""; applyMood();
      if (name === "heyecanli" || name === "mutlu") hop();
      if (name === "saskin") { gaze.x.t = 0; gaze.y.t = -.3; }
    },
    rateFor() { return (MOODS[curMoodName()] || MOODS.notr).rate || 1; },
    level(rms) { level = Math.max(0, Math.min(10, parseFloat(rms) || 0)); },
    noteHeard(text, m) {
      if (m !== "chat" && m !== "english") return;
      const t = String(text || "").trim();
      if (t.length < 8) return;
      let list = [];
      try { list = JSON.parse(store.get("recentTalk", "[]")) || []; } catch (e) { }
      const day = new Date().toISOString().slice(0, 10);
      list.push({ d: day, t: t.slice(0, 90) });
      store.set("recentTalk", JSON.stringify(list.slice(-12)));
    },
    recentNote() {
      let list = [];
      try { list = JSON.parse(store.get("recentTalk", "[]")) || []; } catch (e) { }
      const day = new Date().toISOString().slice(0, 10);
      const old = list.filter((x) => x.d !== day).slice(-5);
      if (!old.length) return "";
      return old.map((x) => `- (${x.d}) "${x.t}"`).join("\n");
    }
  };
  window.Alive = Alive;

  // the mood fades a few seconds after she stops talking
  let wasTalking = false;
  function moodDecay(now) {
    const s = st();
    if (s === "speaking") { wasTalking = true; return; }
    if (wasTalking && s !== "speaking") { wasTalking = false; if (mood !== "notr") moodUntil = Math.min(moodUntil, now + 4000); }
  }

  function hop() {
    if (!bot || reduce) return;
    bot.classList.remove("squish"); void bot.offsetWidth; bot.classList.add("squish");
  }

  // ---------- gaze, blink, head ----------
  const gaze = { x: spring(0, 140, .62), y: spring(0, 140, .62) };
  const head = { x: spring(0, 60, .55), y: spring(0, 60, .55), tilt: spring(0, 50, .5) };
  const sway = spring(0, 70, .25), pupil = spring(3, 80, .6);
  let lastPointer = 0, nextSaccade = 0, nextBlink = performance.now() + 2500, blinkStart = -1, blinkTwice = false;
  let level = 0, glow = 0, lastActivity = performance.now(), yawnUntil = 0, lastYawn = performance.now();
  let tiltX = 0, tiltY = 0;
  const activity = () => { lastActivity = performance.now(); };

  addEventListener("pointermove", pointer, { passive: true });
  addEventListener("pointerdown", (e) => { pointer(e); activity(); if (yawnUntil > performance.now()) { yawnUntil = 0; shownMood = ""; } }, { passive: true });
  function pointer(ev) {
    if (sheetOpen()) return;
    const r = disc.getBoundingClientRect(), cx = r.left + r.width / 2, cy = r.top + r.height * .45;
    gaze.x.t = Math.max(-1, Math.min(1, (ev.clientX - cx) / (innerWidth * .45)));
    gaze.y.t = Math.max(-1, Math.min(1, (ev.clientY - cy) / (innerHeight * .45)));
    lastPointer = performance.now();
  }
  addEventListener("deviceorientation", (ev) => {
    if (ev.gamma == null) return;
    tiltX = Math.max(-1, Math.min(1, ev.gamma / 30));
    tiltY = Math.max(-1, Math.min(1, ((ev.beta || 45) - 45) / 30));
  });

  function saccade(now) {
    const s = st();
    if (now - lastPointer < 2500) return;
    if (faceSeen(now)) {
      // look straight at the child, with tiny natural flicks between their eyes
      const flick = now > nextSaccade ? ((nextSaccade = now + 400 + Math.random() * 900), (Math.random() - .5) * .12) : 0;
      gaze.x.t = Math.max(-1, Math.min(1, cam.x)) + flick; gaze.y.t = Math.max(-1, Math.min(1, cam.y));
      return;
    }
    if (now < nextSaccade) return;
    let x, y;
    if (s === "listening") { x = (Math.random() - .5) * .25; y = .15 + (Math.random() - .5) * .15; nextSaccade = now + 1200 + Math.random() * 1800; }
    else if (s === "speaking") { x = (Math.random() - .5) * .45; y = (Math.random() - .5) * .3; nextSaccade = now + 700 + Math.random() * 1600; }
    else if (s === "thinking") { x = .45 + Math.random() * .3; y = -.55 - Math.random() * .2; nextSaccade = now + 1500 + Math.random() * 1500; }
    else {
      const r = Math.random();
      if (r < .55) { x = (Math.random() - .5) * .4; y = (Math.random() - .5) * .3; }
      else if (r < .75) { x = (Math.random() - .5) * .3; y = .7; } // glance down at the chat bubble
      else { x = Math.random() < .5 ? -.9 : .9; y = (Math.random() - .5) * .6; }
      nextSaccade = now + 900 + Math.random() * 2800;
    }
    const jump = Math.abs(x - gaze.x.t) + Math.abs(y - gaze.y.t);
    gaze.x.t = x; gaze.y.t = y;
    if (jump > .9 && Math.random() < .45) nextBlink = now; // people often blink on a big glance
  }

  function blink(now) {
    if (body.classList.contains("sleeping")) { eyes.style.transform = ""; return; }
    if (blinkStart < 0 && now >= nextBlink) { blinkStart = now; blinkTwice = Math.random() < .18; }
    let sy = 1;
    if (blinkStart >= 0) {
      const e = now - blinkStart;
      if (e < 60) sy = 1 - .92 * (e / 60);
      else if (e < 100) sy = .08;
      else if (e < 190) sy = .08 + .92 * ((e - 100) / 90);
      else {
        sy = 1; blinkStart = -1;
        if (blinkTwice) { blinkTwice = false; nextBlink = now + 120; }
        else nextBlink = now + (st() === "listening" ? 3500 : 2000) + Math.random() * 4000;
      }
    }
    eyes.style.transform = sy < 1 ? `scaleY(${sy.toFixed(3)})` : "";
  }

  function boredom(now) {
    if (st() !== "idle" || sheetOpen() || body.classList.contains("sleeping")) { lastActivity = Math.max(lastActivity, now - 20000); return; }
    if (now - lastActivity > 50000 && now - lastYawn > 75000) {
      lastYawn = now; yawnUntil = now + 2300; shownMood = "";
    }
  }

  // ---------- watching the child through the front camera ----------
  // A small face detector (MediaPipe, free, runs on the phone) finds the face; her eyes and head turn toward it.
  // Turned off in Settings with "Gözleriyle beni takip etsin". The camera stops whenever anything else needs it.
  const cam = { on: false, stream: null, video: null, det: null, busy: false, last: 0, seenAt: 0, lostAt: 0, x: 0, y: 0, failed: 0 };
  function faceSeen(now) { return now - cam.seenAt < 600; }
  const watchWanted = () => store.get("watch", "1") === "1";
  const camAllowed = () => watchWanted() && !document.hidden && !sheetOpen() && !body.classList.contains("arOn")
    && !body.classList.contains("sleeping") && !document.querySelector(".snap.show, .snap.open");
  if (navigator.mediaDevices && navigator.mediaDevices.getUserMedia) {
    const orig = navigator.mediaDevices.getUserMedia.bind(navigator.mediaDevices);
    // any other camera use (photo, AR) first releases ours
    navigator.mediaDevices.getUserMedia = (c) => { if (!cam.starting) stopCam(); return orig(c); };
    cam.gum = orig;
  }
  async function loadDetector() {
    if (cam.det) return cam.det;
    const base = "https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.14";
    const mp = await import(base + "/vision_bundle.mjs");
    const files = await mp.FilesetResolver.forVisionTasks(base + "/wasm");
    cam.det = await mp.FaceDetector.createFromOptions(files, {
      baseOptions: { modelAssetPath: "https://storage.googleapis.com/mediapipe-models/face_detector/blaze_face_short_range/float16/1/blaze_face_short_range.tflite", delegate: "CPU" },
      runningMode: "VIDEO", minDetectionConfidence: .5
    });
    return cam.det;
  }
  async function startCam() {
    if (cam.on || cam.starting || !cam.gum || cam.failed > 2) return;
    cam.starting = true;
    try {
      await loadDetector();
      if (!camAllowed()) return;
      cam.stream = await cam.gum({ video: { facingMode: "user", width: { ideal: 320 }, height: { ideal: 240 }, frameRate: { ideal: 15 } }, audio: false });
      if (!cam.video) { cam.video = document.createElement("video"); cam.video.muted = true; cam.video.playsInline = true; cam.video.setAttribute("playsinline", ""); }
      cam.video.srcObject = cam.stream;
      await cam.video.play();
      cam.on = true;
    } catch (e) { cam.failed++; stopCam(); }
    finally { cam.starting = false; }
  }
  function stopCam() {
    if (cam.stream) cam.stream.getTracks().forEach((t) => t.stop());
    cam.stream = null; cam.on = false;
    if (cam.video) cam.video.srcObject = null;
  }
  function track(now) {
    if (!cam.on || cam.busy || now - cam.last < 110) return;
    const v = cam.video;
    if (!v || v.readyState < 2 || !v.videoWidth) return;
    cam.last = now; cam.busy = true;
    try {
      const r = cam.det.detectForVideo(v, now);
      const f = r && r.detections && r.detections[0];
      if (f) {
        const b = f.boundingBox, w = v.videoWidth, h = v.videoHeight;
        // the front camera sees a mirror image: the child's right is the picture's left
        cam.x = -(((b.originX + b.width / 2) / w) - .5) * 2.2;
        cam.y = (((b.originY + b.height * .4) / h) - .5) * 2;
        if (now - cam.seenAt > 8000 && now - cam.lostAt > 3000 && st() === "idle") { nextBlink = now; hop(); }
        cam.seenAt = now;
      } else if (faceSeen(now)) cam.lostAt = now;
    } catch (e) { cam.failed++; if (cam.failed > 2) stopCam(); }
    cam.busy = false;
  }
  setInterval(() => { if (camAllowed()) startCam(); else if (cam.on) stopCam(); }, 1500);
  document.addEventListener("visibilitychange", () => { if (document.hidden) stopCam(); });

  // ---------- frame loop ----------
  let prev = performance.now(), prevHeadX = 0;
  function frame(now) {
    const dt = Math.min(.05, (now - prev) / 1000); prev = now;
    if (document.hidden) { requestAnimationFrame(frame); return; }
    moodDecay(now);
    applyMood();
    track(now);
    saccade(now);
    blink(now);
    boredom(now);

    // mouth target
    if (talking && lip) {
      const e = (now - lip.start) / 1000;
      if (lip.tl && e < lip.est) {
        while (lip.idx < lip.tl.length - 1 && lip.tl[lip.idx + 1].at <= e) lip.idx++;
        const k = lip.tl[lip.idx];
        setMouth(k.v === "rest" ? curMood().rest : V[k.v], k.open);
      } else {
        // speech ran longer than estimated (or a sound effect): soft babble
        const i = Math.floor(e / .13);
        const v = BABBLE[(i * 7 + (i >> 2)) % BABBLE.length];
        setMouth(V[v], .55 + ((i * 37) % 10) / 25);
      }
    } else if (yawnUntil > now) {
      const p = 1 - (yawnUntil - now) / 2300;
      setMouth(V.yawn, p < .15 ? p / .15 : p > .8 ? (1 - p) / .2 : 1);
    }
    const mk = talking ? 1 : .6; // slower lips when not talking
    Object.values(M).forEach((s) => { s.k = 900 * mk; stepSpring(s, dt); });
    drawMouth();

    // eyes and head follow the gaze, the head a little behind
    Object.values(gaze).forEach((s) => stepSpring(s, dt));
    const s = st();
    head.x.t = gaze.x.x * .9 + tiltX * .3;
    head.y.t = gaze.y.x * .6 + tiltY * .2;
    head.tilt.t = s === "listening" ? 7 + level * .5 : s === "thinking" ? -5 : curMoodName() === "utangac" ? -6 : 0;
    Object.values(head).forEach((h) => stepSpring(h, dt));
    if (!reduce) {
      const pt = `translate(${(gaze.x.x * 4).toFixed(2)}px, ${(gaze.y.x * 4).toFixed(2)}px)`;
      pupilsAll.forEach((p) => (p.style.transform = pt));
      // pupils widen when she listens, sees a face or gets excited, shrink in bright surprise
      const n = curMoodName();
      pupil.t = s === "listening" || faceSeen(now) ? 3.6 : n === "saskin" ? 2.3 : n === "heyecanli" || n === "mutlu" ? 3.5 : 3;
      stepSpring(pupil, dt);
      pupilDots.forEach((p) => p.setAttribute("r", pupil.x.toFixed(2)));
      look.setAttribute("transform",
        `translate(${(head.x.x * 3).toFixed(2)} ${(head.y.x * 2).toFixed(2)}) rotate(${(head.x.x * 4 + head.tilt.x).toFixed(2)} 100 128)`);
      // accessories lag behind the head and wobble back
      const hv = (head.x.x - prevHeadX) / Math.max(dt, .001); prevHeadX = head.x.x;
      sway.t = 0; sway.v -= hv * dt * 260;
      stepSpring(sway, dt);
      const a = Math.max(-12, Math.min(12, sway.x));
      accs.forEach((g) => g.setAttribute("transform", `rotate(${a.toFixed(2)} 100 82)`));
      glare.forEach((g, i) => g.setAttribute("transform", `translate(${(-tiltX * 3 - head.x.x * 1.5).toFixed(2)} ${(-tiltY * 1.5).toFixed(2)}) ${glareBase[i]}`));
    }

    // antenna tip pulses with the mic level (no CSS filters: some WebViews make filtered SVG parts vanish)
    const target = s === "listening" ? level / 10 : 0;
    glow += (target - glow) * Math.min(1, dt * 12);
    if (tip) tip.setAttribute("transform", glow > .02 ? `translate(100 ${tipY}) scale(${(1 + glow * .5).toFixed(3)}) translate(-100 ${-tipY})` : "");
    level *= Math.pow(.02, dt);

    requestAnimationFrame(frame);
  }
  requestAnimationFrame(frame);
  }
})();
