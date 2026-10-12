window.Piper = {
    loading: false,
    loaded: false,
    module: null,
    activeVoice: null,
    currentUrl: null,
    cancelRequested: false,
    progressFiles: {},

    _resetProgressUI() {
        window.$('piper-progress-list').innerHTML = '';
        this.progressFiles = {};
        window.$('piper-title').textContent = 'Preparing Piper TTS…';
        window.$('piper-subtitle').innerHTML = 'First time only — the neural voice model is being downloaded and cached persistently on your device (OPFS). Subsequent launches are instant and fully offline.';
    },

    _showOverlay() {
        window.$('piper-overlay').classList.add('visible');
    },

    _hideOverlay() {
        window.$('piper-overlay').classList.remove('visible');
    },

    _updateProgress(data) {
        const url = data.url || data.file || 'model';
        const key = url.split('/').pop() || url;
        let rec = this.progressFiles[key];
        if (!rec) {
            const row = document.createElement('div');
            row.className = 'piper-progress-item';
            row.innerHTML = `<div class="file-name"><span>${window.escapeHtml(key)}</span><span class="pct">0%</span></div><div class="bar"><div></div></div>`;
            window.$('piper-progress-list').appendChild(row);
            rec = { el: row, pctEl: row.querySelector('.pct'), barEl: row.querySelector('.bar > div'), done: false };
            this.progressFiles[key] = rec;
        }
        const total = data.total || 0;
        const loaded = data.loaded || 0;
        if (total > 0) {
            const pct = Math.max(0, Math.min(100, (loaded / total) * 100));
            rec.pctEl.textContent = pct.toFixed(1) + '%';
            rec.barEl.style.width = pct + '%';
            if (loaded && total) {
                const mb = (loaded / 1048576).toFixed(1);
                const totalMb = (total / 1048576).toFixed(1);
                rec.pctEl.textContent = `${mb}/${totalMb} MB`;
            }
            if (loaded >= total && total > 0) {
                rec.pctEl.textContent = '✓ Done';
                rec.el.classList.add('done');
                rec.done = true;
            }
        } else if (data.status === 'done' || data.status === 'ready') {
            rec.pctEl.textContent = '✓ Done';
            rec.barEl.style.width = '100%';
            rec.el.classList.add('done');
            rec.done = true;
        }
    },

    async _loadModule() {
        if (this.module) return this.module;
        try {
            this.module = await import('https://cdn.jsdelivr.net/npm/@mintplex-labs/piper-tts-web@1.0.4/dist/index.js');
        } catch (e) {
            this.module = await import('https://unpkg.com/@mintplex-labs/piper-tts-web@1.0.4/dist/index.js');
        }
        return this.module;
    },

    async ensureVoice(voiceId) {
        voiceId = voiceId || window.settings.piperVoice || window.DEFAULT_PIPER_VOICE;
        if (this.loaded && this.activeVoice === voiceId) return true;
        if (this.loading) {
            while (this.loading) await new Promise(r => setTimeout(r, 100));
            if (this.loaded && this.activeVoice === voiceId) return true;
        }
        this.loading = true;
        this.cancelRequested = false;
        this._resetProgressUI();
        this._showOverlay();
        try {
            window.$('piper-title').textContent = 'Loading Piper library…';
            const mod = await this._loadModule();
            if (this.cancelRequested) throw new Error('Cancelled by user');
            let stored = [];
            try { stored = (await mod.stored()) || []; } catch(e) {}
            const alreadyStored = stored.includes(voiceId);
            window.$('piper-title').textContent = alreadyStored ? 'Loading cached voice…' : 'Downloading voice model…';
            if (alreadyStored) window.$('piper-subtitle').innerHTML = 'This voice is already cached on your device — loading from local storage.';
            if (!alreadyStored) {
                await mod.download(voiceId, (p) => { try { this._updateProgress(p || {}); } catch(e) {} });
            }
            if (this.cancelRequested) throw new Error('Cancelled by user');
            this.activeVoice = voiceId;
            this.loaded = true;
            window.$('piper-title').textContent = 'Ready ✓';
            window.$('piper-subtitle').textContent = 'Voice loaded successfully.';
            setTimeout(() => this._hideOverlay(), 500);
            return true;
        } catch (err) {
            console.error('[Piper] load failed', err);
            window.$('piper-title').textContent = 'Load failed';
            window.$('piper-subtitle').textContent = (err.message || String(err)) + '. Falling back to device voice.';
            window.toast('Piper failed: ' + (err.message || err), 'error', 5000);
            setTimeout(() => this._hideOverlay(), 1800);
            return false;
        } finally {
            this.loading = false;
        }
    },

    cancelLoad() {
        this.cancelRequested = true;
        this._hideOverlay();
        this.loading = false;
    },

    async synthesize(text, voiceId) {
        if (!this.loaded || this.activeVoice !== voiceId) {
            const ok = await this.ensureVoice(voiceId);
            if (!ok) throw new Error('Piper not loaded');
        }
        const mod = this.module;
        const wavBlob = await mod.predict({ text: text, voiceId: voiceId || this.activeVoice });
        if (this.currentUrl) { try { URL.revokeObjectURL(this.currentUrl); } catch(e) {} }
        this.currentUrl = URL.createObjectURL(wavBlob);
        return this.currentUrl;
    },

    async previewVoice(voiceId) {
        const sample = "Hi there! This is a preview of my voice. I hope you enjoy the way I sound.";
        const url = await this.synthesize(sample, voiceId || window.settings.piperVoice);
        return new Promise((resolve, reject) => {
            const a = new Audio(url);
            a.playsInline = true;
            a.onended = () => { resolve(); };
            a.onerror = (e) => reject(e);
            a.play().catch(reject);
        });
    }
};