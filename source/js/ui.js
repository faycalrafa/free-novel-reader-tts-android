window.shadow = window.$('reader-content').attachShadow({ mode: 'open' });

window.renderHTMLInto = function(html, name) {
    if (window.TTS.active) window.TTS.stop({ silent: true, preserveAutoNext: false });
    const readerContent = window.$('reader-content');
    readerContent.onscroll = null;
    window.app.scrollFireGuard = false;

    let doc;
    try {
        const parser = new DOMParser();
        doc = parser.parseFromString(html, 'text/html');
    } catch (err) { window.toast('Could not parse text: ' + err.message, 'error'); return; }

    doc.querySelectorAll('script, link[rel="stylesheet"], base, iframe').forEach(el => el.remove());
    doc.querySelectorAll('form').forEach(el => el.removeAttribute('action'));
    
    const inlineStyles = Array.from(doc.querySelectorAll('style')).map(s => s.textContent).join('\n');
    doc.querySelectorAll('style').forEach(s => s.remove());
    const body = doc.body || doc.documentElement;

    window.shadow.innerHTML = `
        <style>
            :host { display: block; min-height: 100%; color: inherit; font-family: inherit; }
            * { color: inherit; }
            a { color: var(--na-link); }
            img { max-width: 100%; height: auto; border-radius: 6px; }
            h1, h2, h3 { line-height: 1.3; margin-top: 1.2em; margin-bottom: 0.6em; }
            p { margin-bottom: 1em; }
            pre { overflow-x: auto; white-space: pre-wrap; word-wrap: break-word; }
            .na-tts-highlight {
                background: var(--na-highlight) !important;
                border-radius: 4px !important;
                padding: 2px 4px !important;
                margin: -2px -4px !important;
                transition: background 0.3s;
                outline: none !important;
            }
            ${inlineStyles}
        </style>
    `;

    const frag = document.createDocumentFragment();
    Array.from(body.childNodes).forEach(node => frag.appendChild(node));
    window.shadow.appendChild(frag);

    window.applyReaderTheme();
    window.$('empty-state').classList.add('hidden');
    readerContent.style.display = 'block';

    const title = (doc.title || '').trim() || name;
    window.$('page-title').textContent = title;
    window.$('page-sub').textContent = name;

    readerContent.scrollTop = 0;
    window.attachScrollHandler();
    window.updateMediaSessionMetadata(title, name);
    window.attachContentClickHandler();
};

window.applyReaderTheme = function() {
    const readerContent = window.$('reader-content');
    readerContent.classList.remove('dark', 'sepia');
    if (window.settings.theme === 'dark') readerContent.classList.add('dark');
    else if (window.settings.theme === 'sepia') readerContent.classList.add('sepia');
    readerContent.style.fontSize = window.settings.fontSize + 'px';
};

window.renderResumeCard = async function() {
    if (!window.settings.rememberProgress) { window.$('resume-container').innerHTML = ''; return; }
    const lastId = localStorage.getItem('novelArrowReader::lastLibId');
    if (!lastId) return;
    const lib = window.state.library.find(l => l.id === lastId);
    if (!lib) return;
    const pl = await window.idbGet(window.STORES.playlists, lastId);
    if (!pl) return;
    const idx = pl.progress?.index || 0;
    const chap = pl.entries[idx]?.name || '';
    window.$('resume-container').innerHTML = `
        <div class="resume-card">
            <div class="rc-title"><i class="fa-solid fa-bookmark"></i> Continue reading</div>
            <div class="rc-name">${window.escapeHtml(lib.name)}</div>
            <div class="rc-sub">Chapter ${idx + 1} / ${pl.entries.length} — ${window.escapeHtml(chap)}</div>
            <div class="rc-actions">
                <button class="btn-sm primary" id="btn-resume-continue"><i class="fa-solid fa-play"></i> Resume</button>
                <button class="btn-sm ghost" id="btn-resume-dismiss"><i class="fa-solid fa-xmark"></i> Dismiss</button>
            </div>
        </div>
    `;
    window.$('btn-resume-continue').addEventListener('click', () => window.openLibraryEntry(lastId));
    window.$('btn-resume-dismiss').addEventListener('click', () => window.$('resume-container').innerHTML = '');
};

window.setLoopButtonState = function(active) {
    window.app.loopActive = active;
    const btnLoop = window.$('btn-loop');
    if (active) {
        btnLoop.classList.add('active');
        btnLoop.innerHTML = '<i class="fa-solid fa-stop"></i>';
        btnLoop.title = 'Stop auto-next loop';
        window.toast(`Auto-next enabled (${window.settings.loopCount === 0 ? '∞' : window.settings.loopCount} chapters)`, 'success');
    } else {
        btnLoop.classList.remove('active');
        btnLoop.innerHTML = '<i class="fa-solid fa-rotate"></i>';
        btnLoop.title = 'Start auto-next loop';
    }
};

window.$('btn-loop').addEventListener('click', () => {
    if (window.app.loopActive) { window.setLoopButtonState(false); return; }
    if (!window.state.currentPlaylist) { window.toast('Load a playlist first', 'warn'); return; }
    window.app.loopRemaining = window.settings.loopCount === 0 ? Infinity : (window.settings.loopCount || 100);
    window.setLoopButtonState(true);
});

window.updateFabNext = function() {
    const hasNext = window.state.currentPlaylist && window.state.currentEntryIndex < window.state.currentPlaylist.entries.length - 1;
    if (window.$('tts-dock').classList.contains('visible') || !hasNext) window.$('fab-next').classList.add('hidden');
    else window.$('fab-next').classList.remove('hidden');
};

window.$('fab-next').addEventListener('click', () => {
    if (!window.state.currentPlaylist) { window.toast('No playlist loaded', 'warn'); return; }
    window.goToNextItem();
});

window.triggerAutoNext = function(reason) {
    if (!window.app.loopActive) return;
    if (window.TTS.active) { window.TTS.pendingNext = true; return; }
    window.doAutoNext(reason);
};

window.doAutoNext = function(reason) {
    if (!window.app.loopActive) return;
    if (window.app.loopRemaining !== Infinity) {
        window.app.loopRemaining -= 1;
        if (window.app.loopRemaining <= 0) {
            window.setLoopButtonState(false);
            window.toast('Loop finished', 'info');
            return;
        }
    }
    const delay = Math.max(50, parseInt(window.settings.countdownMs, 10) || 1000);
    setTimeout(() => {
        if (!window.app.loopActive) return;
        const wasIndex = window.state.currentEntryIndex;
        window.goToNextItem();
        if (window.state.currentEntryIndex === wasIndex) window.setLoopButtonState(false);
    }, delay);
};

window.attachScrollHandler = function() {
    const readerContent = window.$('reader-content');
    readerContent.onscroll = () => {
        if (window.app.scrollSaveTimer) clearTimeout(window.app.scrollSaveTimer);
        window.app.scrollSaveTimer = setTimeout(() => {
            if (window.state.currentPlaylist) {
                window.state.currentPlaylist.progress.scrollTop = readerContent.scrollTop || 0;
                window.savePlaylistProgress();
            }
        }, 400);
        
        const nearBottom = readerContent.scrollTop + readerContent.clientHeight >= readerContent.scrollHeight - 6;
        if (nearBottom && !window.app.scrollFireGuard) {
            window.app.scrollFireGuard = true;
            setTimeout(() => (window.app.scrollFireGuard = false), 4000);
            if (window.settings.autoNextOnScroll || window.app.loopActive) {
                window.triggerAutoNext('scroll-bottom');
            }
        }
    };
};

window.$('btn-library').addEventListener('click', window.openDrawer);
window.$('drawer-close').addEventListener('click', window.closeDrawer);
window.$('drawer-backdrop').addEventListener('click', window.closeDrawer);
window.$('empty-open-library').addEventListener('click', window.openDrawer);

// Refresh / re-sync all root folders (remaps subfolders → playlists)
window.$('drawer-refresh').addEventListener('click', async () => {
    const btn = window.$('drawer-refresh');
    btn.classList.add('busy');
    btn.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i>';
    try { await window.refreshAllRoots(); }
    catch (e) { window.toast('Refresh failed: ' + (e.message || e), 'error'); }
    finally { btn.classList.remove('busy'); btn.innerHTML = '<i class="fa-solid fa-rotate"></i>'; }
});

document.querySelectorAll('.drawer-tabs button').forEach(b => {
    b.addEventListener('click', () => {
        document.querySelectorAll('.drawer-tabs button').forEach(x => x.classList.remove('active'));
        b.classList.add('active');
        window.drawerTab = b.dataset.tab;
        window.renderDrawer();
    });
});

window.$('empty-open-folder').addEventListener('click', window.addFolder);
window.$('drawer-add-folder').addEventListener('click', window.addFolder);
window.$('empty-open-files').addEventListener('click', () => window.$('file-input-multi').click());
window.$('drawer-add-files').addEventListener('click', () => window.$('file-input-multi').click());

window.$('file-input-multi').addEventListener('change', async () => {
    const fileInputMulti = window.$('file-input-multi');
    const files = Array.from(fileInputMulti.files || []).filter(f => window.isTextFile(f.name, f.type));
    fileInputMulti.value = '';
    if (!files.length) { window.toast('No supported text/HTML files selected', 'warn'); return; }
    const name = files.length === 1 ? files[0].name : `Files (${new Date().toLocaleString()})`;
    await window.ingestFilesAsFolder(name, files);
});

window.$('btn-tts-toggle').addEventListener('click', () => {
    window.$('tts-dock').classList.toggle('visible');
    window.updateFabNext();
});

window.$('piper-cancel').addEventListener('click', () => window.Piper.cancelLoad());

window.$('tts-progress-wrap').addEventListener('click', (e) => {
    const wrap = window.$('tts-progress-wrap');
    const rect = wrap.getBoundingClientRect();
    const pct = (e.clientX - rect.left) / rect.width;
    window.TTS.seekPct(pct);
});

window.$('tts-stop').addEventListener('click', () => window.TTS.stop());
window.$('tts-prev-para').addEventListener('click', () => { if (window.TTS.active) window.TTS.speakAt(Math.max(0, window.TTS.index - 1), true); });
window.$('tts-next-para').addEventListener('click', () => { if (window.TTS.active) window.TTS.speakAt(window.TTS.index + 1, true); });
window.$('tts-prev-chapter').addEventListener('click', () => { if (window.state.currentEntryIndex > 0) window.loadEntry(window.state.currentEntryIndex - 1); });
window.$('tts-next-chapter').addEventListener('click', () => window.goToNextItem());
window.$('tts-playpause').addEventListener('click', () => { if (!window.TTS.active) { window.TTS.start(); return; } window.TTS.togglePause(); });

window.$('btn-download-mp3').addEventListener('click', window.openDownloadDialog);

window.modalEl = null;
window.$('btn-settings').addEventListener('click', openSettings);

function openSettings() {
    if (window.modalEl) return;
    window.settings = window.loadSettings();
    window.modalEl = document.createElement('div');
    window.modalEl.className = 'modal-backdrop';
    window.modalEl.innerHTML = `
        <div class="modal">
            <button class="close-btn" id="modal-close" aria-label="Close"><i class="fa-solid fa-xmark"></i></button>
            <h2><i class="fa-solid fa-gear"></i> Settings</h2>

            <h3><i class="fa-solid fa-palette"></i> Reading</h3>
            <div class="field">
                <label>Theme</label>
                <div class="segmented" id="seg-theme">
                    <button data-val="light">☀️ Light</button>
                    <button data-val="sepia">📜 Sepia</button>
                    <button data-val="dark">🌙 Dark</button>
                </div>
            </div>
            <div class="field">
                <label>Font size: <span id="s-font-size-val">${window.settings.fontSize}px</span></label>
                <input type="range" min="12" max="24" step="1" id="s-font-size" value="${window.settings.fontSize}">
            </div>

            <h3><i class="fa-solid fa-microphone-lines"></i> Text-to-Speech</h3>
            <div class="field">
                <label>Engine</label>
                <div class="segmented" id="seg-engine">
                    <button data-val="auto">Auto</button>
                    <button data-val="piper">Piper <span class="piper-badge">NEURAL</span></button>
                    <button data-val="device">Device</button>
                </div>
            </div>
            <div class="field">
                <label>Piper Voice <span id="piper-status-text" style="font-size:10px;color:var(--muted);"></span></label>
                <div class="voice-row">
                    <select class="select-input" id="s-piper-voice"></select>
                    <button class="voice-preview-btn" id="btn-preview-piper" title="Hear sample">
                        <i class="fa-solid fa-play"></i>
                    </button>
                </div>
                <div class="hint">Neural offline TTS. First use downloads the voice model (~63–114 MB), cached persistently in OPFS.</div>
            </div>
            <div class="field">
                <label>Device Voice (fallback)</label>
                <div class="voice-row">
                    <select class="select-input" id="s-device-voice"></select>
                    <button class="voice-preview-btn" id="btn-preview-device" title="Hear sample">
                        <i class="fa-solid fa-play"></i>
                    </button>
                </div>
            </div>
            <div class="field">
                <label>Rate: <span id="s-rate-val">${window.settings.ttsRate.toFixed(2)}x</span></label>
                <input type="range" min="0.5" max="2" step="0.05" id="s-rate" value="${window.settings.ttsRate}">
            </div>
            <div class="row between" style="margin-bottom:10px;">
                <label style="margin:0;font-size:12px;color:var(--muted);font-weight:600;">Auto-scroll to follow TTS</label>
                <label class="toggle"><input type="checkbox" id="s-tts-autoscroll" ${window.settings.ttsAutoScroll ? 'checked' : ''}><span class="slider"></span></label>
            </div>
            <div class="row between" style="margin-bottom:10px;">
                <label style="margin:0;font-size:12px;color:var(--muted);font-weight:600;">Auto-next chapter when TTS finishes</label>
                <label class="toggle"><input type="checkbox" id="s-tts-autonext" ${window.settings.autoNextOnTTSEnd ? 'checked' : ''}><span class="slider"></span></label>
            </div>

            <h3><i class="fa-solid fa-mobile-screen"></i> Background & Media Controls</h3>
            <div class="row between" style="margin-bottom:10px;">
                <label style="margin:0;font-size:12px;color:var(--muted);font-weight:600;">Background playback (keeps reading with screen off)</label>
                <label class="toggle"><input type="checkbox" id="s-bg-playback" ${window.settings.backgroundPlayback ? 'checked' : ''}><span class="slider"></span></label>
            </div>
            <div class="row between" style="margin-bottom:10px;">
                <label style="margin:0;font-size:12px;color:var(--muted);font-weight:600;">Show media controls (notification panel / lock screen)</label>
                <label class="toggle"><input type="checkbox" id="s-media-session" ${window.settings.mediaSessionEnabled ? 'checked' : ''}><span class="slider"></span></label>
            </div>

            <h3><i class="fa-solid fa-forward"></i> Auto-Next</h3>
            <div class="field">
                <label>Loop count (0 = infinite)</label>
                <input class="text-input" type="number" id="s-loop-count" min="0" step="1" value="${window.settings.loopCount}">
            </div>
            <div class="field">
                <label>Delay between chapters (ms)</label>
                <input class="text-input" type="number" id="s-countdown" min="50" step="50" value="${window.settings.countdownMs}">
            </div>
            <div class="row between" style="margin-bottom:10px;">
                <label style="margin:0;font-size:12px;color:var(--muted);font-weight:600;">Auto-next when scrolled to bottom</label>
                <label class="toggle"><input type="checkbox" id="s-scroll-bottom" ${window.settings.autoNextOnScroll ? 'checked' : ''}><span class="slider"></span></label>
            </div>

            <h3><i class="fa-solid fa-crop"></i> Content</h3>
            <div class="field">
                <label>Content container selector</label>
                <input class="text-input" type="text" id="s-content-selector" placeholder="e.g. #chapter-content" value="${window.escapeAttr(window.settings.contentSelector)}">
                <div class="hint">Optional CSS selector to restrict TTS reading to a specific element.</div>
            </div>

            <h3><i class="fa-solid fa-ban"></i> Ignored Elements (TTS skips these)</h3>
            <div class="ignore-list-wrap" id="s-ignore-list"></div>
            <div class="add-ignore">
                <input class="text-input" type="text" id="s-ignore-input" placeholder="CSS selector, e.g. .comments, #ads">
                <button class="btn-sm primary" id="s-ignore-add"><i class="fa-solid fa-plus"></i></button>
            </div>
            <div style="margin-top:8px;">
                <button class="btn-sm ghost" id="s-ignore-reset"><i class="fa-solid fa-rotate-left"></i> Reset to defaults</button>
            </div>

            <h3 style="margin-top:18px;"><i class="fa-solid fa-forward"></i> TTS Skip Words</h3>
            <div class="list" id="s-skip-list"></div>
            <button class="btn-sm primary" id="s-add-skip" style="margin-top:8px;"><i class="fa-solid fa-plus"></i> Add skip word</button>

            <h3><i class="fa-solid fa-language"></i> Pronunciation Corrections</h3>
            <div class="list" id="s-repl-list"></div>
            <button class="btn-sm primary" id="s-add-repl" style="margin-top:8px;"><i class="fa-solid fa-plus"></i> Add rule</button>

            <h3><i class="fa-solid fa-database"></i> Persistence</h3>
            <div class="row between" style="margin-bottom:10px;">
                <label style="margin:0;font-size:12px;color:var(--muted);font-weight:600;">Save reading progress</label>
                <label class="toggle"><input type="checkbox" id="s-remember-progress" ${window.settings.rememberProgress ? 'checked' : ''}><span class="slider"></span></label>
            </div>
            <div style="display:flex;gap:6px;margin-top:8px;">
                <button class="btn-sm ghost" id="s-clear-piper"><i class="fa-solid fa-trash"></i> Clear cached voices</button>
            </div>

            <div style="display:flex;gap:8px;margin-top:20px;justify-content:flex-end;">
                <button class="btn-sm" id="s-reset">Reset settings</button>
                <button class="btn-sm primary" id="s-save"><i class="fa-solid fa-check"></i> Save</button>
            </div>
        </div>
    `;
    document.body.appendChild(window.modalEl);
    window.modalEl.addEventListener('click', (e) => { if (e.target === window.modalEl) closeSettings(); });
    window.$('modal-close').addEventListener('click', closeSettings);

    setSegmentedActive('seg-theme', window.settings.theme); setSegmentedActive('seg-engine', window.settings.engine);
    window.modalEl.querySelectorAll('.segmented button').forEach(b => {
        b.addEventListener('click', () => {
            b.parentElement.querySelectorAll('button').forEach(x => x.classList.remove('active')); b.classList.add('active');
        });
    });

    const piperStatusEl = window.modalEl.querySelector('#piper-status-text');
    if (window.Piper.loaded) piperStatusEl.textContent = `✓ ${window.Piper.activeVoice} loaded`;
    else piperStatusEl.textContent = '⚠ Not loaded — downloads on first use';

    populatePiperVoices(); populateDeviceVoices();
    
    const rateInput = window.modalEl.querySelector('#s-rate'); const rateVal = window.modalEl.querySelector('#s-rate-val');
    rateInput.addEventListener('input', () => rateVal.textContent = parseFloat(rateInput.value).toFixed(2) + 'x');
    const fsInput = window.modalEl.querySelector('#s-font-size'); const fsVal = window.modalEl.querySelector('#s-font-size-val');
    fsInput.addEventListener('input', () => fsVal.textContent = fsInput.value + 'px');

    renderSkipList(); renderReplList(); renderIgnoreList();

    window.modalEl.querySelector('#s-add-skip').addEventListener('click', () => { window.settings.ttsSkipList.push({ text: '', exact: false }); renderSkipList(); });
    window.modalEl.querySelector('#s-add-repl').addEventListener('click', () => { window.settings.ttsReplacements.push({ from: '', to: '', exact: false }); renderReplList(); });
    window.modalEl.querySelector('#s-ignore-add').addEventListener('click', () => {
        const inp = window.modalEl.querySelector('#s-ignore-input'); const val = inp.value.trim();
        if (!val) return; if (!window.settings.ttsIgnoreSelectors.includes(val)) window.settings.ttsIgnoreSelectors.push(val);
        inp.value = ''; renderIgnoreList();
    });
    window.modalEl.querySelector('#s-ignore-reset').addEventListener('click', () => { window.settings.ttsIgnoreSelectors = window.DEFAULT_IGNORE_SELECTORS.slice(); renderIgnoreList(); });

    window.modalEl.querySelector('#s-save').addEventListener('click', persistFromModal);
    window.modalEl.querySelector('#s-reset').addEventListener('click', () => {
        if (confirm('Reset ALL settings to defaults?')) { window.settings = { ...window.DEFAULT_SETTINGS, ttsIgnoreSelectors: window.DEFAULT_IGNORE_SELECTORS.slice() }; window.saveSettings(window.settings); closeSettings(); openSettings(); window.toast('Settings reset', 'success'); }
    });
    window.modalEl.querySelector('#s-clear-piper').addEventListener('click', async () => {
        if (!confirm('Delete all cached Piper voice models from your device?')) return;
        try { if (!window.Piper.module) await window.Piper._loadModule(); await window.Piper.module.flush(); window.Piper.loaded = false; window.Piper.activeVoice = null; window.toast('Piper cache cleared', 'success'); piperStatusEl.textContent = '⚠ Not loaded — downloads on first use'; } catch(e) { window.toast('Could not clear cache: ' + (e.message || e), 'error'); }
    });

    window.$('btn-preview-piper').addEventListener('click', async (e) => {
        const btn = e.currentTarget; const voiceId = window.modalEl.querySelector('#s-piper-voice').value;
        if (!voiceId) { window.toast('Pick a voice first', 'warn'); return; }
        btn.classList.add('busy'); btn.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i>';
        try { const wasActive = window.TTS.active; if (wasActive) window.TTS._stopSilent(); await window.Piper.previewVoice(voiceId); }
        catch (err) { window.toast('Preview failed: ' + (err.message || err), 'error'); }
        finally { btn.classList.remove('busy'); btn.innerHTML = '<i class="fa-solid fa-play"></i>'; }
    });

    window.$('btn-preview-device').addEventListener('click', (e) => {
        const btn = e.currentTarget; const voiceName = window.modalEl.querySelector('#s-device-voice').value; const rate = parseFloat(window.modalEl.querySelector('#s-rate').value) || 1.0;
        try { speechSynthesis.cancel(); } catch {}
        const u = new SpeechSynthesisUtterance("Hi there! This is a preview of my voice. I hope you enjoy the way I sound."); u.rate = rate;
        const voices = speechSynthesis.getVoices(); if (voiceName) { const v = voices.find(x => x.name === voiceName); if (v) u.voice = v; }
        btn.classList.add('busy'); btn.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i>';
        u.onend = u.onerror = () => { btn.classList.remove('busy'); btn.innerHTML = '<i class="fa-solid fa-play"></i>'; }; speechSynthesis.speak(u);
    });
}

function setSegmentedActive(id, val) { const box = window.modalEl.querySelector('#' + id); if (!box) return; box.querySelectorAll('button').forEach(b => { b.classList.toggle('active', b.dataset.val === val); }); }
function getSegmentedValue(id) { const box = window.modalEl.querySelector('#' + id); if (!box) return ''; const b = box.querySelector('button.active'); return b ? b.dataset.val : ''; }
function closeSettings() { if (window.modalEl) { window.modalEl.remove(); window.modalEl = null; } }

function populatePiperVoices() {
    const sel = window.modalEl.querySelector('#s-piper-voice'); sel.innerHTML = '';
    const groups = [ { label: '🇺🇸 US Voices', filter: v => v.lang === 'en-US' }, { label: '🇬🇧 UK Voices', filter: v => v.lang === 'en-GB' } ];
    for (const g of groups) {
        const list = window.PIPER_VOICES.filter(g.filter); if (!list.length) continue;
        const og = document.createElement('optgroup'); og.label = g.label;
        list.forEach(v => {
            const o = document.createElement('option'); o.value = v.id; o.textContent = `${v.label} — ${v.quality} (${v.size})`; o.title = v.notes;
            if (v.id === window.settings.piperVoice) o.selected = true; og.appendChild(o);
        });
        sel.appendChild(og);
    }
}

function populateDeviceVoices() {
    const sel = window.modalEl.querySelector('#s-device-voice');
    const fill = () => {
        const voices = speechSynthesis.getVoices(); const englishVoices = voices.filter(v => v.lang && v.lang.toLowerCase().startsWith('en')); sel.innerHTML = '';
        const def = document.createElement('option'); def.value = ''; def.textContent = '— Auto (best English match) —'; sel.appendChild(def);
        if (!englishVoices.length) { const o = document.createElement('option'); o.value = ''; o.textContent = 'No English voices installed'; o.disabled = true; sel.appendChild(o); return; }
        const local = englishVoices.filter(v => v.localService); const network = englishVoices.filter(v => !v.localService);
        if (local.length) { const og = document.createElement('optgroup'); og.label = '⚡ Local voices'; local.forEach(v => { const o = document.createElement('option'); o.value = v.name; o.textContent = `${v.name} (${v.lang})`; if (v.name === window.settings.deviceVoice) o.selected = true; og.appendChild(o); }); sel.appendChild(og); }
        if (network.length) { const og = document.createElement('optgroup'); og.label = '☁️ Network voices'; network.forEach(v => { const o = document.createElement('option'); o.value = v.name; o.textContent = `${v.name} (${v.lang})`; if (v.name === window.settings.deviceVoice) o.selected = true; og.appendChild(o); }); sel.appendChild(og); }
    };
    fill(); if (speechSynthesis.getVoices().length === 0) speechSynthesis.onvoiceschanged = fill;
}

function renderIgnoreList() {
    const wrap = window.modalEl.querySelector('#s-ignore-list'); wrap.innerHTML = '';
    if (!window.settings.ttsIgnoreSelectors.length) { wrap.innerHTML = `<div style="color:var(--muted);font-size:11px;padding:6px;">No selectors ignored. Add one below.</div>`; return; }
    window.settings.ttsIgnoreSelectors.forEach((sel, i) => { const tag = document.createElement('span'); tag.className = 'tag-row'; tag.innerHTML = `<code>${window.escapeHtml(sel)}</code><button title="Remove"><i class="fa-solid fa-xmark"></i></button>`; tag.querySelector('button').addEventListener('click', () => { window.settings.ttsIgnoreSelectors.splice(i, 1); renderIgnoreList(); }); wrap.appendChild(tag); });
}

function renderSkipList() {
    const wrap = window.modalEl.querySelector('#s-skip-list'); wrap.innerHTML = '';
    if (!window.settings.ttsSkipList.length) { const em = document.createElement('div'); em.style.cssText = 'color:var(--muted);font-size:11px;padding:6px;'; em.textContent = 'No skip words. Words added here will be removed from spoken text.'; wrap.appendChild(em); }
    window.settings.ttsSkipList.forEach((item, i) => { const row = document.createElement('div'); row.className = 'list-item'; row.innerHTML = `<input class="text-input" type="text" placeholder="word or phrase" value="${window.escapeAttr(item.text)}"><label class="toggle" title="Match whole word only"><input type="checkbox" ${item.exact ? 'checked' : ''}><span class="slider"></span></label><button class="btn-sm danger" title="Remove"><i class="fa-solid fa-trash"></i></button>`; const [inp, tglLabel, delBtn] = [row.children[0], row.children[1], row.children[2]]; inp.addEventListener('input', () => window.settings.ttsSkipList[i].text = inp.value); tglLabel.querySelector('input').addEventListener('change', (e) => window.settings.ttsSkipList[i].exact = e.target.checked); delBtn.addEventListener('click', () => { window.settings.ttsSkipList.splice(i, 1); renderSkipList(); }); wrap.appendChild(row); });
}

function renderReplList() {
    const wrap = window.modalEl.querySelector('#s-repl-list'); wrap.innerHTML = '';
    if (!window.settings.ttsReplacements.length) { const em = document.createElement('div'); em.style.cssText = 'color:var(--muted);font-size:11px;padding:6px;'; em.textContent = 'No pronunciation rules. Add mispronounced names → phonetic spelling.'; wrap.appendChild(em); }
    window.settings.ttsReplacements.forEach((item, i) => { const row = document.createElement('div'); row.className = 'list-item'; row.style.flexWrap = 'wrap'; row.innerHTML = `<input class="text-input" type="text" placeholder="target text" value="${window.escapeAttr(item.from)}" style="flex:1 1 130px;"><input class="text-input" type="text" placeholder="replacement" value="${window.escapeAttr(item.to)}" style="flex:1 1 130px;"><label class="toggle" title="Match whole word only"><input type="checkbox" ${item.exact ? 'checked' : ''}><span class="slider"></span></label><button class="btn-sm danger" title="Remove"><i class="fa-solid fa-trash"></i></button>`; const [fromInp, toInp, tglLabel, delBtn] = [row.children[0], row.children[1], row.children[2], row.children[3]]; fromInp.addEventListener('input', () => window.settings.ttsReplacements[i].from = fromInp.value); toInp.addEventListener('input', () => window.settings.ttsReplacements[i].to = toInp.value); tglLabel.querySelector('input').addEventListener('change', (e) => window.settings.ttsReplacements[i].exact = e.target.checked); delBtn.addEventListener('click', () => { window.settings.ttsReplacements.splice(i, 1); renderReplList(); }); wrap.appendChild(row); });
}

function persistFromModal() {
    try {
        window.settings.theme = getSegmentedValue('seg-theme') || 'dark'; window.settings.engine = getSegmentedValue('seg-engine') || 'device';
        window.settings.fontSize = Math.min(24, Math.max(12, parseInt(window.modalEl.querySelector('#s-font-size').value, 10) || 16));
        window.settings.piperVoice = window.modalEl.querySelector('#s-piper-voice').value || window.DEFAULT_PIPER_VOICE; window.settings.deviceVoice = window.modalEl.querySelector('#s-device-voice').value || '';
        window.settings.ttsRate = parseFloat(window.modalEl.querySelector('#s-rate').value) || 1.0; window.settings.ttsAutoScroll = window.modalEl.querySelector('#s-tts-autoscroll').checked;
        window.settings.autoNextOnTTSEnd = window.modalEl.querySelector('#s-tts-autonext').checked; window.settings.backgroundPlayback = window.modalEl.querySelector('#s-bg-playback').checked;
        window.settings.mediaSessionEnabled = window.modalEl.querySelector('#s-media-session').checked; window.settings.loopCount = Math.max(0, parseInt(window.modalEl.querySelector('#s-loop-count').value, 10) || 0);
        window.settings.countdownMs = Math.max(50, parseInt(window.modalEl.querySelector('#s-countdown').value, 10) || 1000); window.settings.autoNextOnScroll = window.modalEl.querySelector('#s-scroll-bottom').checked;
        window.settings.contentSelector = window.modalEl.querySelector('#s-content-selector').value.trim(); window.settings.rememberProgress = window.modalEl.querySelector('#s-remember-progress').checked;
        window.settings.ttsSkipList = window.settings.ttsSkipList.filter(s => s.text && s.text.trim()); window.settings.ttsReplacements = window.settings.ttsReplacements.filter(r => r.from && r.from.trim());
        window.saveSettings(window.settings); window.applyReaderTheme(); closeSettings(); window.toast('Settings saved', 'success');
    } catch (err) { console.error(err); window.toast('Could not save: ' + err.message, 'error'); }
}

window.$('btn-clear-progress').addEventListener('click', async () => {
    if (!confirm('Clear ALL libraries, progress, and settings? This cannot be undone.')) return;
    try {
        await window.idbDel(window.STORES.handles, window.STORES.handles);
        indexedDB.deleteDatabase(window.DB_NAME);
        localStorage.clear();
        location.reload();
    } catch(e) { window.toast('Error wiping data', 'error'); }
});

if ('speechSynthesis' in window) {
    speechSynthesis.onvoiceschanged = () => {};
    try { speechSynthesis.getVoices(); } catch(e) {}
}

document.addEventListener('visibilitychange', () => {
    if (window.TTS.active && !window.TTS.paused && document.hidden) window.startBackgroundKeepAlive();
    else if (!document.hidden && window.TTS.active && !window.TTS.paused) window.requestWakeLock();
});

window.addEventListener('load', async () => {
    try {
        await window.openDB();
        await window.loadLibraryFromDB();
        await window.renderResumeCard();

        setTimeout(() => {
            if (window.getTTSActiveFlag() && window.shadow.textContent.trim()) window.TTS.start();
        }, 800);
        
        window.setLoopButtonState(false);
        if (window.settings.mediaSessionEnabled && 'mediaSession' in navigator) {
            try { navigator.mediaSession.metadata = new MediaMetadata({ title: 'NovelArrow Reader Pro', artist: 'Ready to read', album: 'Local HTML Reader', artwork: [{ src: window.makeAlbumArt(), sizes: '512x512', type: 'image/png' }] }); } catch(e) {}
        }
    } catch(err) {
        console.error('Init error', err); window.toast('Startup issue: ' + err.message, 'error');
    }
});

window.addEventListener('error', (e) => { console.error('[Global error]', e.error || e.message); });
window.addEventListener('unhandledrejection', (e) => { console.error('[Unhandled rejection]', e.reason); });