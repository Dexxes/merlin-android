// PDF-Reader in der WebView von PdfReaderView.kt, Gegenstück zu PDFArticleView.swift (PDFKit) auf iOS.
//
// pdf.js rendert jede Seite in ein Canvas und legt den Text-Layer darüber, damit Text wie im
// Text-Reader ausgewählt werden kann. Markierungen werden nie in die Datei geschrieben, sondern wie
// im Web-Reader und auf iOS als Seite + Rechtecke gespeichert (`pdfPage`, `pdfRects`: `[x, y, w, h]`,
// 0..1 bezogen auf die unrotierte CropBox, Ursprung oben links, eine Zeile je Rechteck).
//
// Kotlin → JS: window.MerlinPdf.{open, setHighlights, mark, comment, clearSelection, reveal, find,
// findStep, clearFind}. JS → Kotlin: window.MerlinPdfBridge (siehe PdfJsBridge in PdfReaderView.kt).

import * as pdfjsLib from '../pdfjs/pdf.min.mjs';

pdfjsLib.GlobalWorkerOptions.workerSrc = '../pdfjs/pdf.worker.min.mjs';

const bridge = window.MerlinPdfBridge || {
  onDocumentReady() {}, onLayout() {}, onLoadFailed() {}, onSelectionChanged() {}, onSelectionCleared() {},
  onCreateHighlight() {}, onCommentAnchor() {}, onHighlightTap() {}, onOpenComments() {}, onFindResult() {},
  onOpenLink() {}, onTap() {}, onScriptReady() {},
};

// Wie die COLORS-Tabelle im Text-Reader und im Web.
const COLORS = { yellow: '#fde68a', green: '#bbf7d0', blue: '#bfdbfe', pink: '#fbcfe8', orange: '#fed7aa' };
const COMMENT_COLOR = 'comment';
const MAX_CANVAS_PIXELS = 8 * 1024 * 1024;
const MAX_RECTS = 200;

const viewer = document.getElementById('viewer');
let doc = null;
/** Je Seite: { num, el, page, vp1, view, canvas, renderedScale, task, textEl, textDivs, textInfo, textTask, hlEl, findEl, linkEl, links } */
let pages = [];
let highlights = [];
let counts = {};
let canMark = false;
/** Gezeichnete Markierungen je Seite: [{ id, color, rects: [{l,t,w,h}] }] (Bruchteile der Seitenbox). */
const drawn = new Map();

// ── Öffnen und Layout ──────────────────────────────────────────────────────

async function open(url, options) {
  options = options || {};
  if (options.background) document.documentElement.style.setProperty('--merlin-bg', options.background);
  try {
    doc = await pdfjsLib.getDocument({
      url,
      // Kein `eval`/`new Function` für Schriften (CVE-2024-4367), keine XFA-Formulare.
      isEvalSupported: false,
      enableXfa: false,
      cMapUrl: '../pdfjs/cmaps/',
      cMapPacked: true,
      standardFontDataUrl: '../pdfjs/standard_fonts/',
    }).promise;
  } catch (e) {
    bridge.onLoadFailed(e && e.name === 'PasswordException' ? 'encrypted' : 'failed');
    return;
  }
  const count = doc.numPages;
  pages = [];
  for (let i = 1; i <= count; i++) {
    const page = await doc.getPage(i);
    const el = document.createElement('div');
    el.className = 'page';
    el.dataset.page = String(i);
    const hlEl = document.createElement('div');
    hlEl.className = 'hlLayer';
    const findEl = document.createElement('div');
    findEl.className = 'findLayer';
    const linkEl = document.createElement('div');
    linkEl.className = 'linkLayer';
    const spinner = document.createElement('div');
    spinner.className = 'spinner';
    el.append(spinner, hlEl, findEl, linkEl);
    viewer.appendChild(el);
    pages.push({
      num: i, el, page, vp1: page.getViewport({ scale: 1 }), view: page.view, spinner,
      canvas: null, renderedScale: 0, task: null,
      textEl: null, textDivs: null, textInfo: null, textTask: null,
      hlEl, findEl, linkEl, links: null,
    });
  }
  layout();
  pages.forEach(drawHighlightsOn);
  observer = new IntersectionObserver(onIntersect, { root: null, rootMargin: '150% 0px' });
  pages.forEach(p => observer.observe(p.el));
  bridge.onDocumentReady(count);
  bridge.onLayout();
}

let cssWidth = 0;

/** Seitenbreite = Fensterbreite minus Rand; Höhe aus dem Seitenverhältnis (vor dem ersten Rendern). */
function layout() {
  const width = Math.max(1, document.documentElement.clientWidth - 16);
  if (width === cssWidth) return false;
  cssWidth = width;
  for (const p of pages) {
    const scale = width / p.vp1.width;
    p.el.style.width = width + 'px';
    p.el.style.height = Math.round(p.vp1.height * scale) + 'px';
    p.el.style.setProperty('--scale-factor', String(scale));
    p.cssScale = scale;
    // Neue Breite: alles neu rendern (Text-Layer misst in Pixeln).
    releaseCanvas(p);
    releaseText(p, true);
  }
  return true;
}

let resizeTimer = null;
window.addEventListener('resize', () => {
  clearTimeout(resizeTimer);
  resizeTimer = setTimeout(() => {
    const range = document.documentElement.scrollHeight - window.innerHeight;
    const fraction = range > 0 ? window.scrollY / range : 0;
    if (layout()) {
      const next = document.documentElement.scrollHeight - window.innerHeight;
      window.scrollTo(0, fraction * Math.max(0, next));
      schedule();
    }
  }, 150);
});

// Pinch-Zoom: sichtbare Seiten nach dem Zoomen schärfer (bzw. wieder sparsamer) neu rendern.
let zoomTimer = null;
if (window.visualViewport) {
  window.visualViewport.addEventListener('resize', () => {
    clearTimeout(zoomTimer);
    zoomTimer = setTimeout(schedule, 300);
  });
}

function zoomFactor() {
  const s = window.visualViewport ? window.visualViewport.scale : 1;
  return Math.min(Math.max(s || 1, 1), 5);
}

// ── Rendern (nur Seiten in Bildschirmnähe) ─────────────────────────────────

let observer = null;
const near = new Set();

function onIntersect(entries) {
  for (const entry of entries) {
    const p = pages[Number(entry.target.dataset.page) - 1];
    if (!p) continue;
    if (entry.isIntersecting) {
      near.add(p);
    } else {
      near.delete(p);
      releaseCanvas(p);
      releaseText(p, false);
    }
  }
  schedule();
}

let rendering = false;

function schedule() {
  if (rendering) { pendingSchedule = true; return; }
  renderNext();
}
let pendingSchedule = false;

async function renderNext() {
  rendering = true;
  pendingSchedule = false;
  try {
    // Nächstgelegene Seite zuerst.
    const mid = window.scrollY + window.innerHeight / 2;
    const list = Array.from(near).sort((a, b) =>
      Math.abs(a.el.offsetTop + a.el.offsetHeight / 2 - mid) - Math.abs(b.el.offsetTop + b.el.offsetHeight / 2 - mid));
    for (const p of list) {
      if (!near.has(p)) continue;
      if (needsCanvas(p)) await renderCanvas(p);
      if (!p.textEl) await renderText(p);
      if (!p.links) renderLinks(p);
    }
  } finally {
    rendering = false;
    if (pendingSchedule) renderNext();
  }
}

function targetScale(p) {
  const dpr = window.devicePixelRatio || 1;
  let scale = p.cssScale * dpr * zoomFactor();
  const pixels = p.vp1.width * p.vp1.height * scale * scale;
  if (pixels > MAX_CANVAS_PIXELS) scale *= Math.sqrt(MAX_CANVAS_PIXELS / pixels);
  return scale;
}

function needsCanvas(p) {
  if (!p.canvas) return true;
  const target = targetScale(p);
  return Math.abs(target - p.renderedScale) / target > 0.2;
}

async function renderCanvas(p) {
  const scale = targetScale(p);
  const viewport = p.page.getViewport({ scale });
  const canvas = document.createElement('canvas');
  canvas.width = Math.max(1, Math.floor(viewport.width));
  canvas.height = Math.max(1, Math.floor(viewport.height));
  const task = p.page.render({ canvasContext: canvas.getContext('2d', { alpha: false }), viewport });
  p.task = task;
  try {
    await task.promise;
  } catch (e) {
    canvas.width = canvas.height = 0;
    return;
  } finally {
    if (p.task === task) p.task = null;
  }
  if (!near.has(p)) { canvas.width = canvas.height = 0; return; }
  // Erst nach dem Rendern tauschen: beim Nachschärfen bleibt die alte Fassung bis dahin stehen.
  if (p.canvas) { p.canvas.width = p.canvas.height = 0; p.canvas.remove(); }
  p.el.insertBefore(canvas, p.el.firstChild);
  p.canvas = canvas;
  p.renderedScale = scale;
  if (p.spinner) { p.spinner.remove(); p.spinner = null; }
}

function releaseCanvas(p) {
  if (p.task) { p.task.cancel(); p.task = null; }
  if (p.canvas) {
    p.canvas.width = p.canvas.height = 0;
    p.canvas.remove();
    p.canvas = null;
    p.renderedScale = 0;
  }
}

async function textContentOf(p) {
  if (!p.textContent) p.textContent = await p.page.getTextContent();
  return p.textContent;
}

async function renderText(p) {
  if (p.textEl) return;
  if (p.textTask && p.textTaskGen === p.textGen) return p.textTask;
  const gen = p.textTaskGen = p.textGen = (p.textGen || 0) + 1;
  const task = (async () => {
    const content = await textContentOf(p);
    const el = document.createElement('div');
    el.className = 'textLayer';
    const layer = new pdfjsLib.TextLayer({
      textContentSource: content,
      container: el,
      viewport: p.page.getViewport({ scale: p.cssScale }),
    });
    await layer.render();
    // Inzwischen freigegeben (neue Breite, weggescrollt)? Dann diese Fassung verwerfen.
    if (gen !== p.textGen || p.textEl) return;
    const end = document.createElement('div');
    end.className = 'endOfContent';
    el.appendChild(end);
    p.el.insertBefore(el, p.linkEl);
    p.textEl = el;
    p.textDivs = layer.textDivs;
    if (findState.matches.length) drawFindOn(p);
  })().catch(() => {}).finally(() => { if (p.textTask === task) p.textTask = null; });
  p.textTask = task;
  return task;
}

/** Text-Layer freigeben – außer eine Auswahl hängt darin (sonst ginge sie verloren). */
function releaseText(p, force) {
  p.textGen = (p.textGen || 0) + 1; // laufendes Rendern verwerfen
  if (!p.textEl) return;
  const sel = window.getSelection();
  if (!force && sel && sel.rangeCount && p.textEl.contains(sel.anchorNode)) return;
  p.textEl.remove();
  p.textEl = null;
  p.textDivs = null;
  p.findEl.replaceChildren();
}

// ── Links ──────────────────────────────────────────────────────────────────

async function renderLinks(p) {
  p.links = [];
  let annotations = [];
  try { annotations = await p.page.getAnnotations({ intent: 'display' }); } catch (e) { return; }
  for (const a of annotations) {
    if (a.subtype !== 'Link' || !a.rect) continue;
    const box = pdfRectToFraction(p, a.rect);
    if (!box) continue;
    const link = document.createElement('a');
    setBox(link, box);
    if (a.url && /^https?:/i.test(a.url)) {
      link.addEventListener('click', ev => { ev.preventDefault(); ev.stopPropagation(); bridge.onOpenLink(a.url); });
    } else if (a.dest) {
      link.addEventListener('click', ev => { ev.preventDefault(); ev.stopPropagation(); goToDest(a.dest); });
    } else {
      continue;
    }
    p.linkEl.appendChild(link);
    p.links.push(link);
  }
}

async function goToDest(dest) {
  try {
    const explicit = typeof dest === 'string' ? await doc.getDestination(dest) : dest;
    if (!Array.isArray(explicit)) return;
    const index = await doc.getPageIndex(explicit[0]);
    const p = pages[index];
    if (p) window.scrollTo(0, p.el.offsetTop - 8);
  } catch (e) {}
}

// ── Koordinaten ────────────────────────────────────────────────────────────

/** PDF-Rechteck [x1, y1, x2, y2] (Nutzerkoordinaten) → Bruchteile der angezeigten Seite. */
function pdfRectToFraction(p, rect) {
  const vp = p.vp1;
  const [ax, ay] = vp.convertToViewportPoint(rect[0], rect[1]);
  const [bx, by] = vp.convertToViewportPoint(rect[2], rect[3]);
  const l = Math.min(ax, bx) / vp.width, t = Math.min(ay, by) / vp.height;
  const w = Math.abs(bx - ax) / vp.width, h = Math.abs(by - ay) / vp.height;
  return w > 0 && h > 0 ? { l, t, w, h } : null;
}

/** Gespeichertes Rechteck [x, y, w, h] (0..1, unrotierte CropBox, oben links) → angezeigte Bruchteile. */
function normToFraction(p, r) {
  if (!Array.isArray(r) || r.length !== 4) return null;
  const [x1, y1, x2, y2] = p.view;
  const W = x2 - x1, H = y2 - y1;
  return pdfRectToFraction(p, [x1 + r[0] * W, y2 - (r[1] + r[3]) * H, x1 + (r[0] + r[2]) * W, y2 - r[1] * H]);
}

/** Angezeigte Bruchteile → gespeichertes Rechteck (Umkehrung von normToFraction). */
function fractionToNorm(p, f) {
  const vp = p.vp1;
  const [ax, ay] = vp.convertToPdfPoint(f.l * vp.width, f.t * vp.height);
  const [bx, by] = vp.convertToPdfPoint((f.l + f.w) * vp.width, (f.t + f.h) * vp.height);
  const [x1, y1, x2, y2] = p.view;
  const W = x2 - x1, H = y2 - y1;
  const clamp = v => Math.min(1, Math.max(0, v));
  const round5 = v => Math.round(v * 100000) / 100000;
  const nx1 = clamp((Math.min(ax, bx) - x1) / W), nx2 = clamp((Math.max(ax, bx) - x1) / W);
  const ny1 = clamp((y2 - Math.max(ay, by)) / H), ny2 = clamp((y2 - Math.min(ay, by)) / H);
  if (nx2 <= nx1 || ny2 <= ny1) return null;
  return [round5(nx1), round5(ny1), round5(nx2 - nx1), round5(ny2 - ny1)];
}

function setBox(el, f) {
  el.style.left = (f.l * 100) + '%';
  el.style.top = (f.t * 100) + '%';
  el.style.width = (f.w * 100) + '%';
  el.style.height = (f.h * 100) + '%';
}

/** Client-Rechtecke einer Range auf Seite p, je Zeile zusammengefasst, als Bruchteile der Seitenbox. */
function rangeFractions(p, range) {
  const box = p.el.getBoundingClientRect();
  if (box.width <= 0 || box.height <= 0) return [];
  const parts = [];
  for (const r of Array.from(range.getClientRects())) {
    const l = Math.max(r.left, box.left), t = Math.max(r.top, box.top);
    const rr = Math.min(r.right, box.right), b = Math.min(r.bottom, box.bottom);
    if (rr - l < 0.5 || b - t < 0.5) continue;
    parts.push({ l: (l - box.left) / box.width, t: (t - box.top) / box.height, w: (rr - l) / box.width, h: (b - t) / box.height });
  }
  parts.sort((a, b) => a.t - b.t || a.l - b.l);
  const lines = [];
  for (const r of parts) {
    const line = lines.find(x => {
      const overlap = Math.min(x.t + x.h, r.t + r.h) - Math.max(x.t, r.t);
      return overlap > 0.5 * Math.min(x.h, r.h);
    });
    if (line) {
      const right = Math.max(line.l + line.w, r.l + r.w), bottom = Math.max(line.t + line.h, r.t + r.h);
      line.l = Math.min(line.l, r.l); line.t = Math.min(line.t, r.t);
      line.w = right - line.l; line.h = bottom - line.t;
    } else {
      lines.push(Object.assign({}, r));
    }
  }
  return lines;
}

// ── Markierungen ───────────────────────────────────────────────────────────

function authorColorOf(h) {
  return h && typeof h.authorColor === 'string' && /^#[0-9a-f]{6}$/i.test(h.authorColor) ? h.authorColor : null;
}

function setHighlights(list, countMap, markable) {
  highlights = Array.isArray(list) ? list.filter(h => h.pdfPage != null) : [];
  counts = countMap || {};
  canMark = !!markable;
  pages.forEach(drawHighlightsOn);
}

function drawHighlightsOn(p) {
  p.hlEl.replaceChildren();
  const list = [];
  for (const h of highlights) {
    if (h.pdfPage !== p.num || !Array.isArray(h.pdfRects)) continue;
    const rects = h.pdfRects.map(r => normToFraction(p, r)).filter(Boolean);
    if (!rects.length) continue;
    const author = authorColorOf(h);
    const isComment = h.color === COMMENT_COLOR;
    for (const f of rects) {
      const el = document.createElement('div');
      el.className = isComment ? 'hl comment' : 'hl';
      el.dataset.highlightId = String(h.id);
      if (author) el.style.setProperty('--mh-author', author);
      if (!isComment) el.style.background = COLORS[h.color] || COLORS.yellow;
      setBox(el, f);
      p.hlEl.appendChild(el);
    }
    const n = counts[String(h.id)];
    if (n) {
      const last = rects[rects.length - 1];
      const badge = document.createElement('span');
      badge.className = 'hl-count';
      badge.textContent = String(n);
      if (author) badge.style.setProperty('--mh-author', author);
      badge.style.left = ((last.l + last.w) * 100) + '%';
      badge.style.top = (last.t * 100) + '%';
      p.hlEl.appendChild(badge);
    }
    list.push({ id: h.id, color: h.color, text: h.highlightedText || '', rects });
  }
  drawn.set(p.num, list);
}

function reveal(id) {
  const h = highlights.find(x => String(x.id) === String(id));
  if (!h) return;
  const p = pages[h.pdfPage - 1];
  if (!p) return;
  const first = (drawn.get(p.num) || []).find(x => String(x.id) === String(id));
  const top = first ? first.rects[0].t * p.el.offsetHeight : 0;
  window.scrollTo({ top: Math.max(0, p.el.offsetTop + top - window.innerHeight / 3), behavior: 'smooth' });
  const els = p.hlEl.querySelectorAll('.hl[data-highlight-id="' + String(id) + '"]');
  els.forEach(e => e.classList.add('flash'));
  setTimeout(() => els.forEach(e => e.classList.remove('flash')), 1200);
}

// ── Auswahl ────────────────────────────────────────────────────────────────

function pageOf(node) {
  const el = node && (node.nodeType === 1 ? node : node.parentElement);
  const pageEl = el && el.closest('.page');
  return pageEl ? pages[Number(pageEl.dataset.page) - 1] : null;
}

/** Ausgewählter Text; Zeilenwechsel im Text-Layer (eigene Spans ohne <br>) werden zu Leerzeichen. */
function rangeText(p, range) {
  const walker = document.createTreeWalker(p.textEl, NodeFilter.SHOW_TEXT);
  let out = '', lastTop = null;
  for (let n = walker.nextNode(); n; n = walker.nextNode()) {
    if (!range.intersectsNode(n)) continue;
    let t = n.nodeValue;
    if (n === range.endContainer) t = t.slice(0, range.endOffset);
    if (n === range.startContainer) t = t.slice(range.startOffset);
    const top = n.parentElement ? n.parentElement.getBoundingClientRect().top : null;
    if (lastTop !== null && top !== null && Math.abs(top - lastTop) > 2 && !/\s$/.test(out)) out += ' ';
    if (top !== null) lastTop = top;
    out += t;
  }
  return out.replace(/\s+/g, ' ').trim();
}

/** Text, Seite und Rechtecke der Auswahl – nur auf der Seite, auf der sie beginnt (wie im Web). */
function selectionInfo() {
  const sel = window.getSelection();
  if (!sel || sel.isCollapsed || !sel.rangeCount) return null;
  const range = sel.getRangeAt(0).cloneRange();
  const p = pageOf(range.startContainer);
  if (!p || !p.textEl || !p.textEl.contains(range.startContainer)) return null;
  if (!p.textEl.contains(range.endContainer)) {
    const end = p.textEl.querySelector('.endOfContent');
    if (end) range.setEndBefore(end); else range.setEnd(p.textEl, p.textEl.childNodes.length);
  }
  const text = rangeText(p, range);
  if (!text) return null;
  const rects = rangeFractions(p, range).map(f => fractionToNorm(p, f)).filter(Boolean).slice(0, MAX_RECTS);
  if (!rects.length) return null;
  return { text, page: p.num, rects, range };
}

let selTimer = null;
document.addEventListener('selectionchange', () => {
  clearTimeout(selTimer);
  selTimer = setTimeout(() => {
    const sel = window.getSelection();
    if (!sel || sel.isCollapsed || !sel.rangeCount) { bridge.onSelectionCleared(); return; }
    const r = sel.getRangeAt(0).getBoundingClientRect();
    bridge.onSelectionChanged(JSON.stringify({ x: r.left, y: r.top, width: r.width, height: r.height }));
  }, 150);
});

function takeSelection() {
  const info = selectionInfo();
  window.getSelection().removeAllRanges();
  bridge.onSelectionCleared();
  return info;
}

function mark(color) {
  if (!canMark) return;
  const info = takeSelection();
  if (!info) return;
  bridge.onCreateHighlight(JSON.stringify({
    highlightedText: info.text, startXpath: '', startOffset: 0, endXpath: '', endOffset: 0,
    color, pdfPage: info.page, pdfRects: info.rects,
  }));
}

// „Kommentieren“ an einer frischen Auswahl: nichts einfärben, Kotlin öffnet das Kommentarfeld;
// erst der abgeschickte Kommentar legt die Stelle an (über `anchor`).
function comment() {
  const info = takeSelection();
  if (!info) return;
  bridge.onCommentAnchor(JSON.stringify({ highlightedText: info.text, pdfPage: info.page, pdfRects: info.rects }));
}

function clearSelection() {
  const sel = window.getSelection();
  if (sel) sel.removeAllRanges();
}

// Tipp auf eine Markierung (die Ebene liegt unter dem Text-Layer, daher Treffer-Test per Position).
viewer.addEventListener('click', ev => {
  const sel = window.getSelection();
  if (sel && !sel.isCollapsed) return;
  const pageEl = ev.target.closest && ev.target.closest('.page');
  if (!pageEl) { bridge.onTap(); return; }
  const p = pages[Number(pageEl.dataset.page) - 1];
  const box = pageEl.getBoundingClientRect();
  const fx = (ev.clientX - box.left) / box.width, fy = (ev.clientY - box.top) / box.height;
  const tol = 4 / Math.max(1, box.height);
  const hits = (drawn.get(p.num) || []).filter(h =>
    h.rects.some(f => fx >= f.l - tol && fx <= f.l + f.w + tol && fy >= f.t - tol && fy <= f.t + f.h + tol));
  if (!hits.length) { bridge.onTap(); return; }
  // Farbige Markierung vor einer nur kommentierten Stelle, sonst die zuletzt gezeichnete.
  const hit = hits.find(h => h.color !== COMMENT_COLOR) || hits[hits.length - 1];
  if (hit.color === COMMENT_COLOR || !canMark) {
    bridge.onOpenComments(Number(hit.id), hit.text);
    return;
  }
  const top = Math.min(...hit.rects.map(f => f.t)), bottom = Math.max(...hit.rects.map(f => f.t + f.h));
  bridge.onHighlightTap(Number(hit.id), JSON.stringify({
    x: box.left, y: box.top + top * box.height, width: box.width, height: (bottom - top) * box.height,
  }));
});

// ── Suche ──────────────────────────────────────────────────────────────────

const findState = { query: '', matches: [], index: -1, token: 0 };

/** Ein Zeichen ohne Akzent und klein – gleich lang wie das Original, damit Offsets stimmen. */
function foldChar(c) {
  const f = c.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
  return f.length === 1 ? f : (c.toLowerCase().length === 1 ? c.toLowerCase() : c);
}

function fold(s) {
  let out = '';
  for (const c of s) out += c.length === 1 ? foldChar(c) : c;
  return out;
}

/** Seitentext wie im Text-Layer: Einträge hintereinander, nach Zeilenende ein Leerzeichen. */
async function textInfoOf(p) {
  if (p.textInfo) return p.textInfo;
  const content = await textContentOf(p);
  const items = content.items.filter(it => it.str !== undefined);
  const starts = [];
  let text = '';
  for (const it of items) {
    starts.push(text.length);
    text += it.str;
    if (it.hasEOL) text += ' ';
  }
  p.textInfo = { items, starts, text, folded: fold(text) };
  return p.textInfo;
}

async function find(query) {
  const token = ++findState.token;
  clearFindLayers();
  findState.query = (query || '').trim();
  findState.matches = [];
  findState.index = -1;
  if (!findState.query || !doc) { bridge.onFindResult(-1, 0); return; }
  const needle = fold(findState.query);
  for (const p of pages) {
    const info = await textInfoOf(p);
    if (token !== findState.token) return;
    let from = 0;
    while (true) {
      const at = info.folded.indexOf(needle, from);
      if (at < 0) break;
      findState.matches.push({ page: p.num, start: at, end: at + needle.length });
      from = at + Math.max(1, needle.length);
    }
  }
  if (!findState.matches.length) { bridge.onFindResult(-1, 0); return; }
  // Erster Treffer ab der sichtbaren Seite.
  const visible = currentPage();
  const first = findState.matches.findIndex(m => m.page >= visible);
  await showMatch(first >= 0 ? first : 0);
}

function currentPage() {
  const y = window.scrollY + 1;
  for (const p of pages) if (p.el.offsetTop + p.el.offsetHeight > y) return p.num;
  return 1;
}

async function findStep(forward) {
  const n = findState.matches.length;
  if (!n) return;
  await showMatch(((findState.index + (forward ? 1 : -1)) % n + n) % n);
}

function clearFind() {
  findState.token++;
  findState.query = '';
  findState.matches = [];
  findState.index = -1;
  clearFindLayers();
}

function clearFindLayers() {
  pages.forEach(p => p.findEl.replaceChildren());
}

async function showMatch(index) {
  findState.index = index;
  const m = findState.matches[index];
  const p = pages[m.page - 1];
  bridge.onFindResult(index, findState.matches.length);
  // Seite in die Nähe holen, damit Text-Layer und Canvas entstehen.
  window.scrollTo(0, Math.max(0, p.el.offsetTop - 40));
  near.add(p);
  await renderText(p);
  if (findState.matches[index] !== m) return;
  pages.forEach(x => { if (x !== p) x.findEl.replaceChildren(); });
  const current = drawFindOn(p);
  if (current) {
    window.scrollTo(0, Math.max(0, p.el.offsetTop + current.t * p.el.offsetHeight - window.innerHeight / 3));
  }
  schedule();
}

/** Alle Treffer der Seite einzeichnen (der gewählte orange); liefert das Rechteck des gewählten. */
function drawFindOn(p) {
  p.findEl.replaceChildren();
  if (!p.textDivs || !p.textInfo) return null;
  const selected = findState.matches[findState.index];
  let currentRect = null;
  for (const m of findState.matches) {
    if (m.page !== p.num) continue;
    const range = rangeForOffsets(p, m.start, m.end);
    if (!range) continue;
    const isCurrent = m === selected;
    for (const f of rangeFractions(p, range)) {
      const el = document.createElement('div');
      el.className = isCurrent ? 'find current' : 'find';
      setBox(el, f);
      p.findEl.appendChild(el);
      if (isCurrent && !currentRect) currentRect = f;
    }
  }
  return currentRect;
}

function rangeForOffsets(p, start, end) {
  const { starts, items } = p.textInfo;
  const locate = (offset, isEnd) => {
    for (let i = 0; i < items.length; i++) {
      const s = starts[i], len = items[i].str.length;
      if (offset >= s && (offset < s + len || (isEnd && offset === s + len))) {
        const div = p.textDivs[i];
        const node = div && div.firstChild;
        return node ? [node, offset - s] : null;
      }
    }
    return null;
  };
  const a = locate(start, false), b = locate(end, true);
  if (!a || !b) return null;
  try {
    const range = document.createRange();
    range.setStart(a[0], a[1]);
    range.setEnd(b[0], b[1]);
    return range;
  } catch (e) {
    return null;
  }
}

window.MerlinPdf = { open, setHighlights, mark, comment, clearSelection, reveal, find, findStep, clearFind };
bridge.onScriptReady();
