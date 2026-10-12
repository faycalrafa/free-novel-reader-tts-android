window.$ = function(id) { return document.getElementById(id); };

window.STORAGE_KEY = 'novelArrowReader::settings::v3';
window.SESSION_KEY = 'novelArrowReader::session::v1';
window.TTS_FLAG_KEY = 'novelArrowReader::tts_active::v1';

window.PIPER_VOICES = [
    { id: 'en_US-libritts_r-medium', label: 'LibriTTS-R (US)', lang: 'en-US', quality: 'medium', size: '~63 MB', notes: 'Rich US multi-speaker voice.' },
    { id: 'en_US-libritts-high',     label: 'LibriTTS (US, HQ)', lang: 'en-US', quality: 'high',   size: '~114 MB', notes: 'Higher-quality LibriTTS.' },
    { id: 'en_GB-aru-medium',        label: 'Aru (UK)',        lang: 'en-GB', quality: 'medium', size: '~63 MB',  notes: 'Warm UK narrator.' }
];
window.DEFAULT_PIPER_VOICE = 'en_US-libritts_r-medium';

window.DEFAULT_IGNORE_SELECTORS = [
    'nav', 'footer', 'header', 'aside', 'button', 'a',
    '[role="navigation"]', '.ads', '.ad', '.advertisement',
    'script', 'style', 'noscript', 'iframe',
    'form', 'input', 'textarea', 'select'
];

window.DEFAULT_SETTINGS = {
    countdownMs: 1000,
    loopCount: 10,
    autoNextOnScroll: false,
    autoNextOnTTSEnd: true,
    ttsAutoStart: false,
    contentSelector: '',
    theme: 'dark',
    fontSize: 16,
    engine: 'device',
    piperVoice: window.DEFAULT_PIPER_VOICE,
    deviceVoice: '',
    ttsRate: 1.0,
    ttsAutoScroll: true,
    ttsSkipList: [],
    ttsReplacements: [],
    ttsIgnoreSelectors: window.DEFAULT_IGNORE_SELECTORS.slice(),
    backgroundPlayback: true,
    mediaSessionEnabled: true,
    rememberProgress: true
};

window.safeGet = function(key, fallback) {
    try { const raw = localStorage.getItem(key); if (!raw) return fallback; return JSON.parse(raw); } catch (e) { return fallback; }
};

window.safeSet = function(key, val) {
    try { localStorage.setItem(key, JSON.stringify(val)); return true; } catch (e) { return false; }
};

window.loadSettings = function() {
    const parsed = window.safeGet(window.STORAGE_KEY, null);
    if (!parsed) return { ...window.DEFAULT_SETTINGS };
    const merged = { ...window.DEFAULT_SETTINGS, ...parsed };
    if (!Array.isArray(merged.ttsSkipList))       merged.ttsSkipList = [];
    if (!Array.isArray(merged.ttsReplacements))   merged.ttsReplacements = [];
    if (!Array.isArray(merged.ttsIgnoreSelectors))merged.ttsIgnoreSelectors = window.DEFAULT_IGNORE_SELECTORS.slice();
    if (!merged.piperVoice) merged.piperVoice = window.DEFAULT_PIPER_VOICE;
    return merged;
};

window.saveSettings = function(s) { window.safeSet(window.STORAGE_KEY, s); };

window.settings = window.loadSettings();
window.sessionState = window.safeGet(window.SESSION_KEY, {});

window.setTTSActiveFlag = function(v) { try { localStorage.setItem(window.TTS_FLAG_KEY, v ? '1' : '0'); } catch(e) {} };
window.getTTSActiveFlag = function() { try { return localStorage.getItem(window.TTS_FLAG_KEY) === '1'; } catch(e) { return false; } };

window.DB_NAME = 'novelarrow-db';
window.DB_VERSION = 2;
window.STORES = { handles: 'handles', playlists: 'playlists' };
window._db = null;

window.openDB = function() {
    return new Promise((resolve, reject) => {
        if (window._db) return resolve(window._db);
        const req = indexedDB.open(window.DB_NAME, window.DB_VERSION);
        req.onupgradeneeded = e => {
            const db = e.target.result;
            if (!db.objectStoreNames.contains(window.STORES.handles))   db.createObjectStore(window.STORES.handles,   { keyPath: 'id' });
            if (!db.objectStoreNames.contains(window.STORES.playlists)) db.createObjectStore(window.STORES.playlists, { keyPath: 'id' });
        };
        req.onsuccess = e => { window._db = e.target.result; resolve(window._db); };
        req.onerror   = e => reject(e.target.error);
    });
};

window.idbGet = async function(store, key) {
    const db = await window.openDB();
    return new Promise((res, rej) => {
        const tx = db.transaction(store, 'readonly').objectStore(store).get(key);
        tx.onsuccess = () => res(tx.result);
        tx.onerror   = () => rej(tx.error);
    });
};

window.idbPut = async function(store, val) {
    const db = await window.openDB();
    return new Promise((res, rej) => {
        const tx = db.transaction(store, 'readwrite').objectStore(store).put(val);
        tx.onsuccess = () => res();
        tx.onerror   = () => rej(tx.error);
    });
};

window.idbDel = async function(store, key) {
    const db = await window.openDB();
    return new Promise((res, rej) => {
        const tx = db.transaction(store, 'readwrite').objectStore(store).delete(key);
        tx.onsuccess = () => res();
        tx.onerror   = () => rej(tx.error);
    });
};

window.idbAll = async function(store) {
    const db = await window.openDB();
    return new Promise((res, rej) => {
        const out = []; const tx = db.transaction(store, 'readonly').objectStore(store).openCursor();
        tx.onsuccess = e => { const c = e.target.result; if (c) { out.push(c.value); c.continue(); } else res(out); };
        tx.onerror   = () => rej(tx.error);
    });
};

window.state = {
    library: [],
    currentLibId: null,
    currentPlaylist: null,
    currentEntryIndex: -1
};

window._sessionFileRefs = new Map();
window.supportsFSAPI = ('showDirectoryPicker' in window) && ('showOpenFilePicker' in window);
window.TEXT_EXT_RE = /\.(txt|md|markdown|html?|xhtml|xml|json|csv|log|rtf|srt|vtt|ini|cfg|yaml|yml|tex|nfo|text)$/i;

window.isTextFile = function(name, type) {
    if (window.TEXT_EXT_RE.test(name)) return true;
    if (type && type.startsWith('text/')) return true;
    return false;
};

window.uid = function() { return 'id_' + Math.random().toString(36).slice(2, 10) + Date.now().toString(36); };
window.naturalCompare = function(a, b) { return a.localeCompare(b, undefined, { numeric: true, sensitivity: 'base' }); };
window.escapeHtml = function(s) { return String(s == null ? '' : s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c])); };
window.escapeAttr = function(s) { return String(s == null ? '' : s).replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;'); };
window.escapeRegex = function(s) { return String(s == null ? '' : s).replace(/[.*+?^${}()|[\]\\]/g, '\\$&'); };

window.app = {
    loopActive: false,
    loopRemaining: 0,
    scrollFireGuard: false,
    scrollSaveTimer: null
};

window.showLoading = function(text) { window.$('loading-text').textContent = text || 'Loading…'; window.$('loading').classList.add('visible'); };
window.hideLoading = function() { window.$('loading').classList.remove('visible'); };

window.toast = function(msg, kind, ttl) {
    kind = kind || 'info';
    ttl = ttl || 3500;
    const el = document.createElement('div');
    el.className = 'toast ' + kind;
    const iconMap = { info: 'fa-circle-info', error: 'fa-triangle-exclamation', success: 'fa-check', warn: 'fa-triangle-exclamation' };
    el.innerHTML = `<span class="icon ${kind}"><i class="fa-solid ${iconMap[kind] || iconMap.info}"></i></span><span>${window.escapeHtml(msg)}</span>`;
    window.$('toast-wrap').appendChild(el);
    setTimeout(() => {
        el.style.transition = 'opacity 0.25s, transform 0.25s';
        el.style.opacity = '0'; el.style.transform = 'translateY(-6px)';
        setTimeout(() => el.remove(), 280);
    }, ttl);
};