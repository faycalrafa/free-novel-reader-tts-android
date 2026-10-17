
window.wakeLock = null;

window.requestWakeLock = async function() {
    try { if ('wakeLock' in navigator) { window.wakeLock = await navigator.wakeLock.request('screen'); window.wakeLock.addEventListener('release', () => { window.wakeLock = null; }); } } catch (e) {}
};

window.releaseWakeLock = function() {
    try { if (window.wakeLock) { window.wakeLock.release(); window.wakeLock = null; } } catch (e) {}
};

window.startBackgroundKeepAlive = function() {
    if (!window.settings.backgroundPlayback) return;
    try {
        const silentAudio = window.$('silent-audio');
        silentAudio.volume = 0.001; silentAudio.loop = true; silentAudio.muted = false;
        const p = silentAudio.play(); if (p && p.catch) p.catch(() => {});
    } catch (e) {}
};

window.stopBackgroundKeepAlive = function() {
    try { const silentAudio = window.$('silent-audio'); silentAudio.pause(); silentAudio.currentTime = 0; } catch (e) {}
};

window.setupMediaSession = function() {
    if (!window.settings.mediaSessionEnabled || !('mediaSession' in navigator)) return;
    try {
        navigator.mediaSession.setActionHandler('play', () => {
            if (!window.TTS.active) window.TTS.start(); else if (window.TTS.paused) window.TTS.resume();
            try { window.$('tts-audio').play(); } catch (e) {}
            try { window.$('silent-audio').play(); } catch (e) {}
            navigator.mediaSession.playbackState = 'playing';
        });
        navigator.mediaSession.setActionHandler('pause', () => {
            if (window.TTS.active) window.TTS.pause();
            try { window.$('silent-audio').pause(); } catch (e) {}
            navigator.mediaSession.playbackState = 'paused';
        });
        navigator.mediaSession.setActionHandler('previoustrack', () => { if (window.TTS.active) window.TTS.speakAt(Math.max(0, window.TTS.index - 1), true); });
        navigator.mediaSession.setActionHandler('nexttrack', () => { if (window.TTS.active) window.TTS.speakAt(window.TTS.index + 1, true); });
        navigator.mediaSession.setActionHandler('stop', () => window.TTS.stop());
    } catch (e) { console.warn('[MediaSession] setup failed', e); }
};

window.updateMediaSessionMetadata = function(title, sub) {
    if (!('mediaSession' in navigator)) return;
    try {
        const entry = window.state.currentPlaylist?.entries[window.state.currentEntryIndex];
        const chapterName = entry ? entry.name.replace(/\.[^.]+$/, '') : (title || 'Reading');
        const total = window.TTS.paragraphs ? window.TTS.paragraphs.length : 0;
        const cur = window.TTS.index + 1;
        navigator.mediaSession.metadata = new MediaMetadata({
            title: title || 'BookReader',
            artist: total > 0 ? `¶ ${cur} / ${total}` : 'BookReader Pro',
            album: chapterName,
            artwork: [{ src: window.makeAlbumArt(), sizes: '512x512', type: 'image/png' }]
        });
    } catch (e) {}
};

window._cachedArtwork = null;
window.makeAlbumArt = function() {
    if (window._cachedArtwork) return window._cachedArtwork;
    try {
        const c = document.createElement('canvas'); c.width = 512; c.height = 512;
        const ctx = c.getContext('2d');
        const g = ctx.createLinearGradient(0, 0, 512, 512); g.addColorStop(0, '#2563eb'); g.addColorStop(1, '#7c3aed');
        ctx.fillStyle = g; ctx.fillRect(0, 0, 512, 512);
        ctx.fillStyle = 'rgba(255,255,255,0.95)'; ctx.font = 'bold 260px system-ui, sans-serif'; ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
        ctx.fillText('📖', 256, 256); ctx.font = 'bold 46px system-ui, sans-serif'; ctx.fillText('BookReader', 256, 460);
        window._cachedArtwork = c.toDataURL('image/png'); return window._cachedArtwork;
    } catch (e) { return ''; }
};

window.TTS = {
    active: false,
    paused: false,
    speaking: false,
    loading: false,
    paragraphs: [],
    index: 0,
    currentEl: null,
    usingPiper: false,
    pendingNext: false,
    wasActiveBeforeAdvance: false,
    _abortFlag: 0,
    _keepAliveTimer: null,

    collectParagraphs() {
        if (!window.shadow) return [];
        let root = window.shadow;
        if (window.settings.contentSelector) {
            try { const el = window.shadow.querySelector(window.settings.contentSelector); if (el) root = el; } catch (e) {}
        }
        const nodes = Array.from(root.querySelectorAll('p, h1, h2, h3, h4, h5, h6, li, blockquote, div'));
        const ignoreSel = (window.settings.ttsIgnoreSelectors || []).filter(Boolean).join(', ');
        const filtered = nodes.filter(el => {
            const text = (el.textContent || '').trim();
            if (text.length < 2) return false;
            if (['DIV','BLOCKQUOTE','LI'].includes(el.tagName) && el.querySelector('p, h1, h2, h3, h4, h5, h6, li, blockquote, article, section')) return false;
            if (ignoreSel) { try { if (el.closest(ignoreSel)) return false; } catch(e) {} }
            const cs = window.getComputedStyle(el);
            if (cs.display === 'none' || cs.visibility === 'hidden' || parseFloat(cs.opacity) === 0) return false;
            const rect = el.getBoundingClientRect();
            if (rect.width === 0 && rect.height === 0) return false;
            return true;
        });

        // Split long paragraphs into sentence-sized chunks (at dots, question marks, exclamation marks).
        // Each chunk becomes its own "virtual" paragraph so long paragraphs don't hang the TTS engine.
        // We wrap the sentences in <span class="na-tts-sentence"> so highlighting still tracks the current sentence.
        const MAX_CHARS = 240; // paragraphs longer than this get split at dots
        const out = [];
        for (const el of filtered) {
            const raw = (el.textContent || '').trim();
            if (raw.length <= MAX_CHARS || !/[.!?。！？]/.test(raw)) { out.push(el); continue; }
            // Already split?
            const existing = el.querySelectorAll(':scope > .na-tts-sentence');
            if (existing.length > 1) { existing.forEach(s => out.push(s)); continue; }
            const sentences = this.splitIntoSentences(raw);
            if (sentences.length <= 1) { out.push(el); continue; }
            // Rebuild element with each sentence wrapped in a span
            el.innerHTML = '';
            for (let i = 0; i < sentences.length; i++) {
                const s = document.createElement('span');
                s.className = 'na-tts-sentence';
                s.textContent = sentences[i] + (i < sentences.length - 1 ? ' ' : '');
                el.appendChild(s);
                out.push(s);
            }
        }
        return out;
    },

    splitIntoSentences(text) {
        // Split on ., !, ?, 。, ！, ？ followed by whitespace or end.
        // Keep the delimiter with the sentence.
        const parts = [];
        const re = /[^.!?。！？]+[.!?。！？]+(?=\s|$)|[^.!?。！？]+$/g;
        let m;
        while ((m = re.exec(text)) !== null) {
            const s = m[0].trim();
            if (s.length >= 2) parts.push(s);
        }
        // If regex found nothing usable, fall back to a simple dot split
        if (!parts.length) {
            return text.split(/(?<=[.!?。！？])\s+/).map(s => s.trim()).filter(s => s.length >= 2);
        }
        return parts;
    },

    findViewportStartIndex() {
        const readerContent = window.$('reader-content');
        const containerRect = readerContent.getBoundingClientRect();
        const winH = readerContent.clientHeight;
        for (let i = 0; i < this.paragraphs.length; i++) {
            const rect = this.paragraphs[i].getBoundingClientRect();
            const relTop = rect.top - containerRect.top;
            const relBottom = rect.bottom - containerRect.top;
            if (relBottom > 0 && relTop < winH * 0.5) return i;
        }
        return 0;
    },

    applyReplacements(text) {
        let out = String(text || '');
        try {
            for (const rule of window.settings.ttsReplacements || []) {
                if (!rule.from) continue;
                if (rule.exact) {
                    const re = new RegExp(`\\b${window.escapeRegex(rule.from)}\\b`, 'gi'); out = out.replace(re, rule.to || '');
                } else { out = out.split(rule.from).join(rule.to || ''); }
            }
            for (const skip of window.settings.ttsSkipList || []) {
                if (!skip.text) continue;
                if (skip.exact) {
                    const re = new RegExp(`\\b${window.escapeRegex(skip.text)}\\b`, 'gi'); out = out.replace(re, '');
                } else { out = out.split(skip.text).join(''); }
            }
        } catch(e) {}
        return out.replace(/\s+/g, ' ').trim();
    },

    highlight(el) {
        const readerContent = window.$('reader-content');
        if (this.currentEl) this.currentEl.classList.remove('na-tts-highlight');
        this.currentEl = el;
        if (el) {
            el.classList.add('na-tts-highlight');
            if (window.settings.ttsAutoScroll) {
                const contRect = readerContent.getBoundingClientRect();
                const rect = el.getBoundingClientRect();
                const offset = rect.top - contRect.top - (readerContent.clientHeight * 0.35);
                readerContent.scrollBy({ top: offset, behavior: 'smooth' });
            }
        }
        this.updateUI();
    },

    updateUI() {
        const total = this.paragraphs.length || 0;
        const cur = Math.min(this.index + 1, total);
        const pct = total ? Math.round((cur / total) * 100) : 0;
        window.$('tts-progress').style.width = pct + '%';
        window.$('tts-para-info').textContent = `${cur} / ${total}`;
        // Bottom-bar text preview intentionally removed — the highlighted paragraph in the reader
        // already shows what is being read.
        const npEl = window.$('tts-now-playing');
        if (npEl) npEl.textContent = '';

        let status = 'Idle';
        if (this.loading) status = 'Loading voice…';
        else if (this.paused) status = 'Paused';
        else if (this.speaking) status = 'Speaking';
        else if (this.active) status = 'Ready';

        const entry = window.state.currentPlaylist?.entries[window.state.currentEntryIndex];
        window.$('tts-chap-info').textContent = entry ? entry.name.replace(/\.[^.]+$/, '') : status;
        
        window.$('tts-playpause').innerHTML = (this.active && !this.paused) ? '<i class="fa-solid fa-pause"></i>' : '<i class="fa-solid fa-play"></i>';
        if ('mediaSession' in navigator && 'setPositionState' in navigator.mediaSession) {
            try { navigator.mediaSession.setPositionState({ duration: total || 1, position: Math.min(cur, total || 1), playbackRate: parseFloat(window.settings.ttsRate) || 1.0 }); } catch(e) {}
        }
        window.updateMediaSessionMetadata(window.$('page-title').textContent, window.$('page-sub').textContent);
    },

    pickEngine() {
        if (window.settings.engine === 'piper') return 'piper';
        if (window.settings.engine === 'device') return 'device';
        return window.settings.piperVoice ? 'piper' : 'device';
    },

    async speakAt(index, isJump) {
        if (!this.active) return;
        if (index < 0) index = 0;
        // If the underlying paragraph nodes were removed from the DOM (e.g., chapter switched),
        // re-collect to avoid ghost references.
        if (this.paragraphs.length && this.paragraphs[0] && !this.paragraphs[0].isConnected) {
            this.paragraphs = this.collectParagraphs();
        }
        if (index >= this.paragraphs.length) {
            window.markCurrentDone();
            if (window.settings.autoNextOnTTSEnd || this.pendingNext || window.app.loopActive) {
                this.pendingNext = false; this.wasActiveBeforeAdvance = true; this._stopSilent();
                if (window.app.loopActive) { window.doAutoNext('tts-end-of-page'); }
                else if (window.settings.autoNextOnTTSEnd) {
                    setTimeout(() => {
                        const was = window.state.currentEntryIndex;
                        window.goToNextItem();
                        if (window.state.currentEntryIndex !== was) { setTimeout(() => this.start(), 500); }
                    }, 300);
                }
                return;
            }
            this.stop(); return;
        }

        this.index = index;
        const el = this.paragraphs[index];
        this.highlight(el);
        const raw = el.textContent || '';
        const text = this.applyReplacements(raw);

        if (!text || text.length < 2) return this.speakAt(index + 1);

        const engine = this.pickEngine();
        this.usingPiper = (engine === 'piper');
        const rate = parseFloat(window.settings.ttsRate) || 1.0;
        const myToken = ++this._abortFlag;

        this.loading = true; this.updateUI();

        try {
            if (this.usingPiper) {
                try { speechSynthesis.cancel(); } catch {}
                const voiceId = window.settings.piperVoice || window.DEFAULT_PIPER_VOICE;
                if (!window.Piper.loaded || window.Piper.activeVoice !== voiceId) {
                    const ok = await window.Piper.ensureVoice(voiceId);
                    if (!ok) { this.usingPiper = false; this.loading = false; return this.speakDeviceUtterance(text, isJump); }
                }
                if (myToken !== this._abortFlag || !this.active) { this.loading = false; return; }
                // Piper synthesis time scales with text length; long sentences occasionally never resolve.
                // Wrap in a hard timeout so we never hang forever on one paragraph.
                const url = await Promise.race([
                    window.Piper.synthesize(text, voiceId),
                    new Promise((_, rej) => setTimeout(() => rej(new Error('Piper synth timeout')), Math.max(15000, text.length * 120)))
                ]);
                if (myToken !== this._abortFlag || !this.active) { this.loading = false; return; }
                await this._playAudio(url, rate, myToken);
                if (myToken !== this._abortFlag || !this.active) return;
                this.onUtteranceEnd();
            } else {
                try { window.$('tts-audio').pause(); } catch(e) {}
                this.loading = false;
                this.speakDeviceUtterance(text, isJump);
            }
        } catch (err) {
            console.error('[TTS] speakAt error', err); this.loading = false;
            if (myToken !== this._abortFlag || !this.active) return;
            window.toast('TTS error: ' + (err.message || err), 'error');
            setTimeout(() => this.speakAt(this.index + 1), 300);
        }
    },

    _playAudio(url, rate, token) {
        const ttsAudio = window.$('tts-audio');
        return new Promise((resolve, reject) => {
            let watchdog = null;
            const clean = () => { if (watchdog) { clearTimeout(watchdog); watchdog = null; } };
            try {
                ttsAudio.src = url; ttsAudio.playbackRate = rate || 1.0;
                ttsAudio.onplay = () => {
                    this.loading = false; this.speaking = true; this.paused = false; this.updateUI();
                    try { if ('mediaSession' in navigator) navigator.mediaSession.playbackState = 'playing'; } catch(e) {}
                    // Watchdog: if playback never fires 'ended' (rare bug where audio silently stalls),
                    // resolve based on the audio's known duration so we can advance.
                    clean();
                    const dur = isFinite(ttsAudio.duration) && ttsAudio.duration > 0 ? ttsAudio.duration : 60;
                    watchdog = setTimeout(() => {
                        if (token !== this._abortFlag) return;
                        this.speaking = false; this.updateUI();
                        resolve();
                    }, Math.max(5000, (dur / (rate || 1.0)) * 1000 + 4000));
                };
                ttsAudio.onended = () => { clean(); this.speaking = false; this.updateUI(); resolve(); };
                ttsAudio.onerror = () => { clean(); this.speaking = false; this.loading = false; reject(new Error('Audio playback failed')); };
                const p = ttsAudio.play(); if (p && p.catch) p.catch((err) => { clean(); reject(err); });
            } catch(e) { clean(); reject(e); }
        });
    },

    speakDeviceUtterance(text, isJump) {
        if (!('speechSynthesis' in window)) { window.toast('Device speech synthesis not available', 'error'); this.stop(); return; }
        if (isJump) { try { speechSynthesis.cancel(); } catch {} }
        const u = new SpeechSynthesisUtterance(text);
        u.rate = parseFloat(window.settings.ttsRate) || 1.0;
        const voices = speechSynthesis.getVoices();
        let voice = null;
        if (window.settings.deviceVoice) voice = voices.find(v => v.name === window.settings.deviceVoice);
        if (!voice) voice = voices.find(v => v.localService && /Microsoft Mark/i.test(v.name)) || voices.find(v => v.localService && v.lang && v.lang.startsWith('en')) || voices.find(v => v.lang && v.lang.startsWith('en'));
        if (voice) u.voice = voice;
        u.onstart = () => { this.speaking = true; this.paused = false; this.updateUI(); try { if ('mediaSession' in navigator) navigator.mediaSession.playbackState = 'playing'; } catch(e) {} };
        u.onend = () => { this.speaking = false; this.updateUI(); if (this.active && window._bookReaderU === u) this.onUtteranceEnd(); };
        u.onerror = (e) => { this.speaking = false; if (this.active && e.error !== 'interrupted' && e.error !== 'canceled') setTimeout(() => this.speakAt(this.index + 1), 300); };
        window._bookReaderU = u;
        if (isJump) setTimeout(() => { if (this.active) speechSynthesis.speak(u); }, 100); else speechSynthesis.speak(u);
        
        if (this._keepAliveTimer) clearInterval(this._keepAliveTimer);
        this._keepAliveTimer = setInterval(() => {
            try { if (!this.active) { clearInterval(this._keepAliveTimer); return; } if (speechSynthesis.speaking && !speechSynthesis.paused) { speechSynthesis.pause(); speechSynthesis.resume(); } } catch(e) {}
        }, 10000);
    },

    onUtteranceEnd() {
        if (!this.active) return;
        if (window.settings.rememberProgress && window.state.currentPlaylist) {
            window.state.currentPlaylist.progress.ttsIndex = this.index;
            window.savePlaylistProgress();
        }
        if (this.pendingNext) {
            this.pendingNext = false; this.wasActiveBeforeAdvance = true; this._stopSilent();
            window.doAutoNext('tts-line-end-after-scroll'); return;
        }
        this.speakAt(this.index + 1);
    },

    async start(opts) {
        opts = opts || {};
        if (!window.shadow.textContent.trim()) { window.toast('Load a chapter first', 'warn'); return; }
        this.paragraphs = this.collectParagraphs();
        if (!this.paragraphs.length) { window.toast('No readable text found. Try setting a content selector in Settings.', 'warn'); return; }

        const engine = this.pickEngine();
        if (engine === 'piper') {
            const voiceId = window.settings.piperVoice || window.DEFAULT_PIPER_VOICE;
            if (!window.Piper.loaded || window.Piper.activeVoice !== voiceId) {
                const ok = await window.Piper.ensureVoice(voiceId);
                if (!ok) window.toast('Piper unavailable — using device voice', 'warn');
            }
        }
        try { speechSynthesis.cancel(); } catch {}

        this.active = true; this.paused = false; this.pendingNext = false;
        window.setTTSActiveFlag(true); window.sessionState.ttsActive = true; window.safeSet(window.SESSION_KEY, window.sessionState);
        window.$('tts-dock').classList.add('visible');
        window.setupMediaSession(); window.startBackgroundKeepAlive(); window.requestWakeLock();

        // Play button (top-bar / dock) always starts from the current viewport,
        // not from the saved chapter position. Only explicit startIndex or a
        // fresh double-tap-on-paragraph honors a specific index.
        let start;
        if (typeof opts.startIndex === 'number') {
            start = opts.startIndex;
        } else {
            start = this.findViewportStartIndex();
        }

        this.updateUI();
        setTimeout(() => this.speakAt(start, false), 100);
    },

    _stopSilent() {
        this.active = false; this.paused = false; this.speaking = false; this.loading = false;
        this.pendingNext = false;
        this._abortFlag++;
        try { speechSynthesis.cancel(); } catch {}
        try {
            const ttsAudio = window.$('tts-audio');
            ttsAudio.pause();
            ttsAudio.onended = null; ttsAudio.onplay = null; ttsAudio.onerror = null;
            ttsAudio.currentTime = 0;
            ttsAudio.removeAttribute('src'); ttsAudio.load();
        } catch(e) {}
        if (this._keepAliveTimer) { clearInterval(this._keepAliveTimer); this._keepAliveTimer = null; }
    },

    stop(opts) {
        opts = opts || {}; this._stopSilent();
        if (this.currentEl) this.currentEl.classList.remove('na-tts-highlight');
        this.currentEl = null;
        if (!opts.preserveAutoNext) this.wasActiveBeforeAdvance = false;
        window.stopBackgroundKeepAlive(); window.releaseWakeLock();
        if (!opts.preserveAutoNext) window.setTTSActiveFlag(false);
        try { if ('mediaSession' in navigator) navigator.mediaSession.playbackState = 'none'; } catch(e) {}
        // Hide the bottom TTS dock when the user explicitly stops (unless caller asked to keep it,
        // e.g., the download-recording flow calls stop({silent:true}) between chapters).
        if (!opts.silent && !opts.preserveAutoNext) {
            window.$('tts-dock').classList.remove('visible');
            window.updateFabNext && window.updateFabNext();
        }
        this.updateUI();
    },

    pause() {
        if (!this.active) return;
        if (this.usingPiper) { try { window.$('tts-audio').pause(); } catch(e) {} } else { try { speechSynthesis.pause(); } catch {} }
        this.paused = true; this.updateUI();
        try { if ('mediaSession' in navigator) navigator.mediaSession.playbackState = 'paused'; } catch(e) {}
    },

    resume() {
        if (!this.active) return;
        this.paused = false;
        if (this.usingPiper) { try { const p = window.$('tts-audio').play(); if (p && p.catch) p.catch(() => this.speakAt(this.index, true)); } catch(e) { this.speakAt(this.index, true); } } else { this.speakAt(this.index, true); }
        this.updateUI();
        try { if ('mediaSession' in navigator) navigator.mediaSession.playbackState = 'playing'; } catch(e) {}
    },

    togglePause() { this.paused ? this.resume() : this.pause(); },

    seekPct(pct) {
        if (!this.active || !this.paragraphs.length) return;
        const idx = Math.max(0, Math.min(this.paragraphs.length - 1, Math.floor(pct * this.paragraphs.length)));
        this.speakAt(idx, true);
    }
};

window.ttsClickHandler = null;
window.lastTap = 0;
window.lastNode = null;

window.attachContentClickHandler = function() {
    window.detachContentClickHandler();
    if (!window.shadow) return;
    window.ttsClickHandler = (e) => {
        const path = e.composedPath();
        const target = path.find(el => el.matches && el.matches('p, h1, h2, h3, h4, h5, h6, li, blockquote, div'));
        if (!target) return;
        const now = Date.now();
        if (now - window.lastTap < 350 && window.lastNode === target) {
            window.lastTap = 0; e.preventDefault(); e.stopPropagation();
            let paras = window.TTS.paragraphs;
            if (!window.TTS.active || !paras.length) { paras = window.TTS.collectParagraphs(); }
            const idx = paras.indexOf(target);
            if (idx >= 0) { if (!window.TTS.active) { window.TTS.start({ startIndex: idx, freshStart: true }); } else { window.TTS.speakAt(idx, true); } }
        } else { window.lastTap = now; window.lastNode = target; }
    };
    window.shadow.addEventListener('click', window.ttsClickHandler, true);
};

window.detachContentClickHandler = function() {
    if (window.ttsClickHandler && window.shadow) { window.shadow.removeEventListener('click', window.ttsClickHandler, true); window.ttsClickHandler = null; }
};

window.openDownloadDialog = function() {
    if (!window.state.currentPlaylist) { window.toast('Open a playlist first', 'warn'); return; }
    const total = window.state.currentPlaylist.entries.length;
    const html = `
    <div class="modal-backdrop" id="dl-backdrop">
        <div class="modal" role="dialog">
        <button class="close-btn" id="dl-close"><i class="fa-solid fa-xmark"></i></button>
        <h2><i class="fa-solid fa-download"></i> Download as audio</h2>
        <p style="font-size:12px;color:var(--muted);line-height:1.5">Records TTS output to WAV/WebM files for the selected chapters. This runs in the foreground — keep the tab open.</p>
        <div class="field">
            <label>Scope</label>
            <select class="select-input" id="dl-scope">
            <option value="current">Current chapter only</option>
            <option value="all">Entire playlist (${total} chapters)</option>
            </select>
        </div>
        <button class="btn primary" id="dl-start" style="width:100%"><i class="fa-solid fa-play"></i> Start recording</button>
        </div>
    </div>`;
    const div = document.createElement('div'); div.innerHTML = html; document.body.appendChild(div.firstElementChild);
    const backdrop = window.$('dl-backdrop');
    function close(){ backdrop.remove(); }
    window.$('dl-close').addEventListener('click', close);
    backdrop.addEventListener('click', e => { if (e.target === backdrop) close(); });
    window.$('dl-start').addEventListener('click', async () => {
        const scope = window.$('dl-scope').value; close(); await window.runDownloadJob(scope);
    });
};

window.runDownloadJob = async function(scope) {
    const pl = window.state.currentPlaylist;
    const cur = window.state.currentEntryIndex;
    let indices = [];
    if (scope === 'current') indices = [cur]; else indices = pl.entries.map((_, i) => i);

    const overlay = document.createElement('div');
    overlay.className = 'download-overlay visible';
    overlay.innerHTML = `<div class="card">
        <h3><i class="fa-solid fa-microphone-lines"></i> Recording audio</h3>
        <div class="subtitle" id="dl-sub">0 / ${indices.length}</div>
        <div id="dl-list"></div>
        <button class="btn" id="dl-cancel" style="width:100%;margin-top:10px"><i class="fa-solid fa-stop"></i> Stop</button>
    </div>`;
    document.body.appendChild(overlay);
    let cancelled = false;
    overlay.querySelector('#dl-cancel').addEventListener('click', () => { cancelled = true; overlay.remove(); window.toast('Cancelled', 'warn'); });

    const listEl = overlay.querySelector('#dl-list');
    const subEl = overlay.querySelector('#dl-sub');
    window.TTS.stop({ silent: true, preserveAutoNext: false });

    for (let n = 0; n < indices.length; n++) {
        if (cancelled) return;
        const idx = indices[n];
        const entry = pl.entries[idx];
        subEl.textContent = `${n + 1} / ${indices.length}`;
        const item = document.createElement('div'); item.className = 'dl-progress-item';
        item.innerHTML = `<div class="file-name"><span class="n">${window.escapeHtml(entry.name)}</span><span class="pct">0%</span></div><div class="bar"><div></div></div>`;
        listEl.appendChild(item); listEl.scrollTop = listEl.scrollHeight;
        try {
            await window.loadEntry(idx);
            await window.recordChapterToFile(entry, item);
            item.classList.add('done'); item.querySelector('.pct').textContent = '100%'; item.querySelector('.bar>div').style.width = '100%';
        } catch (err) {
            console.error(err); item.classList.add('error'); item.querySelector('.pct').textContent = 'ERR'; window.toast('Chapter failed: ' + err.message, 'error');
        }
    }
    if (!cancelled) {
        subEl.textContent = `Done — ${indices.length} file(s)`;
        const cbtn = overlay.querySelector('#dl-cancel');
        cbtn.innerHTML = '<i class="fa-solid fa-check"></i> Close';
        cbtn.addEventListener('click', () => overlay.remove());
    }
};

window.recordChapterToFile = async function(entry, itemEl) {
    let paras = window.TTS.collectParagraphs();
    if (!paras.length) throw new Error('No paragraphs to speak');

    const useRealCapture = confirm(`Enable real audio capture for "${entry.name}"?\n\nOK = share this tab with audio (browser prompt), Cancel = save as .txt only.`);
    const total = paras.length;
    let recorder = null, chunks = [], stream = null;

    if (useRealCapture) {
        try {
            stream = await navigator.mediaDevices.getDisplayMedia({ audio: true, video: { width: 1, height: 1 } });
            const audioTracks = stream.getAudioTracks();
            if (!audioTracks.length) { stream.getTracks().forEach(t => t.stop()); throw new Error('No audio track — tick "Share tab audio"'); }
            const aStream = new MediaStream(audioTracks);
            recorder = new MediaRecorder(aStream, { mimeType: MediaRecorder.isTypeSupported('audio/webm;codecs=opus') ? 'audio/webm;codecs=opus' : 'audio/webm' });
            recorder.ondataavailable = e => { if (e.data.size) chunks.push(e.data); };
            recorder.start();
        } catch (err) { if (stream) stream.getTracks().forEach(t => t.stop()); throw err; }
    } else {
        const text = paras.map(p => p.textContent.trim()).join('\n\n');
        const blob = new Blob([text], { type: 'text/plain' });
        const a = document.createElement('a'); a.href = URL.createObjectURL(blob); a.download = entry.name.replace(/\.[^.]+$/, '') + '.txt'; a.click();
        setTimeout(() => URL.revokeObjectURL(a.href), 2000);
        return;
    }

    for (let i = 0; i < paras.length; i++) {
        await new Promise(async (resolve, reject) => {
            const text = window.TTS.applyReplacements(paras[i].textContent || '');
            if (!text || text.length < 2) return resolve();
            const engine = window.TTS.pickEngine();
            if (engine === 'piper') {
                const voiceId = window.settings.piperVoice || window.DEFAULT_PIPER_VOICE;
                if (!window.Piper.loaded || window.Piper.activeVoice !== voiceId) {
                    const ok = await window.Piper.ensureVoice(voiceId);
                    if (!ok) return fallback(text, resolve, reject);
                }
                try {
                    const url = await window.Piper.synthesize(text, voiceId);
                    const a = new Audio(url); a.playbackRate = parseFloat(window.settings.ttsRate) || 1.0; a.onended = resolve; a.onerror = () => reject(new Error('Audio playback failed')); a.play().catch(reject);
                } catch(e) { fallback(text, resolve, reject); }
            } else { fallback(text, resolve, reject); }

            function fallback(txt, res, rej) {
                const u = new SpeechSynthesisUtterance(txt); u.rate = parseFloat(window.settings.ttsRate) || 1.0;
                const voices = speechSynthesis.getVoices();
                if (window.settings.deviceVoice) { const v = voices.find(x => x.name === window.settings.deviceVoice); if (v) u.voice = v; }
                u.onend = res; u.onerror = e => { if (e.error === 'interrupted' || e.error === 'canceled') res(); else rej(new Error('TTS: ' + e.error)); };
                speechSynthesis.speak(u);
            }
        });
        const pct = Math.round(((i + 1) / total) * 100);
        itemEl.querySelector('.pct').textContent = pct + '%'; itemEl.querySelector('.bar>div').style.width = pct + '%';
    }
    await new Promise(res => { recorder.onstop = res; recorder.stop(); });
    stream.getTracks().forEach(t => t.stop());
    const blob = new Blob(chunks, { type: recorder.mimeType });
    const a = document.createElement('a'); a.href = URL.createObjectURL(blob); a.download = entry.name.replace(/\.[^.]+$/, '') + '.webm'; a.click();
    setTimeout(() => URL.revokeObjectURL(a.href), 3000);
};
