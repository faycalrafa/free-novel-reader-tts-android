window.drawerTab = 'library';

window.openDrawer = function() {
    window.$('drawer').classList.add('visible');
    window.$('drawer-backdrop').classList.add('visible');
    window.renderDrawer();
};

window.closeDrawer = function() {
    window.$('drawer').classList.remove('visible');
    window.$('drawer-backdrop').classList.remove('visible');
};

window.renderDrawer = function() {
    if (window.drawerTab === 'library') window.renderLibrarySection();
    else window.renderPlaylistSection();
};

window.renderLibrarySection = function() {
    const drawerBody = window.$('drawer-body');
    if (window.state.library.length === 0) {
        drawerBody.innerHTML = `<div class="pl-empty">No playlists yet. Set a root folder — each subfolder becomes its own playlist.</div>`;
        return;
    }

    // Render all subfolders directly as a flat list, removing the root folder grouping.
    let html = '<div class="library-sect">';

    html += window.state.library.map(entry => {
        const cur = entry.id === window.state.currentLibId ? 'current' : '';
        const iconClass = entry.kind === 'folder' ? 'fa-folder' : 'fa-file-lines';
        const subParts = [];
        subParts.push(`${entry.count} chapter${entry.count !== 1 ? 's' : ''}`);
        
        if (entry.progress && entry.progress.total) {
            const done = (entry.progress.completed || []).length;
            subParts.push(`${entry.progress.index + 1}/${entry.progress.total}`);
            if (done > 0) subParts.push(`${done} done`);
        }
        
        return `
        <div class="lib-item ${cur}" data-id="${entry.id}">
            <div class="lib-icon"><i class="fa-solid ${iconClass}"></i></div>
            <div class="lib-info">
                <div class="lib-name">${window.escapeHtml(entry.name)}</div>
                <div class="lib-sub">${subParts.join(' • ')}</div>
            </div>
            <div class="lib-actions">
                <button class="lib-btn danger" data-act="delete" title="Remove"><i class="fa-solid fa-trash"></i></button>
            </div>
        </div>`;
    }).join('');
    
    html += `</div>`;

    drawerBody.innerHTML = html;

    drawerBody.querySelectorAll('.lib-item').forEach(el => {
        const id = el.dataset.id;
        el.addEventListener('click', e => {
            const act = e.target.closest('[data-act]');
            if (act) {
                e.stopPropagation();
                if (act.dataset.act === 'delete') window.removeLibraryEntry(id);
                return;
            }
            window.openLibraryEntry(id);
        });
    });
};

window.renderPlaylistSection = function() {
    const drawerBody = window.$('drawer-body');
    if (!window.state.currentPlaylist) {
        drawerBody.innerHTML = `<div class="pl-empty">Select a playlist from the Library tab.</div>`;
        return;
    }
    const pl = window.state.currentPlaylist;
    const items = pl.entries.map((e, i) => {
        const cur = i === window.state.currentEntryIndex ? 'current' : '';
        const finished = (pl.progress?.completed || []).includes(i) ? 'finished' : '';
        // Per-chapter progress indicator: if this chapter has a stored ttsIndex/scrollTop, mark it started
        const chProg = (pl.progress?.chapterProgress || {})[i];
        let progText = '';
        if (finished) progText = ' · done';
        else if (chProg && (chProg.ttsIndex > 0 || chProg.scrollTop > 0)) progText = ' · in progress';
        return `<div class="pl-item ${cur} ${finished}" data-i="${i}">
            <div class="idx">${i + 1}</div>
            <div class="name">
                <div class="n1" title="${window.escapeAttr(e.name)}">${window.escapeHtml(e.name)}</div>
                <div class="n2">${(e.size||0).toLocaleString()} bytes${progText}</div>
            </div>
        </div>`;
    }).join('');
    drawerBody.innerHTML = `<div style="padding:6px 4px 10px;font-size:11px;color:var(--muted);word-break:break-word">${window.escapeHtml(pl.name)} — ${pl.entries.length} chapter${pl.entries.length!==1?'s':''}</div>${items||'<div class="pl-empty">No chapters.</div>'}`;

    drawerBody.querySelectorAll('.pl-item').forEach(el => {
        el.addEventListener('click', () => {
            const i = parseInt(el.dataset.i, 10);
            window.loadEntry(i, true);
            window.closeDrawer();
        });
    });
    const curEl = drawerBody.querySelector('.pl-item.current');
    if (curEl && window.$('drawer').classList.contains('visible')) {
        setTimeout(() => curEl.scrollIntoView({ block: 'center' }), 50);
    }
};

// -------------------------------------------------------------
// Add root folder — every subfolder becomes a separate playlist
// -------------------------------------------------------------
window.addFolder = async function() {
    try {
        if (window.supportsFSAPI) {
            const dir = await window.showDirectoryPicker({ id: 'novelarrow-folder', mode: 'read' });
            await window.ingestRootDirectory(dir);
        } else {
            const inp = document.createElement('input');
            inp.type = 'file'; inp.webkitdirectory = true; inp.multiple = true;
            inp.style.display = 'none';
            document.body.appendChild(inp);
            inp.addEventListener('change', async () => {
                const files = Array.from(inp.files || []).filter(f => window.isTextFile(f.name, f.type));
                if (!files.length) { window.toast('No supported files found in folder', 'warn'); inp.remove(); return; }
                const rootName = (files[0].webkitRelativePath || files[0].name).split('/')[0] || 'Root';
                await window.ingestRootFilesGroupedBySubfolder(rootName, files);
                inp.remove();
            });
            inp.click();
        }
    } catch (err) {
        if (err.name !== 'AbortError') { console.error(err); window.toast('Could not open folder: ' + err.message, 'error'); }
    }
};

// Scan a root directory: each immediate subdirectory becomes one playlist.
// Loose files at the root level (if any) become their own "Root" playlist.
window.ingestRootDirectory = async function(rootHandle) {
    window.showLoading('Scanning root folder…');
    try {
        const subdirs = [];   // { name, handle }
        const rootFiles = []; // { name, handle }
        for await (const [name, h] of rootHandle.entries()) {
            if (h.kind === 'directory') subdirs.push({ name, handle: h });
            else if (h.kind === 'file' && window.isTextFile(name)) rootFiles.push({ name, handle: h });
        }

        if (subdirs.length === 0 && rootFiles.length === 0) {
            window.toast('No subfolders or text files found', 'warn');
            window.hideLoading();
            return;
        }

        const rootId = window.uid();
        const rootName = rootHandle.name || 'Root';

        // Store the root handle so we can re-sync later
        await window.idbPut(window.STORES.handles, { id: rootId, kind: 'directory', handle: rootHandle, isRoot: true });

        let addedCount = 0;

        // Build a playlist for each subdirectory
        for (const sd of subdirs) {
            const entries = [];
            await collectFilesRecursively(sd.handle, '', entries);
            if (!entries.length) continue;
            entries.sort((a, b) => window.naturalCompare(a.name, b.name));

            const libId = window.uid();
            const playlistEntries = entries.map(e => ({
                id: window.uid(), name: e.name, type: 'file', size: 0, handleRef: e.handleRef
            }));
            const progress = { index: 0, scrollTop: 0, total: entries.length, completed: [], chapterProgress: {} };
            const libEntry = {
                id: libId, name: sd.name, kind: 'folder',
                createdAt: Date.now(), count: entries.length, hasHandle: true,
                rootId, rootName,
                progress
            };
            await window.idbPut(window.STORES.handles, { id: libId, kind: 'directory', handle: sd.handle, rootId });
            await window.idbPut(window.STORES.playlists, {
                id: libId, name: sd.name, kind: 'folder',
                rootId, rootName,
                entries: playlistEntries, progress
            });
            window.state.library.push(libEntry);
            addedCount++;
        }

        // Loose root files → one playlist named after the root folder
        if (rootFiles.length > 0) {
            rootFiles.sort((a, b) => window.naturalCompare(a.name, b.name));
            const libId = window.uid();
            const playlistEntries = rootFiles.map(e => ({
                id: window.uid(), name: e.name, type: 'file', size: 0, handleRef: e.handle
            }));
            const progress = { index: 0, scrollTop: 0, total: rootFiles.length, completed: [], chapterProgress: {} };
            const libEntry = {
                id: libId, name: rootName + ' (root files)', kind: 'folder',
                createdAt: Date.now(), count: rootFiles.length, hasHandle: true,
                rootId, rootName,
                progress
            };
            await window.idbPut(window.STORES.handles, { id: libId, kind: 'directory', handle: rootHandle, rootId, isRootFiles: true });
            await window.idbPut(window.STORES.playlists, {
                id: libId, name: libEntry.name, kind: 'folder',
                rootId, rootName,
                entries: playlistEntries, progress
            });
            window.state.library.push(libEntry);
            addedCount++;
        }

        await window.saveLibraryIndex();
        // Store the root registry so we can refresh
        await saveRootRegistry(rootId, rootName);

        window.toast(`Added ${addedCount} playlist${addedCount!==1?'s':''} from "${rootName}"`, 'success');
        window.hideLoading();
        window.renderDrawer();
    } catch (err) {
        console.error(err); window.toast('Failed to scan root folder: ' + err.message, 'error'); window.hideLoading();
    }
};

// Recursively collect text files within a subdirectory (preserve relative path in name)
async function collectFilesRecursively(dirHandle, prefix, out) {
    for await (const [name, h] of dirHandle.entries()) {
        if (h.kind === 'file' && window.isTextFile(name)) {
            out.push({ name: prefix ? prefix + '/' + name : name, handleRef: h });
        } else if (h.kind === 'directory') {
            await collectFilesRecursively(h, prefix ? prefix + '/' + name : name, out);
        }
    }
}

// Fallback (no FS API): input[type=file webkitdirectory] path — group files by their first subfolder
window.ingestRootFilesGroupedBySubfolder = async function(rootName, files) {
    window.showLoading('Loading files…');
    try {
        const rootId = window.uid();
        const groups = new Map(); // subfolder name -> files[]
        const rootFiles = [];
        for (const f of files) {
            const rel = f.webkitRelativePath || f.name;
            const parts = rel.split('/');
            // parts[0] is root name; if length >= 3, parts[1] is subfolder
            if (parts.length >= 3) {
                const sub = parts[1];
                if (!groups.has(sub)) groups.set(sub, []);
                groups.get(sub).push(f);
            } else {
                rootFiles.push(f);
            }
        }

        let addedCount = 0;

        for (const [subName, subFiles] of groups.entries()) {
            subFiles.sort((a, b) => window.naturalCompare(a.webkitRelativePath || a.name, b.webkitRelativePath || b.name));
            const libId = window.uid();
            const playlistEntries = subFiles.map(f => ({
                id: window.uid(), name: (f.webkitRelativePath || f.name).split('/').slice(2).join('/') || f.name,
                type: 'file', size: f.size, fileRef: f
            }));
            const progress = { index: 0, scrollTop: 0, total: subFiles.length, completed: [], chapterProgress: {} };
            const libEntry = {
                id: libId, name: subName, kind: 'folder',
                createdAt: Date.now(), count: subFiles.length, hasHandle: false,
                rootId, rootName,
                progress
            };
            await window.idbPut(window.STORES.playlists, {
                id: libId, name: subName, kind: 'folder',
                rootId, rootName,
                entries: playlistEntries.map(e => ({ ...e, fileRef: null })),
                progress
            });
            window._sessionFileRefs.set(libId, subFiles);
            window.state.library.push(libEntry);
            addedCount++;
        }

        if (rootFiles.length > 0) {
            rootFiles.sort((a, b) => window.naturalCompare(a.name, b.name));
            const libId = window.uid();
            const playlistEntries = rootFiles.map(f => ({
                id: window.uid(), name: f.name, type: 'file', size: f.size, fileRef: f
            }));
            const progress = { index: 0, scrollTop: 0, total: rootFiles.length, completed: [], chapterProgress: {} };
            const libEntry = {
                id: libId, name: rootName + ' (root files)', kind: 'folder',
                createdAt: Date.now(), count: rootFiles.length, hasHandle: false,
                rootId, rootName,
                progress
            };
            await window.idbPut(window.STORES.playlists, {
                id: libId, name: libEntry.name, kind: 'folder',
                rootId, rootName,
                entries: playlistEntries.map(e => ({ ...e, fileRef: null })),
                progress
            });
            window._sessionFileRefs.set(libId, rootFiles);
            window.state.library.push(libEntry);
            addedCount++;
        }

        await window.saveLibraryIndex();
        await saveRootRegistry(rootId, rootName);
        window.toast(`Loaded ${addedCount} playlist${addedCount!==1?'s':''} from "${rootName}"`, 'success');
        window.hideLoading();
        window.renderDrawer();
    } catch (err) {
        console.error(err); window.toast('Load failed: ' + err.message, 'error'); window.hideLoading();
    }
};

async function saveRootRegistry(rootId, rootName) {
    let regs = [];
    try { const raw = localStorage.getItem('novelArrowReader::roots'); if (raw) regs = JSON.parse(raw); } catch (e) {}
    if (!regs.find(r => r.id === rootId)) regs.push({ id: rootId, name: rootName, at: Date.now() });
    localStorage.setItem('novelArrowReader::roots', JSON.stringify(regs));
}

function loadRootRegistry() {
    try { const raw = localStorage.getItem('novelArrowReader::roots'); if (raw) return JSON.parse(raw); } catch (e) {}
    return [];
}

window.saveLibraryIndex = async function() {
    const index = window.state.library.map(e => ({
        id: e.id, name: e.name, kind: e.kind, createdAt: e.createdAt, count: e.count, hasHandle: e.hasHandle,
        rootId: e.rootId || null, rootName: e.rootName || null
    }));
    localStorage.setItem('novelArrowReader::libraryIndex', JSON.stringify(index));
};

window.loadLibraryFromDB = async function() {
    let index = [];
    try { const raw = localStorage.getItem('novelArrowReader::libraryIndex'); if (raw) index = JSON.parse(raw); } catch (e) {}
    const playlists = await window.idbAll(window.STORES.playlists);
    const plMap = new Map(playlists.map(p => [p.id, p]));
    window.state.library = index.map(e => {
        const pl = plMap.get(e.id);
        return {
            ...e,
            progress: pl?.progress || { index: 0, scrollTop: 0, total: e.count, completed: [], chapterProgress: {} }
        };
    });
};

window.removeLibraryEntry = async function(id) {
    if (!confirm('Remove this playlist? Progress will be lost.')) return;
    window.state.library = window.state.library.filter(e => e.id !== id);
    await window.idbDel(window.STORES.playlists, id);
    await window.idbDel(window.STORES.handles, id);
    window._sessionFileRefs.delete(id);
    if (window.state.currentLibId === id) {
        window.state.currentLibId = null; window.state.currentPlaylist = null; window.state.currentEntryIndex = -1;
        window.shadow.innerHTML = ''; window.$('empty-state').classList.remove('hidden');
        window.$('page-title').textContent = 'NovelArrow Reader Pro'; window.$('page-sub').textContent = 'Ready to read';
    }
    await window.saveLibraryIndex();
    window.renderDrawer();
    window.toast('Removed', 'success');
};

window.verifyPermission = async function(handle, mode = 'read') {
    try { if ((await handle.queryPermission({ mode })) === 'granted') return true; if ((await handle.requestPermission({ mode })) === 'granted') return true; return false; } catch (e) { return false; }
};

// -------------------------------------------------------------
// Refresh: re-sync ALL roots (or a specific root) — remaps subfolders → playlists,
// preserving progress on chapters that still exist.
// -------------------------------------------------------------
window.refreshAllRoots = async function() {
    const roots = loadRootRegistry();
    if (!roots.length) { window.toast('No root folder set. Use "Set root folder" first.', 'warn'); return; }
    for (const r of roots) {
        await window.refreshRoot(r.id);
    }
};

window.refreshRoot = async function(rootId) {
    const rec = await window.idbGet(window.STORES.handles, rootId);
    if (!rec || !rec.handle) { window.toast('Root folder handle not available (session-only). Re-select the root folder.', 'warn'); return; }
    const rootHandle = rec.handle;
    if (!(await window.verifyPermission(rootHandle, 'read'))) { window.toast('Permission denied for root folder', 'error'); return; }

    window.showLoading('Refreshing root folder…');
    try {
        const rootName = rootHandle.name || rec.name || 'Root';

        // Discover subfolders + root-level files under the current on-disk state
        const subdirs = new Map(); // name -> handle
        const rootFiles = [];
        for await (const [name, h] of rootHandle.entries()) {
            if (h.kind === 'directory') subdirs.set(name, h);
            else if (h.kind === 'file' && window.isTextFile(name)) rootFiles.push({ name, handle: h });
        }

        // Existing playlists that belong to this root
        const existing = window.state.library.filter(l => l.rootId === rootId);
        const byName = new Map(existing.map(l => [l.name, l]));

        // For each discovered subdirectory, refresh (or create) a playlist
        for (const [subName, subHandle] of subdirs.entries()) {
            const entries = [];
            await collectFilesRecursively(subHandle, '', entries);
            if (!entries.length) continue;
            entries.sort((a, b) => window.naturalCompare(a.name, b.name));

            const existingLib = byName.get(subName);
            if (existingLib) {
                await mergePlaylist(existingLib.id, subHandle, entries, rootId, rootName, subName);
                byName.delete(subName);
            } else {
                const libId = window.uid();
                const playlistEntries = entries.map(e => ({
                    id: window.uid(), name: e.name, type: 'file', size: 0, handleRef: e.handleRef
                }));
                const progress = { index: 0, scrollTop: 0, total: entries.length, completed: [], chapterProgress: {} };
                const libEntry = {
                    id: libId, name: subName, kind: 'folder',
                    createdAt: Date.now(), count: entries.length, hasHandle: true,
                    rootId, rootName, progress
                };
                await window.idbPut(window.STORES.handles, { id: libId, kind: 'directory', handle: subHandle, rootId });
                await window.idbPut(window.STORES.playlists, {
                    id: libId, name: subName, kind: 'folder', rootId, rootName,
                    entries: playlistEntries, progress
                });
                window.state.library.push(libEntry);
            }
        }

        // Handle the root-files playlist
        const rootFilesName = rootName + ' (root files)';
        if (rootFiles.length > 0) {
            rootFiles.sort((a, b) => window.naturalCompare(a.name, b.name));
            const entries = rootFiles.map(f => ({ name: f.name, handleRef: f.handle }));
            const existingLib = byName.get(rootFilesName);
            if (existingLib) {
                await mergePlaylist(existingLib.id, rootHandle, entries, rootId, rootName, rootFilesName);
                byName.delete(rootFilesName);
            } else {
                const libId = window.uid();
                const playlistEntries = entries.map(e => ({
                    id: window.uid(), name: e.name, type: 'file', size: 0, handleRef: e.handleRef
                }));
                const progress = { index: 0, scrollTop: 0, total: entries.length, completed: [], chapterProgress: {} };
                const libEntry = {
                    id: libId, name: rootFilesName, kind: 'folder',
                    createdAt: Date.now(), count: entries.length, hasHandle: true,
                    rootId, rootName, progress
                };
                await window.idbPut(window.STORES.handles, { id: libId, kind: 'directory', handle: rootHandle, rootId, isRootFiles: true });
                await window.idbPut(window.STORES.playlists, {
                    id: libId, name: rootFilesName, kind: 'folder', rootId, rootName,
                    entries: playlistEntries, progress
                });
                window.state.library.push(libEntry);
            }
        }

        // Remove playlists that no longer exist on disk
        for (const stale of byName.values()) {
            window.state.library = window.state.library.filter(l => l.id !== stale.id);
            await window.idbDel(window.STORES.playlists, stale.id);
            await window.idbDel(window.STORES.handles, stale.id);
            if (window.state.currentLibId === stale.id) {
                window.state.currentLibId = null; window.state.currentPlaylist = null; window.state.currentEntryIndex = -1;
                window.shadow.innerHTML = ''; window.$('empty-state').classList.remove('hidden');
            }
        }

        await window.saveLibraryIndex();
        window.hideLoading();
        window.toast(`Refreshed "${rootName}" successfully`, 'success');
        window.renderDrawer();
    } catch (err) {
        console.error(err); window.toast('Refresh failed: ' + err.message, 'error'); window.hideLoading();
    }
};

async function mergePlaylist(libId, folderHandle, newEntries, rootId, rootName, name) {
    const oldPl = await window.idbGet(window.STORES.playlists, libId) || {
        entries: [], progress: { index: 0, scrollTop: 0, total: 0, completed: [], chapterProgress: {} }
    };
    const merged = newEntries.map(e => {
        const old = oldPl.entries.find(o => o.name === e.name);
        return { id: old?.id || window.uid(), name: e.name, type: 'file', size: 0, handleRef: e.handleRef };
    });

    const oldProgress = oldPl.progress || { index: 0, scrollTop: 0, completed: [], chapterProgress: {} };
    const currentEntryName = oldPl.entries[oldProgress.index]?.name;
    let newIndex = 0;
    if (currentEntryName) {
        const nIdx = merged.findIndex(m => m.name === currentEntryName);
        if (nIdx >= 0) newIndex = nIdx;
    }
    const completed = (oldProgress.completed || []).map(oi => {
        const oldName = oldPl.entries[oi]?.name;
        return oldName ? merged.findIndex(m => m.name === oldName) : -1;
    }).filter(i => i >= 0);

    // Re-map per-chapter progress by name
    const newChapterProgress = {};
    const oldChapProg = oldProgress.chapterProgress || {};
    for (const [oldIdxStr, prog] of Object.entries(oldChapProg)) {
        const oldIdx = parseInt(oldIdxStr, 10);
        const oldName = oldPl.entries[oldIdx]?.name;
        if (!oldName) continue;
        const newIdx = merged.findIndex(m => m.name === oldName);
        if (newIdx >= 0) newChapterProgress[newIdx] = prog;
    }

    const newProgress = {
        index: newIndex,
        scrollTop: oldProgress.scrollTop || 0,
        total: merged.length,
        completed,
        ttsIndex: oldProgress.ttsIndex,
        chapterProgress: newChapterProgress
    };

    await window.idbPut(window.STORES.playlists, {
        id: libId, name, kind: 'folder', rootId, rootName,
        entries: merged.map(m => ({ ...m })), progress: newProgress
    });

    const libEntry = window.state.library.find(l => l.id === libId);
    if (libEntry) { libEntry.count = merged.length; libEntry.progress = newProgress; libEntry.rootId = rootId; libEntry.rootName = rootName; libEntry.name = name; libEntry.hasHandle = true; }

    if (window.state.currentLibId === libId) {
        window.state.currentPlaylist = {
            id: libId, name, kind: 'folder', rootId, rootName,
            entries: merged, progress: newProgress
        };
    }
}

window.openLibraryEntry = async function(id, providedEntries) {
    const lib = window.state.library.find(l => l.id === id);
    if (!lib) { window.toast('Entry not found', 'error'); return; }
    window.showLoading('Loading playlist…');
    try {
        let pl = await window.idbGet(window.STORES.playlists, id);
        if (!pl) { window.toast('Playlist data missing', 'error'); window.hideLoading(); return; }

        // Ensure the progress shape includes chapterProgress
        pl.progress = pl.progress || { index: 0, scrollTop: 0, total: pl.entries.length, completed: [], chapterProgress: {} };
        if (!pl.progress.chapterProgress) pl.progress.chapterProgress = {};

        if (lib.kind === 'folder' && lib.hasHandle) {
            const rec = await window.idbGet(window.STORES.handles, id);
            if (rec && rec.handle && await window.verifyPermission(rec.handle, 'read')) {
                const map = new Map();
                try {
                    // Recursively map files inside this subfolder handle
                    await mapFilesRecursively(rec.handle, '', map);
                } catch (e) {}
                for (const en of pl.entries) {
                    const h = map.get(en.name);
                    if (h) en.handleRef = h;
                }
            } else {
                window.toast('Need permission to read folder', 'warn');
            }
        } else if (lib.kind === 'folder' && !lib.hasHandle) {
            const refs = window._sessionFileRefs.get(id);
            if (refs) {
                for (let i = 0; i < pl.entries.length; i++) {
                    if (refs[i]) pl.entries[i].fileRef = refs[i];
                }
            } else if (providedEntries) {
                pl.entries = providedEntries;
            } else {
                window.toast('Session ended: use Refresh or re-add the root folder.', 'warn');
            }
        }

        window.state.currentLibId = id;
        window.state.currentPlaylist = pl;
        window.$('empty-state').classList.add('hidden');
        const idx = Math.min(pl.progress?.index || 0, pl.entries.length - 1);
        await window.loadEntry(idx, false);

        // Restore per-chapter scroll position from chapterProgress
        setTimeout(() => {
            const chProg = (pl.progress.chapterProgress || {})[idx];
            const scrollTop = chProg?.scrollTop ?? pl.progress?.scrollTop ?? 0;
            window.$('reader-content').scrollTop = scrollTop;
        }, 50);
        localStorage.setItem('novelArrowReader::lastLibId', id);
        window.hideLoading();

        const plTabBtn = document.querySelector('.drawer-tabs button[data-tab="playlist"]');
        if (plTabBtn && pl.entries.length > 1) plTabBtn.click();
    } catch (err) {
        console.error(err); window.toast('Open failed: ' + err.message, 'error'); window.hideLoading();
    }
};

async function mapFilesRecursively(dirHandle, prefix, out) {
    for await (const [name, h] of dirHandle.entries()) {
        if (h.kind === 'file') {
            out.set(prefix ? prefix + '/' + name : name, h);
        } else if (h.kind === 'directory') {
            await mapFilesRecursively(h, prefix ? prefix + '/' + name : name, out);
        }
    }
}

window.getEntryContent = async function(entry) {
    let text = '';
    if (entry.handleRef) {
        const f = await entry.handleRef.getFile();
        text = await f.text();
    } else if (entry.fileRef) {
        text = await entry.fileRef.text();
    } else {
        throw new Error('File handle unavailable. Try refreshing this playlist.');
    }
    return text;
};

window.markCurrentDone = function() {
    const pl = window.state.currentPlaylist;
    if (!pl) return;
    pl.progress.completed = pl.progress.completed || [];
    if (!pl.progress.completed.includes(window.state.currentEntryIndex)) {
        pl.progress.completed.push(window.state.currentEntryIndex);
        window.savePlaylistProgress();
    }
};

window.goToNextItem = function() {
    if (!window.state.currentPlaylist) return;
    window.markCurrentDone();
    const pl = window.state.currentPlaylist;
    if (window.state.currentEntryIndex + 1 < pl.entries.length) {
        window.loadEntry(window.state.currentEntryIndex + 1);
    } else {
        window.toast('End of playlist', 'info');
        if (window.app.loopActive) window.setLoopButtonState(false);
    }
};

window.loadEntry = async function(index, updateProgress = true) {
    const pl = window.state.currentPlaylist;
    if (!pl || index < 0 || index >= pl.entries.length) return;

    // Save current chapter's progress into chapterProgress before switching
    if (window.state.currentEntryIndex >= 0 && window.state.currentEntryIndex !== index) {
        pl.progress.chapterProgress = pl.progress.chapterProgress || {};
        pl.progress.chapterProgress[window.state.currentEntryIndex] = {
            scrollTop: window.$('reader-content').scrollTop || 0,
            ttsIndex: pl.progress.ttsIndex || 0
        };
        window.savePlaylistProgress();
    }

    window.state.currentEntryIndex = index;
    const entry = pl.entries[index];
    window.$('page-title').textContent = entry.name.replace(/\.[^.]+$/, '') || pl.name;
    window.$('page-sub').textContent = `${pl.name} • ${index + 1}/${pl.entries.length}`;

    window.showLoading('Opening ' + entry.name + '…');
    try {
        const html = await window.getEntryContent(entry);
        window.renderHTMLInto(html, entry.name);

        if (updateProgress) {
            pl.progress.index = index;
            const chProg = (pl.progress.chapterProgress || {})[index];
            pl.progress.scrollTop = chProg?.scrollTop ?? 0;
            pl.progress.ttsIndex = chProg?.ttsIndex ?? 0;
            await window.savePlaylistProgress();
        }

        // Restore chapter-level scroll position if we have one
        setTimeout(() => {
            const chProg = (pl.progress.chapterProgress || {})[index];
            if (chProg?.scrollTop) window.$('reader-content').scrollTop = chProg.scrollTop;
        }, 50);

        window.updateFabNext();
        window.hideLoading();
        window.renderDrawer();

        if (window.TTS.wasActiveBeforeAdvance) {
            window.TTS.wasActiveBeforeAdvance = false;
            setTimeout(() => window.TTS.start(), 400);
        }
    } catch (err) {
        window.hideLoading(); console.error(err); window.toast('Failed to open: ' + err.message, 'error');
    }
};

window.savePlaylistProgress = async function() {
    const pl = window.state.currentPlaylist;
    if (!pl) return;
    // Also persist current chapter's live scroll + tts index into chapterProgress
    if (window.state.currentEntryIndex >= 0) {
        pl.progress.chapterProgress = pl.progress.chapterProgress || {};
        pl.progress.chapterProgress[window.state.currentEntryIndex] = {
            scrollTop: window.$('reader-content')?.scrollTop || pl.progress.scrollTop || 0,
            ttsIndex: pl.progress.ttsIndex || 0
        };
    }
    await window.idbPut(window.STORES.playlists, pl);
    const lib = window.state.library.find(l => l.id === pl.id);
    if (lib) lib.progress = pl.progress;
};