// Shared robot drawing for the main screen (index.html) and the floating robot (bubble.html).
// Head items are drawn in the head's coordinates so they bob and tilt with it; outfits sit on the body.

const RING_COLORS = [["#3ddba4", "Yeşil"], ["#ff7eb6", "Pembe"], ["#a78bfa", "Mor"], ["#4fb3ff", "Mavi"], ["#ffa94d", "Turuncu"], ["#ffd43b", "Sarı"]];
const EYE_COLORS = [["#7ff5e0", "Turkuaz"], ["#ff9fd0", "Pembe"], ["#c4b5fd", "Lila"], ["#ffe066", "Sarı"]];

// Shop catalogue. slot: which part of the robot it goes on; price: stars it costs in the shop.
const ITEMS = {
  // outfits
  tisort: { slot: "outfit", name: "Çilekli tişört", icon: "🍓", price: 8 },
  okul: { slot: "outfit", name: "Okul forması", icon: "🎒", price: 10 },
  elbise: { slot: "outfit", name: "Pembe elbise", icon: "👗", price: 15 },
  tulum: { slot: "outfit", name: "Kot tulum", icon: "👖", price: 18 },
  pijama: { slot: "outfit", name: "Yıldızlı pijama", icon: "🌙", price: 20 },
  kazak: { slot: "outfit", name: "Kar tanesi kazak", icon: "❄️", price: 22 },
  kahraman: { slot: "outfit", name: "Süper kahraman", icon: "⚡", price: 30 },
  balerin: { slot: "outfit", name: "Balerin kıyafeti", icon: "🩰", price: 35 },
  prenses: { slot: "outfit", name: "Prenses elbisesi", icon: "👸", price: 45 },
  yagmurluk: { slot: "outfit", name: "Sarı yağmurluk", icon: "☔", price: 12, isNew: true },
  hirka: { slot: "outfit", name: "Kalpli hırka", icon: "💕", price: 14, isNew: true },
  forma: { slot: "outfit", name: "Futbol forması", icon: "⚽", price: 15, isNew: true },
  cicekli: { slot: "outfit", name: "Çiçekli elbise", icon: "🌼", price: 16, isNew: true },
  denizci: { slot: "outfit", name: "Denizci elbisesi", icon: "⚓", price: 20, isNew: true },
  doktor: { slot: "outfit", name: "Doktor önlüğü", icon: "🩺", price: 24, isNew: true },
  gokkusagi: { slot: "outfit", name: "Gökkuşağı elbise", icon: "🌈", price: 25, isNew: true },
  mont: { slot: "outfit", name: "Pofuduk mont", icon: "🧥", price: 26, isNew: true },
  astronot: { slot: "outfit", name: "Astronot kıyafeti", icon: "🚀", price: 32, isNew: true },
  peri: { slot: "outfit", name: "Peri elbisesi", icon: "🧚", price: 38, isNew: true },
  unicornsw: { slot: "outfit", name: "Unicorn sweatshirt", icon: "🦄", price: 40, isNew: true },
  // head
  fiyonk: { slot: "head", name: "Fiyonk", icon: "🎀", price: 0 },
  cicek: { slot: "head", name: "Çiçek toka", icon: "🌸", price: 5 },
  kulaklik: { slot: "head", name: "Kulaklık", icon: "🎧", price: 10 },
  kedi: { slot: "head", name: "Kedi kulakları", icon: "🐱", price: 12 },
  tavsan: { slot: "head", name: "Tavşan kulakları", icon: "🐰", price: 15 },
  sapka: { slot: "head", name: "Parti şapkası", icon: "🥳", price: 20 },
  cicektac: { slot: "head", name: "Çiçek taç", icon: "💐", price: 25 },
  unicorn: { slot: "head", name: "Unicorn boynuzu", icon: "🦄", price: 35 },
  tac: { slot: "head", name: "Prenses tacı", icon: "👑", price: 50 },
  yildiztoka: { slot: "head", name: "Yıldız toka", icon: "⭐", price: 6, isNew: true },
  bant: { slot: "head", name: "Fiyonklu taç bandı", icon: "💜", price: 8, isNew: true },
  bere: { slot: "head", name: "Ponponlu bere", icon: "🧶", price: 12, isNew: true },
  asci: { slot: "head", name: "Aşçı şapkası", icon: "👩‍🍳", price: 14, isNew: true },
  hasir: { slot: "head", name: "Hasır şapka", icon: "👒", price: 18, isNew: true },
  cadi: { slot: "head", name: "Sihirbaz şapkası", icon: "🪄", price: 22, isNew: true },
  peritaci: { slot: "head", name: "Gümüş peri tacı", icon: "✨", price: 30, isNew: true },
  // face
  allik: { slot: "face", name: "Kalp yanaklar", icon: "💗", price: 6 },
  gozluk: { slot: "face", name: "Pembe gözlük", icon: "👓", price: 15 },
  kalpgozluk: { slot: "face", name: "Kalp gözlük", icon: "😍", price: 20 },
  gunes: { slot: "face", name: "Güneş gözlüğü", icon: "😎", price: 25 },
  burun: { slot: "face", name: "Palyaço burnu", icon: "🔴", price: 8, isNew: true },
  parilti: { slot: "face", name: "Pırıltı yanaklar", icon: "✨", price: 9, isNew: true },
  biyik: { slot: "face", name: "Komik bıyık", icon: "🥸", price: 10, isNew: true },
  maske: { slot: "face", name: "Kelebek maske", icon: "🦋", price: 18, isNew: true },
  yildizgozluk: { slot: "face", name: "Yıldız gözlük", icon: "🤩", price: 22, isNew: true },
  // special items: not sold, only won (daily tasks, birthday)
  galaksi: { slot: "outfit", name: "Galaksi elbisesi", icon: "🪐", price: 0, exclusive: true },
  altinelbise: { slot: "outfit", name: "Altın pırıltılı elbise", icon: "🌟", price: 0, exclusive: true },
  dogumgunu: { slot: "outfit", name: "Doğum günü elbisesi", icon: "🎂", price: 0, exclusive: true },
  kalptac: { slot: "head", name: "Kalpli taç", icon: "💖", price: 0, exclusive: true },
  elmasgozluk: { slot: "face", name: "Elmas gözlük", icon: "💎", price: 0, exclusive: true },
  // pets that keep the robot company
  kedicik: { slot: "pet", name: "Kedicik", icon: "🐱", price: 10, isNew: true },
  kopekcik: { slot: "pet", name: "Köpecik", icon: "🐶", price: 10, isNew: true },
  tavsancik: { slot: "pet", name: "Tavşancık", icon: "🐰", price: 12, isNew: true },
  hamster: { slot: "pet", name: "Hamster", icon: "🐹", price: 12, isNew: true },
  tilki: { slot: "pet", name: "Tilki", icon: "🦊", price: 15, isNew: true },
  penguen: { slot: "pet", name: "Penguen", icon: "🐧", price: 18, isNew: true },
  panda: { slot: "pet", name: "Panda", icon: "🐼", price: 20, isNew: true },
  minikunicorn: { slot: "pet", name: "Minik unicorn", icon: "🦄", price: 40, isNew: true }
};
const SLOTS = ["outfit", "head", "face"]; // drawn on the robot; "pet" is shown beside it

const STAR = (cx, cy, r, fill) => {
  const pts = [];
  for (let i = 0; i < 10; i++) {
    const a = Math.PI / 5 * i - Math.PI / 2, rr = i % 2 ? r * 0.45 : r;
    pts.push((cx + rr * Math.cos(a)).toFixed(1) + "," + (cy + rr * Math.sin(a)).toFixed(1));
  }
  return `<polygon points="${pts.join(" ")}" fill="${fill}"/>`;
};
const STAR_OUTLINE = (cx, cy, r, extra) => STAR(cx, cy, r, "rgba(255,212,59,.25)").replace("/>", ` ${extra}/>`);
const SPARKLE = (cx, cy, r, fill) => `<path d="M${cx} ${cy - r} Q${cx + r * 0.2} ${cy - r * 0.2} ${cx + r} ${cy} Q${cx + r * 0.2} ${cy + r * 0.2} ${cx} ${cy + r} Q${cx - r * 0.2} ${cy + r * 0.2} ${cx - r} ${cy} Q${cx - r * 0.2} ${cy - r * 0.2} ${cx} ${cy - r} Z" fill="${fill}"/>`;
const FLOWER = (cx, cy, r, petal, mid) => [0, 72, 144, 216, 288].map((a) => `<circle cx="${(cx + r * Math.cos(a * Math.PI / 180)).toFixed(1)}" cy="${(cy + r * Math.sin(a * Math.PI / 180)).toFixed(1)}" r="${(r * 0.8).toFixed(1)}" fill="${petal}"/>`).join("") + `<circle cx="${cx}" cy="${cy}" r="${(r * 0.6).toFixed(1)}" fill="${mid}"/>`;
const HEART = (cx, cy, s, fill, extra = "") =>
  `<path d="M${cx} ${cy + s * 0.9} C${cx - s * 1.6} ${cy - s * 0.2} ${cx - s * 0.6} ${cy - s * 1.3} ${cx} ${cy - s * 0.35} C${cx + s * 0.6} ${cy - s * 1.3} ${cx + s * 1.6} ${cy - s * 0.2} ${cx} ${cy + s * 0.9} Z" fill="${fill}" ${extra}/>`;
const TORSO = "M60 214 L60 180 Q60 157 84 155 L116 155 Q140 157 140 180 L140 214 Z";
const torso = (fill) => `<path d="${TORSO}" fill="${fill}"/>`;

const OUTFITS = {
  tisort: torso("#ff6b6b") + `<path d="M88 155 Q100 166 112 155" stroke="#fff" stroke-width="3" fill="none"/>`
    + [[78, 178], [96, 186], [118, 176], [86, 200], [110, 198], [126, 194], [72, 194]].map(([x, y]) => `<ellipse cx="${x}" cy="${y}" rx="1.8" ry="2.6" fill="#ffe066"/>`).join("")
    + `<path d="M70 168 l5 -4 l5 4" stroke="#51cf66" stroke-width="2.5" fill="none" stroke-linecap="round"/>`,
  okul: torso("#1f3a68") + `<path d="M82 155 L100 176 L118 155 Z" fill="#fff"/><path d="M95 166 L105 166 L100 172 Z" fill="#e03131"/>`
    + `<circle cx="100" cy="186" r="2.2" fill="#fff"/><circle cx="100" cy="198" r="2.2" fill="#fff"/>`,
  elbise: torso("#ff8cc6") + `<circle cx="92" cy="159" r="7" fill="#fff"/><circle cx="108" cy="159" r="7" fill="#fff"/>`
    + `<path d="M64 196 Q100 206 136 196" stroke="#fff" stroke-width="3" fill="none" stroke-dasharray="4 4"/>`
    + `<circle cx="100" cy="176" r="2.4" fill="#fff"/><circle cx="100" cy="186" r="2.4" fill="#fff"/>`,
  tulum: torso("#f8f9fa") + `<path d="M70 172 L130 172 L130 214 L70 214 Z" fill="#4a7bd0"/>`
    + `<path d="M80 155 L82 173 M120 155 L118 173" stroke="#4a7bd0" stroke-width="6"/>`
    + `<circle cx="82" cy="174" r="3" fill="#ffd43b"/><circle cx="118" cy="174" r="3" fill="#ffd43b"/>`
    + `<rect x="90" y="182" width="20" height="14" rx="3" fill="none" stroke="#2f5aa8" stroke-width="2"/>`,
  pijama: torso("#2f3a72") + STAR(78, 176, 6, "#ffd43b") + STAR(118, 172, 5, "#ffd43b") + STAR(100, 196, 6, "#ffd43b") + STAR(128, 196, 4, "#ffe066")
    + `<path d="M108 182 a7 7 0 1 0 6 10 a5.5 5.5 0 1 1 -6 -10 Z" fill="#fff3bf"/>`
    + `<path d="M88 155 Q100 164 112 155" stroke="#a5b4fc" stroke-width="3" fill="none"/>`,
  kazak: torso("#8fd3ff") + [[82, 182], [116, 178], [100, 200]].map(([x, y]) =>
    `<g stroke="#fff" stroke-width="2" stroke-linecap="round"><path d="M${x - 6} ${y} L${x + 6} ${y} M${x} ${y - 6} L${x} ${y + 6} M${x - 4} ${y - 4} L${x + 4} ${y + 4} M${x - 4} ${y + 4} L${x + 4} ${y - 4}"/></g>`).join("")
    + `<path d="M84 155 Q100 162 116 155" stroke="#4dabf7" stroke-width="5" fill="none"/>`
    + `<path d="M60 168 L140 168" stroke="#fff" stroke-width="2" stroke-dasharray="3 3"/>`,
  kahraman: `<path d="M52 214 L58 162 L142 162 L148 214 Z" fill="#a61e4d"/>` + torso("#e63946")
    + `<circle cx="100" cy="184" r="13" fill="#ffd43b"/><path d="M103 173 L94 186 L100 186 L96 196 L106 182 L100 182 Z" fill="#e63946"/>`,
  balerin: torso("#ffc9de") + `<path d="M56 196 Q64 186 72 196 Q80 186 88 196 Q96 186 104 196 Q112 186 120 196 Q128 186 136 196 Q144 186 146 200 L146 214 L54 214 L54 200 Z" fill="#ffa8cc" opacity=".95"/>`
    + `<path d="M84 155 L84 170 M116 155 L116 170" stroke="#ff8cc6" stroke-width="3"/>` + HEART(100, 172, 5, "#ff5fa2"),
  prenses: torso("#b57edc") + `<circle cx="66" cy="168" r="12" fill="#d7b4f3"/><circle cx="134" cy="168" r="12" fill="#d7b4f3"/>`
    + `<path d="M80 156 Q100 176 120 156" stroke="#ffd43b" stroke-width="3" fill="none"/><circle cx="100" cy="168" r="4" fill="#ff5fa2" stroke="#ffd43b" stroke-width="1.5"/>`
    + `<path d="M62 200 L138 200" stroke="#ffd43b" stroke-width="3"/>` + STAR(84, 188, 3.5, "#fff3bf") + STAR(116, 188, 3.5, "#fff3bf"),
  yagmurluk: torso("#ffd43b") + `<path d="M84 155 L100 170 L116 155" fill="#fab005"/><path d="M100 170 L100 214" stroke="#f08c00" stroke-width="2"/>`
    + [176, 190, 204].map((y) => `<rect x="93" y="${y}" width="5" height="9" rx="2" fill="#8d5524"/><rect x="102" y="${y}" width="5" height="9" rx="2" fill="#8d5524"/>`).join("")
    + `<path d="M70 196 L84 196 M116 196 L130 196" stroke="#f08c00" stroke-width="2.5" stroke-linecap="round"/>`,
  hirka: torso("#ffa8cc") + `<path d="M92 155 L108 155 L106 214 L94 214 Z" fill="#fff"/>`
    + [172, 184, 196, 208].map((y) => `<circle cx="91" cy="${y}" r="1.8" fill="#e64980"/>`).join("")
    + HEART(76, 186, 5, "#e64980") + HEART(124, 182, 4.5, "#e64980") + HEART(122, 202, 3.5, "#fff") + HEART(78, 204, 3.5, "#fff"),
  doktor: torso("#f8f9fa") + `<path d="M90 155 L100 176 L110 155 Z" fill="#74c0fc"/><path d="M88 155 L100 186 M112 155 L100 186" stroke="#ced4da" stroke-width="2.5"/>`
    + `<path d="M84 158 Q78 182 92 192 M116 158 Q122 182 108 192" stroke="#495057" stroke-width="2.5" fill="none"/><circle cx="108" cy="194" r="4.5" fill="#868e96" stroke="#495057" stroke-width="1.5"/>`
    + `<rect x="114" y="196" width="14" height="10" rx="2" fill="#e9ecef"/><path d="M121 198 L121 204 M118 201 L124 201" stroke="#fa5252" stroke-width="2"/>`,
  mont: torso("#9775fa") + [170, 184, 198].map((y) => `<path d="M62 ${y} Q100 ${y + 5} 138 ${y}" stroke="#b197fc" stroke-width="2.5" fill="none"/>`).join("")
    + `<path d="M100 160 L100 214" stroke="#7048e8" stroke-width="2"/>`
    + [80, 88, 96, 104, 112, 120].map((x, i) => `<circle cx="${x}" cy="${156 + (i === 0 || i === 5 ? 2 : 0)}" r="6" fill="#fff"/>`).join(""),
  astronot: torso("#f1f3f5") + `<path d="M74 166 L74 214 M126 166 L126 214" stroke="#ced4da" stroke-width="2"/>`
    + `<rect x="86" y="174" width="28" height="20" rx="4" fill="#495057"/><circle cx="94" cy="181" r="2.6" fill="#fa5252"/><circle cx="101" cy="181" r="2.6" fill="#fcc419"/><circle cx="108" cy="181" r="2.6" fill="#4dabf7"/>`
    + `<rect x="91" y="187" width="18" height="3" rx="1.5" fill="#69db7c"/><circle cx="72" cy="168" r="7" fill="#364fc7"/>` + STAR(72, 168, 4, "#fff")
    + `<path d="M84 156 Q100 164 116 156" stroke="#adb5bd" stroke-width="4" fill="none"/>`,
  peri: `<ellipse cx="56" cy="178" rx="16" ry="24" fill="rgba(165,216,255,.75)" transform="rotate(-20 56 178)"/><ellipse cx="144" cy="178" rx="16" ry="24" fill="rgba(255,190,220,.75)" transform="rotate(20 144 178)"/>`
    + torso("#b2f2bb") + `<path d="M84 155 Q92 166 100 158 Q108 166 116 155" fill="#8ce99a"/>`
    + SPARKLE(80, 186, 4, "#fff") + SPARKLE(118, 192, 5, "#fff") + SPARKLE(100, 204, 3.5, "#fff9db") + SPARKLE(126, 176, 3, "#fff"),
  unicornsw: torso("#e5dbff") + `<path d="M84 155 Q100 162 116 155" stroke="#d0bfff" stroke-width="5" fill="none"/>`
    + `<path d="M80 196 A20 20 0 0 1 120 196" stroke="#ff8787" stroke-width="3" fill="none"/><path d="M84 196 A16 16 0 0 1 116 196" stroke="#ffd43b" stroke-width="3" fill="none"/><path d="M88 196 A12 12 0 0 1 112 196" stroke="#69db7c" stroke-width="3" fill="none"/><path d="M92 196 A8 8 0 0 1 108 196" stroke="#74c0fc" stroke-width="3" fill="none"/>`
    + `<path d="M96 184 L100 166 L104 184 Z" fill="#ffd43b" stroke="#f59f00" stroke-width="1"/>` + STAR(76, 174, 3.5, "#ff8cc6") + STAR(124, 178, 3, "#a78bfa")
};
// Patterned outfits get clipped to the body shape so stripes never spill over the shoulders.
const OUTFITS_CLIPPED = {
  gokkusagi: ["#ff8787", "#ffa94d", "#ffd43b", "#69db7c", "#74c0fc", "#b197fc"].map((c, i) => `<rect x="56" y="${152 + i * 10}" width="90" height="11" fill="${c}"/>`).join("")
    + `<path d="M86 155 Q100 164 114 155" stroke="#fff" stroke-width="3" fill="none"/>`,
  cicekli: `<rect x="56" y="150" width="90" height="70" fill="#ffe066"/>` + [[76, 174], [110, 170], [92, 192], [124, 196], [70, 202]].map(([x, y]) => FLOWER(x, y, 4, "#fff", "#ff922b")).join("")
    + `<circle cx="92" cy="158" r="7" fill="#fff"/><circle cx="108" cy="158" r="7" fill="#fff"/>`,
  denizci: `<rect x="56" y="150" width="90" height="70" fill="#f8f9fa"/><path d="M68 155 L100 186 L132 155 L140 166 L100 198 L60 166 Z" fill="#1c3d78"/>`
    + `<path d="M72 162 L100 190 L128 162" stroke="#fff" stroke-width="2" fill="none"/><path d="M93 190 L107 190 L100 204 Z" fill="#e03131"/>`,
  galaksi: `<rect x="56" y="150" width="90" height="70" fill="#1b1b4b"/><circle cx="80" cy="182" r="9" fill="#ff922b"/><ellipse cx="80" cy="182" rx="15" ry="4" fill="none" stroke="#ffd8a8" stroke-width="2"/>`
    + `<circle cx="118" cy="194" r="6" fill="#74c0fc"/><circle cx="116" cy="192" r="2" fill="#a5d8ff"/>` + STAR(104, 170, 3, "#fff") + STAR(126, 172, 2.5, "#ffe066") + STAR(70, 204, 2.5, "#fff") + STAR(98, 206, 2, "#fff")
    + `<path d="M86 155 Q100 164 114 155" stroke="#b197fc" stroke-width="3" fill="none"/>`,
  altinelbise: `<rect x="56" y="150" width="90" height="70" fill="#ffd43b"/><rect x="56" y="150" width="90" height="70" fill="url(#none)" opacity="0"/>`
    + `<path d="M56 170 L146 160 M56 190 L146 180 M56 210 L146 200" stroke="#fff3bf" stroke-width="3" opacity=".7"/>`
    + SPARKLE(78, 178, 4, "#fff") + SPARKLE(120, 186, 5, "#fff") + SPARKLE(96, 202, 3.5, "#fff") + SPARKLE(130, 166, 3, "#fff")
    + `<circle cx="92" cy="158" r="6.5" fill="#fff"/><circle cx="108" cy="158" r="6.5" fill="#fff"/>`,
  dogumgunu: `<rect x="56" y="150" width="90" height="70" fill="#ff8cc6"/>` + [[66, 170, "#ffd43b"], [128, 168, "#4fb3ff"], [72, 204, "#3ddba4"], [132, 204, "#fff"], [110, 162, "#a78bfa"]].map(([x, y, c]) => `<circle cx="${x}" cy="${y}" r="2.5" fill="${c}"/>`).join("")
    + `<rect x="86" y="182" width="28" height="16" rx="3" fill="#fff"/><rect x="86" y="182" width="28" height="5" fill="#ffa8cc"/><rect x="98" y="172" width="4" height="10" fill="#4fb3ff"/><path d="M100 166 Q103 170 100 172 Q97 170 100 166 Z" fill="#ff922b"/>`,
  forma: [56, 72, 88, 104, 120, 136].map((x, i) => `<rect x="${x}" y="150" width="16" height="70" fill="${i % 2 ? "#fff" : "#e03131"}"/>`).join("")
    + `<path d="M86 155 Q100 166 114 155" stroke="#212529" stroke-width="3" fill="none"/>`
    + `<text x="100" y="202" font-size="20" font-weight="900" font-family="sans-serif" text-anchor="middle" fill="#212529" stroke="#fff" stroke-width="1">10</text>`
};

const HEAD_BACK = {
  tavsan: `<g transform="rotate(-12 80 60)"><ellipse cx="80" cy="50" rx="9" ry="24" fill="var(--head)"/><ellipse cx="80" cy="52" rx="4.5" ry="17" fill="#ffc9de"/></g>`
    + `<g transform="rotate(12 120 60)"><ellipse cx="120" cy="50" rx="9" ry="24" fill="var(--head)"/><ellipse cx="120" cy="52" rx="4.5" ry="17" fill="#ffc9de"/></g>`,
  kulaklik: `<path d="M50 104 Q48 46 100 46 Q152 46 150 104" stroke="var(--acc2)" stroke-width="7" fill="none" stroke-linecap="round"/>`
};
const HEAD_FRONT = {
  kalptac: `<path d="M70 80 Q100 70 130 80" stroke="#ffd43b" stroke-width="5" fill="none"/>` + HEART(80, 70, 7, "#ff5fa2") + HEART(100, 62, 9, "#ff2d7a") + HEART(120, 70, 7, "#ff5fa2")
    + SPARKLE(100, 46, 3.5, "#fff"),
  yildiztoka: STAR(133, 76, 9, "#ffd43b") + STAR(120, 69, 5, "#ffe066") + `<circle cx="131" cy="73" r="1.6" fill="#fff"/>`,
  bant: `<path d="M57 90 Q100 56 143 90" stroke="#a78bfa" stroke-width="6" fill="none" stroke-linecap="round"/>`
    + `<path d="M124 68 L112 60 L112 76 Z" fill="#da77f2"/><path d="M124 68 L136 60 L136 76 Z" fill="#da77f2"/><circle cx="124" cy="68" r="4" fill="#f3d9fa"/>`,
  bere: `<path d="M54 86 Q54 42 100 42 Q146 42 146 86 Z" fill="#ff8cc6"/><rect x="52" y="78" width="96" height="12" rx="6" fill="#f06595"/>`
    + [62, 72, 82, 92, 102, 112, 122, 132].map((x) => `<path d="M${x + 2} 79 L${x + 2} 89" stroke="#ffa8cc" stroke-width="2"/>`).join("")
    + `<circle cx="100" cy="40" r="10" fill="#fff"/><circle cx="96" cy="37" r="3" fill="#f8f9fa"/>`,
  asci: `<rect x="72" y="62" width="56" height="16" rx="3" fill="#f8f9fa" stroke="#dee2e6" stroke-width="1.5"/>`
    + `<circle cx="80" cy="50" r="13" fill="#fff"/><circle cx="100" cy="42" r="15" fill="#fff"/><circle cx="120" cy="50" r="13" fill="#fff"/>`
    + `<path d="M84 66 L84 76 M100 66 L100 76 M116 66 L116 76" stroke="#e9ecef" stroke-width="2"/>`,
  hasir: `<ellipse cx="100" cy="76" rx="54" ry="9" fill="#e9c46a"/><path d="M70 76 Q70 44 100 44 Q130 44 130 76 Z" fill="#f4d58d"/>`
    + `<rect x="70" y="66" width="60" height="7" fill="#ff8cc6"/>` + FLOWER(124, 68, 4, "#fff", "#ffd43b")
    + `<path d="M74 60 Q100 57 126 60" stroke="#e9c46a" stroke-width="1.5" fill="none" opacity=".7"/>`,
  cadi: `<ellipse cx="100" cy="76" rx="48" ry="8" fill="#5f3dc4"/><path d="M74 76 L102 22 Q118 18 124 30 Q112 28 108 36 L126 76 Z" fill="#7950f2"/>`
    + `<rect x="78" y="64" width="46" height="7" fill="#ffd43b"/>` + STAR(104, 46, 5, "#ffe066") + STAR(116, 58, 3, "#fff3bf"),
  peritaci: `<path d="M72 78 Q76 64 86 70 Q92 56 100 52 Q108 56 114 70 Q124 64 128 78" fill="none" stroke="#ced4da" stroke-width="3.5" stroke-linejoin="round"/>`
    + `<circle cx="100" cy="58" r="4" fill="#da77f2"/><circle cx="86" cy="70" r="3" fill="#74c0fc"/><circle cx="114" cy="70" r="3" fill="#74c0fc"/>`
    + SPARKLE(100, 46, 4, "#fff") + SPARKLE(80, 62, 2.5, "#fff") + SPARKLE(120, 62, 2.5, "#fff"),
  kulaklik: `<g fill="var(--acc2)"><rect x="38" y="88" width="22" height="42" rx="10"/><rect x="140" y="88" width="22" height="42" rx="10"/></g>`,
  fiyonk: `<path d="M136 74 L119 61 L119 87 Z" fill="#ff5fa2"/><path d="M136 74 L153 61 L153 87 Z" fill="#ff5fa2"/><circle cx="136" cy="74" r="5.5" fill="#ff9cc8"/>`,
  cicek: [[64, 68], [73, 73], [70, 83], [58, 83], [55, 73]].map(([x, y]) => `<circle cx="${x}" cy="${y}" r="6" fill="#ffa8d2"/>`).join("") + `<circle cx="64" cy="76" r="4.5" fill="#ffd43b"/>`,
  kedi: `<path d="M57 84 L62 54 L84 74 Z" fill="var(--head)"/><path d="M63 76 L65 62 L76 72 Z" fill="#ffb3cf"/>`
    + `<path d="M143 84 L138 54 L116 74 Z" fill="var(--head)"/><path d="M137 76 L135 62 L124 72 Z" fill="#ffb3cf"/>`,
  sapka: `<path d="M78 76 L122 76 L100 26 Z" fill="#a78bfa"/><path d="M86 58 L114 58 M92 44 L108 44" stroke="#ffd43b" stroke-width="4"/><circle cx="100" cy="26" r="6.5" fill="#ff7eb6"/>`,
  cicektac: [[68, 74, "#ff8cc6"], [84, 68, "#ffd43b"], [100, 66, "#a78bfa"], [116, 68, "#4fb3ff"], [132, 74, "#ff8cc6"]].map(([x, y, c]) =>
    [0, 72, 144, 216, 288].map((a) => `<circle cx="${(x + 4.5 * Math.cos(a * Math.PI / 180)).toFixed(1)}" cy="${(y + 4.5 * Math.sin(a * Math.PI / 180)).toFixed(1)}" r="3.6" fill="${c}"/>`).join("")
    + `<circle cx="${x}" cy="${y}" r="2.6" fill="#fff"/>`).join(""),
  unicorn: `<path d="M92 76 L100 32 L108 76 Z" fill="#ffd43b" stroke="#f59f00" stroke-width="1.2"/><path d="M95 64 L105 60 M96 54 L104 50 M98 44 L102 42" stroke="#f59f00" stroke-width="2"/>`
    + `<circle cx="84" cy="74" r="4" fill="#ffa8d2"/><circle cx="116" cy="74" r="4" fill="#a5d8ff"/>`,
  tac: `<path d="M74 77 L78 50 L90 65 L100 44 L110 65 L122 50 L126 77 Z" fill="#ffd43b" stroke="#f59f00" stroke-width="1.5" stroke-linejoin="round"/>`
    + `<circle cx="100" cy="60" r="3.5" fill="#ff5fa2"/><circle cx="84" cy="67" r="2.8" fill="#4fb3ff"/><circle cx="116" cy="67" r="2.8" fill="#4fb3ff"/>`
};
const FACE = {
  elmasgozluk: `<g stroke="#66d9e8" stroke-width="3" fill="rgba(153,233,242,.3)" stroke-linejoin="round"><path d="M84.5 89 L98 104 L84.5 119 L71 104 Z"/><path d="M115.5 89 L129 104 L115.5 119 L102 104 Z"/></g>`
    + SPARKLE(70, 90, 3, "#fff") + SPARKLE(130, 90, 3, "#fff"),
  burun: `<circle cx="100" cy="128" r="6" fill="#ff4757"/><circle cx="98" cy="126" r="1.8" fill="#fff" opacity=".8"/>`,
  parilti: SPARKLE(66, 132, 4.5, "#ffd43b") + SPARKLE(136, 130, 4.5, "#ffd43b") + SPARKLE(142, 120, 2.5, "#fff3bf") + SPARKLE(60, 122, 2.5, "#fff3bf"),
  biyik: `<path d="M100 132 Q92 125 82 130 Q78 133 80 137 Q84 133 90 135 Q96 137 100 134 Q104 137 110 135 Q116 133 120 137 Q122 133 118 130 Q108 125 100 132 Z" fill="#6b4226"/>`,
  maske: `<path fill-rule="evenodd" fill="#b197fc" d="M62 100 Q66 86 84 90 Q94 92 100 98 Q106 92 116 90 Q134 86 138 100 Q136 116 118 118 Q106 118 100 110 Q94 118 82 118 Q64 116 62 100 Z M78 104 a7 9 0 1 0 14 0 a7 9 0 1 0 -14 0 Z M108 104 a7 9 0 1 0 14 0 a7 9 0 1 0 -14 0 Z"/>`
    + SPARKLE(66, 92, 3, "#fff") + SPARKLE(134, 92, 3, "#fff"),
  yildizgozluk: STAR_OUTLINE(84.5, 104, 16, 'stroke="#fab005" stroke-width="2.5" stroke-linejoin="round"') + STAR_OUTLINE(115.5, 104, 16, 'stroke="#fab005" stroke-width="2.5" stroke-linejoin="round"'),
  allik: HEART(70, 136, 5, "#ff7eb6") + HEART(130, 136, 5, "#ff7eb6"),
  gozluk: `<g stroke="#ff5fa2" stroke-width="3.5" fill="rgba(255,255,255,.08)"><circle cx="84.5" cy="104" r="14"/><circle cx="115.5" cy="104" r="14"/><path d="M98.5 104 L101.5 104" fill="none"/></g>`,
  kalpgozluk: HEART(84.5, 104, 11, "rgba(255,95,162,.45)", 'stroke="#ff2d7a" stroke-width="2.5"') + HEART(115.5, 104, 11, "rgba(255,95,162,.45)", 'stroke="#ff2d7a" stroke-width="2.5"')
    + `<path d="M97 101 L103 101" stroke="#ff2d7a" stroke-width="2.5"/>`,
  gunes: `<rect x="70" y="93" width="28" height="22" rx="9" fill="#212529"/><rect x="102" y="93" width="28" height="22" rx="9" fill="#212529"/>`
    + `<path d="M98 100 L102 100" stroke="#212529" stroke-width="3"/><path d="M76 98 L84 98 M108 98 L116 98" stroke="#fff" stroke-width="2.5" stroke-linecap="round" opacity=".7"/>`
};

const group = (cls, map) => Object.entries(map).map(([k, svg]) => `<g class="${cls} ${cls}-${k}">${svg}</g>`).join("");

let robotCount = 0;
/**
 * Returns a fresh robot drawing; each copy gets its own gradient ids so several robots can share a page.
 * opts.full draws the whole robot with little feet (for the camera view) instead of the round portrait crop.
 */
function robotSvg(opts = {}) {
  const id = "rb" + (++robotCount);
  const feet = opts.full ? `<ellipse cx="82" cy="219" rx="14" ry="8" fill="var(--head-shade)"/><ellipse cx="118" cy="219" rx="14" ry="8" fill="var(--head-shade)"/>
        <ellipse cx="78" cy="216" rx="6" ry="2.5" fill="#fff" opacity=".6"/><ellipse cx="114" cy="216" rx="6" ry="2.5" fill="#fff" opacity=".6"/>` : "";
  return `
<svg class="face" viewBox="${opts.full ? "0 0 200 222" : "0 0 200 200"}">
  <defs>
    <linearGradient id="${id}head" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#ffffff"/><stop offset=".55" style="stop-color:var(--head)"/><stop offset="1" style="stop-color:var(--head-shade)"/></linearGradient>
    <linearGradient id="${id}visor" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#2c3d5c"/><stop offset=".5" style="stop-color:var(--visor)"/><stop offset="1" stop-color="#0d1322"/></linearGradient>
    <linearGradient id="${id}ear" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#ffffff" stop-opacity=".6"/><stop offset=".4" style="stop-color:var(--ear)"/><stop offset="1" style="stop-color:var(--ear)"/></linearGradient>
    <radialGradient id="${id}tip" cx=".35" cy=".35" r=".7"><stop offset="0" stop-color="#ffffff"/><stop offset="1" style="stop-color:var(--ring)"/></radialGradient>
    <radialGradient id="${id}eye" cx=".5" cy=".4" r=".7"><stop offset="0" stop-color="#ffffff" stop-opacity=".9"/><stop offset=".35" style="stop-color:var(--eye)"/><stop offset="1" style="stop-color:var(--eye)"/></radialGradient>
    <clipPath id="${id}torso"><path d="${TORSO}"/></clipPath>
    <clipPath id="${id}mouth"><path class="mouth-clip" d="M88 135 Q100 143 112 135 Q100 143 88 135 Z"/></clipPath>
  </defs>
  <g transform="translate(100 88) scale(1.06) translate(-100 -104)">
    <g class="flip" id="flip">
      <g class="body-group">
        ${feet}
        <rect x="92" y="144" width="16" height="14" rx="4" fill="var(--head-shade)"/>
        <path d="${TORSO}" fill="url(#${id}head)"/>
        <ellipse cx="78" cy="166" rx="9" ry="4" fill="#fff" opacity=".6" transform="rotate(-20 78 166)"/>
        <circle cx="100" cy="184" r="9" fill="var(--visor)"/>
        ${HEART(100, 184, 4.6, "var(--ring)")}
        ${group("o", OUTFITS)}
        <g clip-path="url(#${id}torso)">${group("o", OUTFITS_CLIPPED)}</g>
      </g>
      <g class="head-look"><g class="head-group head">
        <g class="acc-sway">${group("hb", HEAD_BACK)}</g>
        <g class="antenna">
          <rect x="97" y="54" width="6" height="22" rx="3" fill="var(--head-shade)"/>
          <circle cx="100" cy="51" r="11" fill="var(--ring)" opacity=".25"/>
          <circle class="antenna-tip tip" cx="100" cy="51" r="7" fill="url(#${id}tip)"/>
        </g>
        <rect x="43" y="91" width="15" height="36" rx="7.5" fill="url(#${id}ear)"/>
        <rect x="142" y="91" width="15" height="36" rx="7.5" fill="url(#${id}ear)"/>
        <circle cx="50.5" cy="109" r="3" fill="#fff" opacity=".7"/><circle cx="149.5" cy="109" r="3" fill="#fff" opacity=".7"/>
        <rect x="54" y="72" width="92" height="76" rx="26" fill="url(#${id}head)" stroke="#ffffff" stroke-opacity=".7" stroke-width="1.2"/>
        <ellipse class="glare" cx="76" cy="79" rx="14" ry="4.5" fill="#fff" opacity=".85" transform="rotate(-10 76 79)"/>
        <rect x="63" y="83" width="74" height="44" rx="18" fill="url(#${id}visor)"/>
        <path class="glare" d="M70 96 Q71 89 79 88" stroke="#fff" stroke-opacity=".28" stroke-width="3" stroke-linecap="round" fill="none"/>
        <g class="brows" stroke="var(--eye)" stroke-width="3" stroke-linecap="round" fill="none">
          <path class="brow brow-l" d="M77 89 Q84 86.5 91 89"/><path class="brow brow-r" d="M109 89 Q116 86.5 123 89"/>
        </g>
        <g class="eyes"><g class="pupils">
          <rect class="eye" x="77" y="94" width="14" height="19" rx="6.5" fill="url(#${id}eye)"/>
          <rect class="eye" x="109" y="94" width="14" height="19" rx="6.5" fill="url(#${id}eye)"/>
          <circle cx="81.5" cy="99" r="2.4" fill="#fff"/><circle cx="113.5" cy="99" r="2.4" fill="#fff"/>
          <circle cx="87" cy="108" r="1.1" fill="#fff" opacity=".8"/><circle cx="119" cy="108" r="1.1" fill="#fff" opacity=".8"/>
          <g stroke="var(--eye)" stroke-width="1.8" stroke-linecap="round">
            <path d="M78 97 L74 93.5"/><path d="M80 95 L78 90.5"/><path d="M122 97 L126 93.5"/><path d="M120 95 L122 90.5"/>
          </g>
        </g></g>
        <g class="sleep-eyes" stroke="var(--eye)" stroke-width="3.5" stroke-linecap="round" fill="none">
          <path d="M77 105 Q84 110 91 105"/><path d="M109 105 Q116 110 123 105"/>
          <path d="M78 106 L75 109.5" stroke-width="1.8"/><path d="M122 106 L125 109.5" stroke-width="1.8"/>
        </g>
        <g class="cheeks" fill="var(--cheek)">
          <ellipse cx="69" cy="136" rx="6.5" ry="3.8"/>
          <ellipse cx="131" cy="136" rx="6.5" ry="3.8"/>
        </g>
        <path class="mouth" d="M88 135 Q100 143 112 135 Q100 143 88 135 Z" stroke="var(--visor)" stroke-width="4" fill="var(--visor)" stroke-linecap="round" stroke-linejoin="round"/>
        <ellipse class="tongue" cx="100" cy="140" rx="0" ry="0" fill="#ff8fab" clip-path="url(#${id}mouth)"/>
        ${group("f", FACE)}
        <g class="acc-sway">${group("h", HEAD_FRONT)}</g>
      </g></g>
    </g>
  </g>
</svg>`;
}
// Older pages read ROBOT_SVG as a string; each read now gets its own copy.
Object.defineProperty(window, "ROBOT_SVG", { get: robotSvg });

(function injectRobotStyle() {
  const ids = (slot) => Object.keys(ITEMS).filter((k) => ITEMS[k].slot === slot);
  const st = document.createElement("style");
  st.textContent = `
    .o, .h, .hb, .f { display: none; }
    ${ids("outfit").map((k) => `svg[data-outfit="${k}"] .o-${k}{display:inline}`).join("\n")}
    ${ids("head").map((k) => `svg[data-head="${k}"] .h-${k}, svg[data-head="${k}"] .hb-${k}{display:inline}`).join("\n")}
    ${ids("face").map((k) => `svg[data-face="${k}"] .f-${k}{display:inline}`).join("\n")}
    ${["sapka", "tac", "unicorn", "bere", "asci", "hasir", "cadi", "peritaci"].map((k) => `svg[data-head="${k}"] .antenna`).join(", ")} { display: none; }
    svg[data-face="gunes"] .eyes, svg[data-face="gunes"] .sleep-eyes { visibility: hidden; }
    svg[data-face="allik"] .cheeks { display: none; }
    .sleep-eyes { display: none; }
    body.sleeping .sleep-eyes { display: inline; }
    body.sleeping .eyes { display: none; }
    .brows { opacity: 0; transition: opacity .3s; }
    .brow { transform-box: fill-box; transform-origin: center; transition: transform .35s cubic-bezier(.3,1.5,.5,1); }
    body.sleeping .brows { opacity: 0 !important; }`;
  document.head.appendChild(st);
})();

function hexToRgba(hex, a) {
  const n = parseInt(hex.slice(1), 16);
  return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${a})`;
}

/** Converts looks saved by older versions ({acc}) to the slot format. */
function normalizeLook(look) {
  look = Object.assign({}, look || {});
  if (look.acc) {
    if (look.acc !== "none" && ITEMS[look.acc]) look[ITEMS[look.acc].slot] = look.acc;
    delete look.acc;
  }
  return look;
}

/** look = {ring, eye, outfit, head, face}; applies to every robot drawing on the page (or only inside `root`). */
function applyLook(look, root) {
  if (typeof look === "string") { try { look = JSON.parse(look || "{}"); } catch (e) { look = {}; } }
  look = normalizeLook(look);
  const ring = look.ring || RING_COLORS[0][0], eye = look.eye || EYE_COLORS[0][0];
  const style = root ? root.style : document.documentElement.style;
  style.setProperty("--ring", ring);
  style.setProperty("--ring-glow-base", hexToRgba(ring, 0.38));
  style.setProperty("--eye", eye);
  style.setProperty("--acc2", ring);
  (root || document).querySelectorAll("svg.face").forEach((svg) => {
    SLOTS.forEach((slot) => { svg.dataset[slot] = look[slot] && ITEMS[look[slot]] ? look[slot] : "none"; });
  });
}
