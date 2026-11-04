package com.novelarrow.reader.data.userscript

internal object DefaultUserscriptTemplates {

    val DEFAULT_DEMO_SCRIPT = """
        // ==UserScript==
        // @name         Clean Novel Reader (Ad & Clutter Remover)
        // @version      1.0.0
        // @description  Removes floating ads, sticky banners, and declutters web novel pages for clean reading and TTS.
        // @match        *://*/*
        // @run-at       document-end
        // @grant        GM_notification
        // ==/UserScript==

        (function() {
            'use strict';
            const adSelectors = [
                '.ads', '.ad-container', '.ad_box', '#sidebar', 'aside',
                '.header-advert', '.footer-advert', 'iframe', '.floating-ad',
                '.social-share', '.share-buttons', '.sticky-banner',
                '.ad-wrapper', '#floating-banner', '.advertisement',
                '[id*="ad_"]', '[class*="advert"]'
            ];
            let removed = 0;
            adSelectors.forEach(function(sel) {
                document.querySelectorAll(sel).forEach(function(el) {
                    if (el.id === 'chapter-content' || el.classList.contains('chapter-content') || el.tagName === 'BODY') {
                        return;
                    }
                    el.remove();
                    removed++;
                });
            });
            if (removed > 0 && typeof GM_notification === 'function') {
                GM_notification('Novel page cleaned (' + removed + ' clutter items removed)');
            }
        })();
    """.trimIndent()

    val DEFAULT_MULTI_DOWNLOAD_SCRIPT = """
        // ==UserScript==
        // @name         Novel Multi-Chapter Downloader Pro
        // @namespace    http://tampermonkey.net/
        // @version      4.2.0
        // @description  Compact floating button on the right to auto-download novel chapters (full frozen HTML or clean minimal reader HTML) with auto-next chapter loop
        // @author       You
        // @match        *://*/*
        // @match        file://*/*
        // @grant        GM_setValue
        // @grant        GM_getValue
        // @grant        GM_addStyle
        // @grant        GM_registerToolbarButton
        // @grant        GM_notification
        // @noframes
        // @run-at       document-idle
        // ==/UserScript==

        (function () {
            'use strict';

            if (window.top !== window.self) return;
            if (window.__novelMultiDownloaderInjected) return;
            window.__novelMultiDownloaderInjected = true;

            // =============================================================
            // 0. CONSTANTS & DOMAIN-SCOPED SETTINGS
            // =============================================================
            const DOMAIN = location.hostname || 'local-file';
            const SETTINGS_KEY = 'novelMultiDownloader::settings::' + DOMAIN;

            // Auto-loop session state (survives SPA nav and page reloads)
            const COUNT_KEY = 'novel_dl_run_count';
            const ACTIVE_KEY = 'novel_dl_active';
            const LAST_URL_KEY = 'novel_dl_last_processed_url';
            const NOVEL_NAME_KEY = 'novel_dl_novel_name';
            const CHAP_NUM_KEY = 'novel_dl_chap_num';
            const FULL_FROZEN_KEY = 'novel_dl_full_frozen';
            const DELAY_MS_KEY = 'novel_dl_delay_ms';
            const RAN_HASHES_KEY = 'novel_dl_ran_hashes';
            const SAME_HASH_RETRIES_KEY = 'novel_dl_same_hash_retries';
            const SAVING_LOCK_KEY = 'novel_dl_is_saving';

            function getRanHashes() {
                try {
                    return JSON.parse(sessionStorage.getItem(RAN_HASHES_KEY) || '[]');
                } catch (e) {
                    return [];
                }
            }

            function saveRanHashes(arr) {
                try {
                    sessionStorage.setItem(RAN_HASHES_KEY, JSON.stringify(arr));
                } catch (e) {}
            }

            function getPageContentForHash() {
                try {
                    let container = null;
                    if (settings.contentSelector) {
                        try {
                            container = document.querySelector(settings.contentSelector);
                        } catch (e) {}
                    }
                    if (!container) {
                        const fallbacks = [
                            '#chapter-content', '.chapter-content', '#content', '.content-area',
                            '.reading-content', '.entry-content', '.text-content', '#article', 'article', 'main', '.chapter-body'
                        ];
                        for (const sel of fallbacks) {
                            try {
                                const el = document.querySelector(sel);
                                if (el && (el.innerText || el.textContent || '').trim().length > 80) {
                                    container = el;
                                    break;
                                }
                            } catch (e) {}
                        }
                    }
                    if (!container) container = document.body || document.documentElement;

                    const clone = container.cloneNode(true);
                    const toRemove = clone.querySelectorAll(
                        '#na-dl-root, .na-dl-modal-backdrop, #na-floating-next, #na-toast-container, ' +
                        'script, style, noscript, nav, header, footer, aside, .ads, .ad, [class*="ad-"], ins'
                    );
                    toRemove.forEach(el => el.remove());
                    return (clone.innerText || clone.textContent || '').replace(/\s+/g, ' ').trim();
                } catch (e) {
                    return (document.body ? (document.body.innerText || '') : '').replace(/\s+/g, ' ').trim();
                }
            }

            function md5(string) {
                function rotateLeft(lValue, iShiftBits) {
                    return (lValue << iShiftBits) | (lValue >>> (32 - iShiftBits));
                }
                function addUnsigned(lX, lY) {
                    var lX4, lY4, lX8, lY8, lResult;
                    lX8 = (lX & 0x80000000);
                    lY8 = (lY & 0x80000000);
                    lX4 = (lX & 0x40000000);
                    lY4 = (lY & 0x40000000);
                    lResult = (lX & 0x3FFFFFFF) + (lY & 0x3FFFFFFF);
                    if (lX4 & lY4) return (lResult ^ 0x80000000 ^ lX8 ^ lY8);
                    if (lX4 | lY4) {
                        if (lResult & 0x40000000) return (lResult ^ 0xC0000000 ^ lX8 ^ lY8);
                        else return (lResult ^ 0x40000000 ^ lX8 ^ lY8);
                    } else {
                        return (lResult ^ lX8 ^ lY8);
                    }
                }
                function F(x, y, z) { return (x & y) | ((~x) & z); }
                function G(x, y, z) { return (x & z) | (y & (~z)); }
                function H(x, y, z) { return (x ^ y ^ z); }
                function I(x, y, z) { return (y ^ (x | (~z))); }
                function FF(a, b, c, d, x, s, ac) {
                    a = addUnsigned(a, addUnsigned(addUnsigned(F(b, c, d), x), ac));
                    return addUnsigned(rotateLeft(a, s), b);
                }
                function GG(a, b, c, d, x, s, ac) {
                    a = addUnsigned(a, addUnsigned(addUnsigned(G(b, c, d), x), ac));
                    return addUnsigned(rotateLeft(a, s), b);
                }
                function HH(a, b, c, d, x, s, ac) {
                    a = addUnsigned(a, addUnsigned(addUnsigned(H(b, c, d), x), ac));
                    return addUnsigned(rotateLeft(a, s), b);
                }
                function II(a, b, c, d, x, s, ac) {
                    a = addUnsigned(a, addUnsigned(addUnsigned(I(b, c, d), x), ac));
                    return addUnsigned(rotateLeft(a, s), b);
                }
                function convertToWordArray(string) {
                    var lWordCount;
                    var lMessageLength = string.length;
                    var lNumberOfWords_temp1 = lMessageLength + 8;
                    var lNumberOfWords_temp2 = (lNumberOfWords_temp1 - (lNumberOfWords_temp1 % 64)) / 64;
                    var lNumberOfWords = (lNumberOfWords_temp2 + 1) * 16;
                    var lWordArray = Array(lNumberOfWords - 1);
                    var lBytePosition = 0;
                    var lByteCount = 0;
                    while (lByteCount < lMessageLength) {
                        lWordCount = (lByteCount - (lByteCount % 4)) / 4;
                        lBytePosition = (lByteCount % 4) * 8;
                        lWordArray[lWordCount] = (lWordArray[lWordCount] | (string.charCodeAt(lByteCount) << lBytePosition));
                        lByteCount++;
                    }
                    lWordCount = (lByteCount - (lByteCount % 4)) / 4;
                    lBytePosition = (lByteCount % 4) * 8;
                    lWordArray[lWordCount] = lWordArray[lWordCount] | (0x80 << lBytePosition);
                    lWordArray[lNumberOfWords - 2] = lMessageLength << 3;
                    lWordArray[lNumberOfWords - 1] = lMessageLength >>> 29;
                    return lWordArray;
                }
                function wordToHex(lValue) {
                    var WordToHexValue = "", WordToHexValue_temp = "", lByte, lCount;
                    for (lCount = 0; lCount <= 3; lCount++) {
                        lByte = (lValue >>> (lCount * 8)) & 255;
                        WordToHexValue_temp = "0" + lByte.toString(16);
                        WordToHexValue = WordToHexValue + WordToHexValue_temp.substr(WordToHexValue_temp.length - 2, 2);
                    }
                    return WordToHexValue;
                }
                function utf8Encode(string) {
                    string = string.replace(/\r\n/g, "\n");
                    var utftext = "";
                    for (var n = 0; n < string.length; n++) {
                        var c = string.charCodeAt(n);
                        if (c < 128) {
                            utftext += String.fromCharCode(c);
                        } else if ((c > 127) && (c < 2048)) {
                            utftext += String.fromCharCode((c >> 6) | 192);
                            utftext += String.fromCharCode((c & 63) | 128);
                        } else {
                            utftext += String.fromCharCode((c >> 12) | 224);
                            utftext += String.fromCharCode(((c >> 6) & 63) | 128);
                            utftext += String.fromCharCode((c & 63) | 128);
                        }
                    }
                    return utftext;
                }
                var x = Array();
                var k, AA, BB, CC, DD, a, b, c, d;
                var S11=7, S12=12, S13=17, S14=22;
                var S21=5, S22=9 , S23=14, S24=20;
                var S31=4, S32=11, S33=16, S34=23;
                var S41=6, S42=10, S43=15, S44=21;
                string = utf8Encode(string);
                x = convertToWordArray(string);
                a = 0x67452301; b = 0xEFCDAB89; c = 0x98BADCFE; d = 0x10325476;
                for (k = 0; k < x.length; k += 16) {
                    AA = a; BB = b; CC = c; DD = d;
                    a = FF(a, b, c, d, x[k+0],  S11, 0xD76AA478);
                    d = FF(d, a, b, c, x[k+1],  S12, 0xE8C7B756);
                    c = FF(c, d, a, b, x[k+2],  S13, 0x242070DB);
                    b = FF(b, c, d, a, x[k+3],  S14, 0xC1BDCEEE);
                    a = FF(a, b, c, d, x[k+4],  S11, 0xF57C0FAF);
                    d = FF(d, a, b, c, x[k+5],  S12, 0x4787C62A);
                    c = FF(c, d, a, b, x[k+6],  S13, 0xA8304613);
                    b = FF(b, c, d, a, x[k+7],  S14, 0xFD469501);
                    a = FF(a, b, c, d, x[k+8],  S11, 0x698098D8);
                    d = FF(d, a, b, c, x[k+9],  S12, 0x8B44F7AF);
                    c = FF(c, d, a, b, x[k+10], S13, 0xFFFF5BB1);
                    b = FF(b, c, d, a, x[k+11], S14, 0x895CD7BE);
                    a = FF(a, b, c, d, x[k+12], S11, 0x6B901122);
                    d = FF(d, a, b, c, x[k+13], S12, 0xFD987193);
                    c = FF(c, d, a, b, x[k+14], S13, 0xA679438E);
                    b = FF(b, c, d, a, x[k+15], S14, 0x49B40821);
                    a = GG(a, b, c, d, x[k+1],  S21, 0xF61E2562);
                    d = GG(d, a, b, c, x[k+6],  S22, 0xC040B340);
                    c = GG(c, d, a, b, x[k+11], S23, 0x265E5A51);
                    b = GG(b, c, d, a, x[k+0],  S24, 0xE9B6C7AA);
                    a = GG(a, b, c, d, x[k+5],  S21, 0xD62F105D);
                    d = GG(d, a, b, c, x[k+10], S22, 0x02441453);
                    c = GG(c, d, a, b, x[k+15], S23, 0xD8A1E681);
                    b = GG(b, c, d, a, x[k+4],  S24, 0xE7D3FBC8);
                    a = GG(a, b, c, d, x[k+9],  S21, 0x21E1CDE6);
                    d = GG(d, a, b, c, x[k+14], S22, 0xC33707D6);
                    c = GG(c, d, a, b, x[k+3],  S23, 0xF4D50D87);
                    b = GG(b, c, d, a, x[k+8],  S24, 0x455A14ED);
                    a = GG(a, b, c, d, x[k+13], S21, 0xA9E3E905);
                    d = GG(d, a, b, c, x[k+2],  S22, 0xFCEFA3F8);
                    c = GG(c, d, a, b, x[k+7],  S23, 0x676F02D9);
                    b = GG(b, c, d, a, x[k+12], S24, 0x8D2A4C8A);
                    a = HH(a, b, c, d, x[k+5],  S31, 0xFFFA3942);
                    d = HH(d, a, b, c, x[k+8],  S32, 0x8771F681);
                    c = HH(c, d, a, b, x[k+11], S33, 0x6D9D6122);
                    b = HH(b, c, d, a, x[k+14], S34, 0xFDE5380C);
                    a = HH(a, b, c, d, x[k+1],  S31, 0xA4BEEA44);
                    d = HH(d, a, b, c, x[k+4],  S32, 0x4BDECFA9);
                    c = HH(c, d, a, b, x[k+7],  S33, 0xF6BB4B60);
                    b = HH(b, c, d, a, x[k+10], S34, 0xBEBFBC70);
                    a = HH(a, b, c, d, x[k+13], S31, 0x289B7EC6);
                    d = HH(d, a, b, c, x[k+0],  S32, 0xEAA127FA);
                    c = HH(c, d, a, b, x[k+3],  S33, 0xD4EF3085);
                    b = HH(b, c, d, a, x[k+6],  S34, 0x04881D05);
                    a = HH(a, b, c, d, x[k+9],  S31, 0xD9D4D039);
                    d = HH(d, a, b, c, x[k+12], S32, 0xE6DB99E5);
                    c = HH(c, d, a, b, x[k+15], S33, 0x1FA27CF8);
                    b = HH(b, c, d, a, x[k+2],  S34, 0xC4AC5665);
                    a = II(a, b, c, d, x[k+0],  S41, 0xF4292244);
                    d = II(d, a, b, c, x[k+7],  S42, 0x432AFF97);
                    c = II(c, d, a, b, x[k+14], S43, 0xAB9423A7);
                    b = II(b, c, d, a, x[k+5],  S44, 0xFC93A039);
                    a = II(a, b, c, d, x[k+12], S41, 0x655B59C3);
                    d = II(d, a, b, c, x[k+3],  S42, 0x8F0CCC92);
                    c = II(c, d, a, b, x[k+10], S43, 0xFFEFF47D);
                    b = II(b, c, d, a, x[k+1],  S44, 0x85845DD1);
                    a = II(a, b, c, d, x[k+8],  S41, 0x6FA87E4F);
                    d = II(d, a, b, c, x[k+15], S42, 0xFE2CE6E0);
                    c = II(c, d, a, b, x[k+6],  S43, 0xA3014314);
                    b = II(b, c, d, a, x[k+13], S44, 0x4E0811A1);
                    a = II(a, b, c, d, x[k+4],  S41, 0xF7537E82);
                    d = II(d, a, b, c, x[k+11], S42, 0xBD3AF235);
                    c = II(c, d, a, b, x[k+2],  S43, 0x2AD7D2BB);
                    b = II(b, c, d, a, x[k+9],  S44, 0xEB86D391);
                    a = addUnsigned(a, AA);
                    b = addUnsigned(b, BB);
                    c = addUnsigned(c, CC);
                    d = addUnsigned(d, DD);
                }
                var temp = wordToHex(a) + wordToHex(b) + wordToHex(c) + wordToHex(d);
                return temp.toLowerCase();
            }

            const DEFAULT_SETTINGS = {
                countdownMs: 1000,
                loopCount: 100,
                nextSelector:
                    'button[title="Next chapter"], a[title="Next chapter"], button[title*="Next Chapter"], a[title*="Next Chapter"], #next_chap, .next-chap, .next-chapter, .btn-next, a.next',
                contentSelector: '',
                fullFrozenHtml: true
            };

            function escapeHtml(s) {
                return String(s || '').replace(/[&<>"']/g, (c) => ({
                    '&': '&amp;',
                    '<': '&lt;',
                    '>': '&gt;',
                    '"': '&quot;',
                    "'": '&#39;'
                })[c]);
            }

            function escapeAttr(s) {
                return String(s || '').replace(/"/g, '&quot;');
            }

            function sanitizeFileName(s) {
                return String(s || 'Novel').replace(/[/\\?%*:|"<>]/g, '_').replace(/\s+/g, ' ').trim();
            }

            function loadSettings() {
                try {
                    let raw;
                    if (typeof GM_getValue === 'function') {
                        raw = GM_getValue(SETTINGS_KEY, null);
                    } else {
                        raw = localStorage.getItem(SETTINGS_KEY);
                    }
                    if (!raw) return { ...DEFAULT_SETTINGS };
                    const parsed = typeof raw === 'string' ? JSON.parse(raw) : raw;
                    const merged = { ...DEFAULT_SETTINGS, ...parsed };
                    if (merged.fullFrozenHtml === undefined) merged.fullFrozenHtml = true;
                    return merged;
                } catch (e) {
                    console.warn('novelMultiDownloader: failed to load settings, using defaults', e);
                    return { ...DEFAULT_SETTINGS };
                }
            }

            function saveSettings(s) {
                try {
                    const json = JSON.stringify(s);
                    if (typeof GM_setValue === 'function') {
                        GM_setValue(SETTINGS_KEY, json);
                    } else {
                        localStorage.setItem(SETTINGS_KEY, json);
                    }
                } catch (e) {
                    console.warn('novelMultiDownloader: could not persist settings', e);
                }
            }

            let settings = loadSettings();

            function notifyLoopState(active, remaining, status) {
                if (window.BrowserBridge && typeof window.BrowserBridge.onAutoLoopStateChanged === 'function') {
                    window.BrowserBridge.onAutoLoopStateChanged(active, remaining || 0, status || '');
                } else if (window.AndroidBridge && typeof window.AndroidBridge.onAutoLoopStateChanged === 'function') {
                    window.AndroidBridge.onAutoLoopStateChanged(active, remaining || 0, status || '');
                }
            }

            function triggerFolderSync(novelTitle) {
                const titleToSync = novelTitle || sessionStorage.getItem(NOVEL_NAME_KEY) || detectNovelInfo().novelName || 'Novel';
                if (window.BrowserBridge && typeof window.BrowserBridge.syncFolder === 'function') {
                    window.BrowserBridge.syncFolder(titleToSync);
                } else if (window.AndroidBridge && typeof window.AndroidBridge.syncFolder === 'function') {
                    window.AndroidBridge.syncFolder(titleToSync);
                }
            }

            // Route chapter save into proper novel folder via native bridge
            function saveChapterContent(novelTitle, chapterTitle, chapNum, htmlContent, fileName) {
                let savedViaBridge = false;
                const chInt = parseInt(chapNum, 10) || 1;
                if (window.BrowserBridge && typeof window.BrowserBridge.saveDownloadedChapter === 'function') {
                    try {
                        window.BrowserBridge.saveDownloadedChapter(novelTitle, chapterTitle, chInt, htmlContent);
                        savedViaBridge = true;
                    } catch (e) {
                        console.warn('BrowserBridge.saveDownloadedChapter error', e);
                    }
                } else if (window.AndroidBridge && typeof window.AndroidBridge.saveDownloadedChapter === 'function') {
                    try {
                        window.AndroidBridge.saveDownloadedChapter(novelTitle, chapterTitle, chInt, htmlContent);
                        savedViaBridge = true;
                    } catch (e) {
                        console.warn('AndroidBridge.saveDownloadedChapter error', e);
                    }
                }

                if (!savedViaBridge) {
                    try {
                        const blob = new Blob([htmlContent], { type: 'text/html;charset=utf-8' });
                        const downloadUrl = URL.createObjectURL(blob);
                        const a = document.createElement('a');
                        a.href = downloadUrl;
                        a.download = fileName;
                        document.body.appendChild(a);
                        a.click();
                        document.body.removeChild(a);
                        URL.revokeObjectURL(downloadUrl);
                    } catch (e) {
                        console.error('Fallback blob download error', e);
                    }
                }
            }

            // =============================================================
            // 1. ASSETS: FONTAWESOME + SCOPED STYLES
            // =============================================================
            function injectExternal(href, isScript = false) {
                return new Promise((resolve) => {
                    try {
                        if (isScript) {
                            const s = document.createElement('script');
                            s.src = href;
                            s.onload = resolve;
                            s.onerror = resolve;
                            document.head.appendChild(s);
                        } else {
                            const l = document.createElement('link');
                            l.rel = 'stylesheet';
                            l.href = href;
                            l.onload = resolve;
                            l.onerror = resolve;
                            document.head.appendChild(l);
                        }
                    } catch (e) { resolve(); }
                });
            }

            injectExternal('https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.1/css/all.min.css');

            const scopedCss = `
                #na-dl-root, #na-dl-root *, .na-dl-modal-backdrop, .na-dl-modal-backdrop * { box-sizing: border-box; }
                
                #na-dl-root {
                    position: fixed;
                    top: 50%;
                    right: 8px;
                    transform: translateY(-50%);
                    z-index: 2147483000;
                    font-family: system-ui, -apple-system, Segoe UI, Roboto, sans-serif;
                }
                .na-dl-btn {
                    width: 36px;
                    height: 36px;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    border: none;
                    border-radius: 10px;
                    background: rgba(17, 17, 17, 0.92);
                    color: #fff;
                    cursor: pointer;
                    font-size: 15px;
                    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.45);
                    backdrop-filter: blur(8px);
                    transition: background 0.15s, transform 0.15s, color 0.15s;
                    padding: 0;
                    outline: none;
                }
                .na-dl-btn:hover {
                    background: #2563eb;
                    color: #fff;
                    transform: scale(1.08);
                }
                .na-dl-btn.active {
                    background: #dc2626;
                    color: #fff;
                }
                .na-dl-btn.working {
                    background: #2563eb;
                    color: #fff;
                }
                .na-dl-badge {
                    font-size: 11px;
                    font-weight: 700;
                    color: #fff;
                }

                .na-dl-modal-backdrop {
                    position: fixed;
                    inset: 0;
                    background: rgba(0, 0, 0, 0.65);
                    z-index: 2147483010;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    padding: 10px;
                    font-family: system-ui, -apple-system, Segoe UI, Roboto, sans-serif;
                }
                .na-dl-modal {
                    background: #0f172a;
                    color: #e5e7eb;
                    width: 100%;
                    max-width: 480px;
                    max-height: 92vh;
                    border-radius: 16px;
                    box-shadow: 0 20px 60px rgba(0, 0, 0, 0.7);
                    font-size: 13px;
                    display: flex;
                    flex-direction: column;
                    overflow: hidden;
                    border: 1px solid rgba(255, 255, 255, 0.08);
                }
                .na-dl-modal-header {
                    flex-shrink: 0;
                    background: #111827;
                    padding: 14px 16px;
                    border-bottom: 1px solid rgba(255, 255, 255, 0.08);
                    display: flex;
                    align-items: center;
                    gap: 10px;
                }
                .na-dl-modal-header h2 {
                    flex: 1;
                    font-size: 15px;
                    font-weight: 700;
                    margin: 0;
                    display: flex;
                    align-items: center;
                    gap: 8px;
                    color: #f1f5f9;
                }
                .na-dl-domain-tag {
                    font-size: 10px;
                    color: #94a3b8;
                    font-weight: 500;
                    background: rgba(255, 255, 255, 0.06);
                    padding: 2px 8px;
                    border-radius: 999px;
                    flex-shrink: 0;
                }
                .na-dl-close {
                    background: transparent;
                    border: none;
                    color: #9ca3af;
                    font-size: 18px;
                    cursor: pointer;
                    padding: 4px 8px;
                    border-radius: 6px;
                    flex-shrink: 0;
                }
                .na-dl-close:hover {
                    background: rgba(255, 255, 255, 0.08);
                    color: #fff;
                }
                .na-dl-modal-body {
                    flex: 1 1 auto;
                    overflow-y: auto;
                    -webkit-overflow-scrolling: touch;
                    padding: 14px 16px;
                }
                .na-dl-section {
                    background: rgba(255, 255, 255, 0.03);
                    border: 1px solid rgba(255, 255, 255, 0.06);
                    border-radius: 10px;
                    padding: 12px;
                    margin-bottom: 10px;
                }
                .na-dl-section:last-child {
                    margin-bottom: 0;
                }
                .na-dl-section h3 {
                    font-size: 11px;
                    font-weight: 700;
                    text-transform: uppercase;
                    color: #93c5fd;
                    margin: 0 0 10px;
                    letter-spacing: 0.06em;
                    display: flex;
                    align-items: center;
                    gap: 6px;
                }
                .na-dl-field {
                    display: flex;
                    flex-direction: column;
                    gap: 5px;
                    margin-bottom: 10px;
                }
                .na-dl-field:last-child {
                    margin-bottom: 0;
                }
                .na-dl-field label {
                    font-size: 11px;
                    color: #cbd5e1;
                    font-weight: 600;
                }
                .na-dl-input {
                    width: 100%;
                    background: #1e293b;
                    color: #fff;
                    border: 1px solid #334155;
                    border-radius: 8px;
                    padding: 8px 10px;
                    font-size: 13px;
                    outline: none;
                    transition: border-color 0.15s;
                    font-family: inherit;
                }
                .na-dl-input:focus {
                    border-color: #2563eb;
                }
                .na-dl-toggle-row {
                    display: flex;
                    align-items: center;
                    justify-content: space-between;
                    gap: 12px;
                    margin-bottom: 10px;
                }
                .na-dl-toggle-row:last-child {
                    margin-bottom: 0;
                }
                .na-dl-toggle-label {
                    flex: 1 1 auto;
                    min-width: 0;
                    font-size: 11px;
                    color: #cbd5e1;
                    font-weight: 600;
                }
                .na-dl-toggle-label small {
                    display: block;
                    color: #94a3b8;
                    font-weight: 400;
                    font-size: 10px;
                    margin-top: 2px;
                    line-height: 1.35;
                }
                .na-dl-toggle {
                    position: relative;
                    display: inline-block;
                    width: 38px;
                    height: 20px;
                    flex-shrink: 0;
                }
                .na-dl-toggle input {
                    opacity: 0;
                    width: 0;
                    height: 0;
                }
                .na-dl-toggle .slider {
                    position: absolute;
                    cursor: pointer;
                    inset: 0;
                    background: #334155;
                    border-radius: 34px;
                    transition: 0.2s;
                }
                .na-dl-toggle .slider::before {
                    content: '';
                    position: absolute;
                    height: 16px;
                    width: 16px;
                    left: 2px;
                    bottom: 2px;
                    background: #fff;
                    border-radius: 50%;
                    transition: 0.2s;
                }
                .na-dl-toggle input:checked + .slider {
                    background: #2563eb;
                }
                .na-dl-toggle input:checked + .slider::before {
                    transform: translateX(18px);
                }
                .na-dl-btn-sm {
                    padding: 8px 14px;
                    border-radius: 8px;
                    font-size: 12px;
                    font-weight: 600;
                    border: none;
                    cursor: pointer;
                    background: #334155;
                    color: #fff;
                    display: inline-flex;
                    align-items: center;
                    gap: 6px;
                    font-family: inherit;
                    transition: background 0.15s;
                }
                .na-dl-btn-sm:hover {
                    background: #475569;
                }
                .na-dl-btn-sm.primary {
                    background: #2563eb;
                }
                .na-dl-btn-sm.primary:hover {
                    background: #1d4ed8;
                }
                .na-dl-btn-sm.ghost {
                    background: transparent;
                    border: 1px solid #334155;
                }
                .na-dl-btn-sm.ghost:hover {
                    background: rgba(255, 255, 255, 0.05);
                }
                .na-dl-modal-footer {
                    flex-shrink: 0;
                    background: #111827;
                    padding: 12px 16px;
                    border-top: 1px solid rgba(255, 255, 255, 0.08);
                    display: flex;
                    gap: 8px;
                    justify-content: flex-end;
                }

                @media (max-width: 560px) {
                    #na-dl-root { right: 4px; }
                    .na-dl-modal { border-radius: 16px 16px 0 0; max-height: 100vh; }
                    .na-dl-modal-backdrop { align-items: flex-end; padding: 0; }
                }

                /* Toast Notifications */
                #na-toast-container {
                    position: fixed;
                    top: 24px;
                    right: 24px;
                    z-index: 2147483647;
                    display: flex;
                    flex-direction: column;
                    gap: 10px;
                    pointer-events: none;
                    font-family: system-ui, -apple-system, Segoe UI, Roboto, sans-serif;
                }
                .na-toast {
                    pointer-events: auto;
                    cursor: pointer;
                    min-width: 220px;
                    max-width: 380px;
                    padding: 10px 14px;
                    border-radius: 12px;
                    font-size: 13px;
                    line-height: 1.4;
                    display: flex;
                    align-items: center;
                    gap: 10px;
                    color: #f1f5f9;
                    background: rgba(15, 23, 42, 0.95);
                    border: 1px solid rgba(255, 255, 255, 0.12);
                    box-shadow: 0 10px 30px rgba(0, 0, 0, 0.55), 0 0 0 1px rgba(0, 0, 0, 0.2);
                    backdrop-filter: blur(12px);
                    -webkit-backdrop-filter: blur(12px);
                    animation: naToastSlideIn 0.25s cubic-bezier(0.16, 1, 0.3, 1) forwards;
                    transition: opacity 0.25s, transform 0.25s;
                }
                .na-toast.hide {
                    opacity: 0;
                    transform: translateY(-10px) scale(0.95);
                }
                .na-toast-icon {
                    font-size: 16px;
                    flex-shrink: 0;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                }
                .na-toast.info {
                    border-color: rgba(59, 130, 246, 0.45);
                }
                .na-toast.info .na-toast-icon {
                    color: #60a5fa;
                }
                .na-toast.success {
                    border-color: rgba(16, 185, 129, 0.45);
                }
                .na-toast.success .na-toast-icon {
                    color: #34d399;
                }
                .na-toast.warn, .na-toast.warning {
                    border-color: rgba(245, 158, 11, 0.55);
                }
                .na-toast.warn .na-toast-icon, .na-toast.warning .na-toast-icon {
                    color: #fbbf24;
                }
                .na-toast.error {
                    border-color: rgba(239, 68, 68, 0.55);
                }
                .na-toast.error .na-toast-icon {
                    color: #f87171;
                }
                .na-toast-msg {
                    flex: 1 1 auto;
                    font-weight: 500;
                }
                @keyframes naToastSlideIn {
                    from {
                        opacity: 0;
                        transform: translateY(-16px) scale(0.92);
                    }
                    to {
                        opacity: 1;
                        transform: translateY(0) scale(1);
                    }
                }
                @media (max-width: 560px) {
                    #na-toast-container {
                        top: 14px;
                        left: 14px;
                        right: 14px;
                        align-items: center;
                    }
                    .na-toast {
                        width: 100%;
                        max-width: 100%;
                    }
                }
            `;

            if (typeof GM_addStyle === 'function') {
                GM_addStyle(scopedCss);
            } else {
                try {
                    const style = document.createElement('style');
                    style.textContent = scopedCss;
                    document.head.appendChild(style);
                } catch (e) {}
            }

            // Toast notification dispatcher (DOM overlay + GM_notification / native bridge)
            function showToast(message, type, durationMs) {
                const toastType = type || 'info';
                const ttl = durationMs || 3500;
                try {
                    let container = document.getElementById('na-toast-container');
                    if (!container) {
                        container = document.createElement('div');
                        container.id = 'na-toast-container';
                        (document.body || document.documentElement).appendChild(container);
                    }

                    const toast = document.createElement('div');
                    toast.className = 'na-toast ' + toastType;

                    let iconHtml = '<i class="fa-solid fa-circle-info"></i>';
                    if (toastType === 'success') {
                        iconHtml = '<i class="fa-solid fa-circle-check"></i>';
                    } else if (toastType === 'warn' || toastType === 'warning') {
                        iconHtml = '<i class="fa-solid fa-triangle-exclamation"></i>';
                    } else if (toastType === 'error') {
                        iconHtml = '<i class="fa-solid fa-circle-xmark"></i>';
                    }

                    toast.innerHTML =
                        '<span class="na-toast-icon">' + iconHtml + '</span>' +
                        '<span class="na-toast-msg">' + escapeHtml(message) + '</span>';

                    toast.addEventListener('click', function() {
                        toast.classList.add('hide');
                        setTimeout(function() { toast.remove(); }, 250);
                    });

                    container.appendChild(toast);

                    setTimeout(function() {
                        if (toast.parentNode) {
                            toast.classList.add('hide');
                            setTimeout(function() { toast.remove(); }, 250);
                        }
                    }, ttl);
                } catch (e) {
                    console.log('[NovelDownloader Toast]', message);
                }

                try {
                    if (typeof GM_notification === 'function') {
                        GM_notification({ text: message, title: 'Novel Downloader', timeout: ttl });
                    } else if (window.BrowserBridge && typeof window.BrowserBridge.gmNotification === 'function') {
                        window.BrowserBridge.gmNotification(message);
                    } else if (window.AndroidBridge && typeof window.AndroidBridge.gmNotification === 'function') {
                        window.AndroidBridge.gmNotification(message);
                    }
                } catch (e) {}
            }

            // =============================================================
            // 2. NOVEL DETECTION & CHAPTER RESOLUTION
            // =============================================================
            function detectNovelInfo() {
                let novelName = '';
                let chapterNum = '';

                const pathname = decodeURIComponent(window.location.pathname || '');

                const classicMatch = pathname.match(/\/chapter\/([^\/]+)\/chapter-(\d+(?:\.\d+)?)/i);
                if (classicMatch) {
                    const rawTitle = classicMatch[1].replace(/[-_]/g, ' ');
                    novelName = rawTitle.replace(/\b\w/g, (c) => c.toUpperCase());
                    chapterNum = classicMatch[2];
                    return { novelName, chapterNum };
                }

                const slugMatch = pathname.match(/\/(?:novel|book|series|read|fiction)?\/?([^\/]+)\/(?:chapter[-_]?|chap[-_]?|ch[-_]?|c)?(\d+(?:\.\d+)?)/i);
                if (slugMatch) {
                    let slug = slugMatch[1].replace(/[-_]/g, ' ').replace(/^\d+\s*/, '').trim();
                    const ignoreSlugs = ['chapter', 'read', 'novel', 'book', 'series', 'fiction', 'index'];
                    if (slug && !ignoreSlugs.includes(slug.toLowerCase())) {
                        novelName = slug.replace(/\b\w/g, (c) => c.toUpperCase());
                    }
                    chapterNum = slugMatch[2];
                }

                if (!chapterNum) {
                    const urlChapMatch = pathname.match(/(?:chapter|chap|ch)[-_]?(\d+(?:\.\d+)?)/i) ||
                                         pathname.match(/(\d+(?:\.\d+)?)(?=\.html?${'$'})/i) ||
                                         pathname.match(/(\d+)(?!.*\d)/);
                    if (urlChapMatch) {
                        chapterNum = urlChapMatch[1];
                    }
                }

                const title = (document.title || '').trim();
                if (!chapterNum && title) {
                    const titleChapMatch = title.match(/(?:chapter|chap|ch)\.?\s*(\d+(?:\.\d+)?)/i);
                    if (titleChapMatch) {
                        chapterNum = titleChapMatch[1];
                    }
                }
                if (!chapterNum) {
                    try {
                        const h = document.querySelector('h1, h2, .chapter-title, #chapter-heading');
                        if (h) {
                            const hMatch = (h.textContent || '').match(/(?:chapter|chap|ch)\.?\s*(\d+(?:\.\d+)?)/i);
                            if (hMatch) chapterNum = hMatch[1];
                        }
                    } catch (e) {}
                }

                if (!novelName) {
                    const metaNovel = document.querySelector('meta[property="og:novel:novel_name"], meta[name="novel-title"], meta[property="book:title"]');
                    if (metaNovel && metaNovel.content) {
                        novelName = metaNovel.content.trim();
                    }
                }
                if (!novelName) {
                    const crumbs = Array.from(document.querySelectorAll('.breadcrumb a, .breadcrumbs a, nav[aria-label="breadcrumb"] a, #breadcrumb a'));
                    if (crumbs.length >= 2) {
                        const cand = crumbs[1].textContent.trim();
                        if (cand && !/home|main|index/i.test(cand) && cand.length > 1 && cand.length < 80) {
                            novelName = cand;
                        }
                    }
                }
                if (!novelName) {
                    const novelEl = document.querySelector('.novel-title, .series-title, .book-title, .novelname, h1.entry-title, .chapter-novel-title');
                    if (novelEl) {
                        const cand = novelEl.textContent.trim();
                        if (cand && cand.length > 1 && cand.length < 90) {
                            novelName = cand;
                        }
                    }
                }
                if (!novelName && title) {
                    let clean = title;
                    clean = clean.replace(/\s*[-–|•]\s*(?:read|free|novel|webnovel|lightnovel|wuxia|vip|online|boxnovel|novelfull|ranobes|royalroad).*${'$'}/i, '');
                    clean = clean.replace(/\s*[-–|•]\s*[^–\-|•]+${'$'}/, '');
                    clean = clean.replace(/^read\s+/i, '');
                    const parts = clean.split(/(?:[-–|•:]?\s*)(?:chapter|chap|ch)\.?\s*\d+/i);
                    if (parts.length > 1 && parts[0].trim().length > 1) {
                        novelName = parts[0].trim();
                    } else if (clean.trim()) {
                        novelName = clean.trim();
                    }
                }

                if (!novelName) novelName = 'Novel';
                if (!chapterNum) chapterNum = '1';

                return { novelName: novelName.trim(), chapterNum: chapterNum.trim() };
            }

            function getNextChapterNumber(val) {
                if (!val) return '1';
                const s = String(val).trim();
                const match = s.match(/^(\D*)(\d+)(\D*)$/);
                if (match) {
                    const prefix = match[1];
                    const digits = match[2];
                    const suffix = match[3];
                    const next = (parseInt(digits, 10) + 1).toString().padStart(digits.length, '0');
                    return prefix + next + suffix;
                }
                const flt = parseFloat(s);
                if (!isNaN(flt)) {
                    return (flt + 1).toString();
                }
                return s;
            }

            function getFormattedFileName(customNovelName, customChapNum) {
                const sessionNovel = customNovelName || sessionStorage.getItem(NOVEL_NAME_KEY);
                const sessionChap = customChapNum || sessionStorage.getItem(CHAP_NUM_KEY);
                if (sessionNovel && sessionChap) {
                    return sanitizeFileName(sessionNovel) + ' Chapter ' + sessionChap + '.html';
                }
                const detected = detectNovelInfo();
                const novelName = sessionNovel || detected.novelName || 'Novel';
                const chapNum = sessionChap || detected.chapterNum || '1';
                return sanitizeFileName(novelName) + ' Chapter ' + chapNum + '.html';
            }

            function getNextChapterElement() {
                try {
                    if (settings.nextSelector) {
                        const el = document.querySelector(settings.nextSelector);
                        if (el) return el;
                    }
                } catch (e) {
                    console.warn('Bad next-chapter selector:', settings.nextSelector, e);
                }
                try {
                    const elements = Array.from(document.querySelectorAll('a, button'));
                    for (const candidate of elements) {
                        const text = (candidate.innerText || candidate.title || '').trim().toLowerCase();
                        if (text === 'next chapter' || text === 'next >' || text === 'next' || text.includes('next chapter')) {
                            return candidate;
                        }
                    }
                } catch (e) {}
                return null;
            }

            function incrementUrlNext() {
                let urlObj;
                try {
                    urlObj = new URL(window.location.href);
                } catch (e) {
                    return;
                }
                let pathname = urlObj.pathname;
                let regex = /(\d+)(?=\.html?${'$'})/i;
                if (!regex.test(pathname)) {
                    regex = /(\d+)(?!.*\d)/;
                }
                if (regex.test(pathname)) {
                    urlObj.pathname = pathname.replace(regex, (match) => {
                        const len = match.length;
                        const nextNum = parseInt(match, 10) + 1;
                        return nextNum.toString().padStart(len, '0');
                    });
                    window.location.href = urlObj.href;
                } else {
                    alert('No digit found in URL to increment.');
                }
            }

            function triggerNextNavigation() {
                const targetElement = getNextChapterElement();
                const isLocalFile = (location.protocol === 'file:' || /\.html?${'$'}/i.test(location.pathname));
                if (targetElement) {
                    const oldUrl = window.location.href;
                    try {
                        targetElement.click();
                    } catch (e) {
                        console.warn('Click on next chapter failed', e);
                    }
                    setTimeout(() => {
                        if (window.location.href === oldUrl && targetElement.href) {
                            try {
                                window.location.href = targetElement.href;
                            } catch (e) {}
                        }
                    }, 1200);
                    return true;
                } else if (isLocalFile) {
                    incrementUrlNext();
                    return true;
                } else {
                    return false;
                }
            }

            async function toBase64(url) {
                try {
                    const response = await fetch(url);
                    const blob = await response.blob();
                    return await new Promise((resolve) => {
                        const reader = new FileReader();
                        reader.onloadend = () => resolve(reader.result);
                        reader.onerror = () => resolve(url);
                        reader.readAsDataURL(blob);
                    });
                } catch { return url; }
            }

            // =============================================================
            // 3. DOWNLOAD EXECUTORS (FROZEN HTML & CLEAN MINIMAL HTML)
            // =============================================================
            async function executeSaveCleanHTML() {
                let container = null;
                if (settings.contentSelector) {
                    try {
                        container = document.querySelector(settings.contentSelector);
                    } catch (e) {
                        console.warn('Bad content selector:', settings.contentSelector, e);
                    }
                }
                if (!container) {
                    const fallbacks = [
                        '#chapter-content', '.chapter-content', '#content', '.content-area',
                        '.reading-content', '.entry-content', '.text-content', '#article', 'article', 'main', '.chapter-body'
                    ];
                    for (const sel of fallbacks) {
                        try {
                            const el = document.querySelector(sel);
                            if (el) { container = el; break; }
                        } catch (e) {}
                    }
                }
                if (!container) container = document.body;

                const clone = container.cloneNode(true);

                const toRemoveSels = [
                    '#na-dl-root', '.na-dl-modal-backdrop', '#na-floating-next',
                    'script', 'style', 'link', 'noscript', 'iframe', 'embed', 'object', 'video', 'audio', 'canvas', 'svg',
                    'img', 'picture', 'source',
                    'form', 'input', 'button', 'select', 'textarea',
                    'nav', 'header', 'footer', 'aside',
                    '.ads', '.ad', '.advertisement', '[class*="ad-"]', '[id*="ad-"]', 'ins.adsbygoogle'
                ];

                toRemoveSels.forEach(sel => {
                    try {
                        clone.querySelectorAll(sel).forEach(el => el.remove());
                    } catch (e) {}
                });

                const ALLOWED_TAGS = new Set([
                    'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
                    'p', 'blockquote', 'pre', 'hr', 'br',
                    'ul', 'ol', 'li',
                    'strong', 'b', 'em', 'i', 'u', 's', 'strike', 'del',
                    'sub', 'sup', 'mark', 'small', 'code', 'span', 'div'
                ]);

                const allEls = Array.from(clone.querySelectorAll('*'));
                for (const el of allEls) {
                    const tag = el.tagName.toLowerCase();
                    if (!ALLOWED_TAGS.has(tag)) {
                        while (el.firstChild) {
                            el.parentNode.insertBefore(el.firstChild, el);
                        }
                        el.remove();
                        continue;
                    }

                    const attrs = Array.from(el.attributes);
                    for (const attr of attrs) {
                        el.removeAttribute(attr.name);
                    }
                }

                clone.querySelectorAll('p, div, span').forEach(el => {
                    if (!el.querySelector('br, hr') && el.textContent.trim() === '') {
                        el.remove();
                    }
                });

                const sessionNovel = sessionStorage.getItem(NOVEL_NAME_KEY);
                const sessionChap = sessionStorage.getItem(CHAP_NUM_KEY);
                const detected = detectNovelInfo();
                const novelTitle = sessionNovel || detected.novelName || 'Novel';
                const chapterNum = sessionChap || detected.chapterNum || '1';
                const fileName = getFormattedFileName(sessionNovel, sessionChap);
                const chapterTitle = novelTitle + ' Chapter ' + chapterNum;

                const cleanDoc = document.implementation.createHTMLDocument(fileName.replace(/\.html?${'$'}/i, ''));

                const metaCharset = cleanDoc.createElement('meta');
                metaCharset.setAttribute('charset', 'utf-8');
                cleanDoc.head.appendChild(metaCharset);

                const metaViewport = cleanDoc.createElement('meta');
                metaViewport.setAttribute('name', 'viewport');
                metaViewport.setAttribute('content', 'width=device-width, initial-scale=1.0');
                cleanDoc.head.appendChild(metaViewport);

                const titleEl = cleanDoc.createElement('title');
                titleEl.textContent = novelTitle + ' - Chapter ' + chapterNum;
                cleanDoc.head.appendChild(titleEl);

                const styleEl = cleanDoc.createElement('style');
                styleEl.textContent = `
                    :root {
                        --bg: #121214;
                        --text: #e4e4e7;
                        --text-muted: #a1a1aa;
                        --accent: #3b82f6;
                        --accent-hover: #2563eb;
                        --border: #27272a;
                        --font-main: system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
                    }
                    @media (prefers-color-scheme: light) {
                        :root {
                            --bg: #f8fafc;
                            --text: #1e293b;
                            --text-muted: #64748b;
                            --border: #e2e8f0;
                        }
                    }
                    * { box-sizing: border-box; }
                    body {
                        margin: 0;
                        padding: 0;
                        background: var(--bg);
                        color: var(--text);
                        font-family: var(--font-main);
                        font-size: 18px;
                        line-height: 1.85;
                        -webkit-font-smoothing: antialiased;
                        -moz-osx-font-smoothing: grayscale;
                    }
                    .reader-container {
                        max-width: 760px;
                        margin: 0 auto;
                        padding: 40px 20px 100px;
                    }
                    .reader-header {
                        text-align: center;
                        margin-bottom: 32px;
                        padding-bottom: 20px;
                        border-bottom: 1px solid var(--border);
                    }
                    .reader-novel-title {
                        font-size: 1.15rem;
                        font-weight: 600;
                        color: var(--accent);
                        margin: 0 0 8px;
                        text-transform: uppercase;
                        letter-spacing: 0.05em;
                    }
                    .reader-chapter-title {
                        font-size: 1.65rem;
                        font-weight: 700;
                        margin: 0;
                        color: var(--text);
                    }
                    .reader-content {
                        font-size: 1.125rem;
                        line-height: 1.85;
                        word-break: break-word;
                    }
                    .reader-content p {
                        margin: 0 0 1.4em;
                    }
                    .reader-content h1, .reader-content h2, .reader-content h3,
                    .reader-content h4, .reader-content h5, .reader-content h6 {
                        color: var(--text);
                        margin: 1.6em 0 0.8em;
                        line-height: 1.3;
                    }
                    .reader-content blockquote {
                        margin: 1.5em 0;
                        padding: 10px 18px;
                        border-left: 4px solid var(--accent);
                        background: rgba(59, 130, 246, 0.05);
                        color: var(--text-muted);
                        font-style: italic;
                    }
                    .reader-content hr {
                        border: none;
                        border-top: 1px solid var(--border);
                        margin: 2em 0;
                    }
                    .reader-content ul, .reader-content ol {
                        margin: 1em 0 1.5em 1.5em;
                        padding: 0;
                    }
                    .reader-content li {
                        margin-bottom: 0.5em;
                    }
                    #na-floating-next {
                        position: fixed;
                        bottom: 24px;
                        right: 24px;
                        z-index: 9999;
                        background: var(--accent);
                        color: #fff;
                        width: 56px;
                        height: 56px;
                        border-radius: 50%;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        cursor: pointer;
                        border: none;
                        box-shadow: 0 4px 16px rgba(0, 0, 0, 0.35);
                        transition: transform 0.2s, background 0.2s;
                    }
                    #na-floating-next:hover {
                        transform: scale(1.08);
                        background: var(--accent-hover);
                    }
                    @media (max-width: 600px) {
                        body { font-size: 16px; line-height: 1.75; }
                        .reader-container { padding: 24px 14px 80px; }
                        .reader-chapter-title { font-size: 1.35rem; }
                        #na-floating-next { bottom: 16px; right: 16px; width: 48px; height: 48px; }
                    }
                `;
                cleanDoc.head.appendChild(styleEl);

                const containerDiv = cleanDoc.createElement('div');
                containerDiv.className = 'reader-container';

                const headerEl = cleanDoc.createElement('header');
                headerEl.className = 'reader-header';
                headerEl.innerHTML = 
                    '<div class="reader-novel-title">' + escapeHtml(novelTitle) + '</div>' +
                    '<h1 class="reader-chapter-title">Chapter ' + escapeHtml(chapterNum) + '</h1>';
                containerDiv.appendChild(headerEl);

                const bodyDiv = cleanDoc.createElement('div');
                bodyDiv.className = 'reader-content';
                bodyDiv.innerHTML = clone.innerHTML;
                containerDiv.appendChild(bodyDiv);

                const standaloneBtn = cleanDoc.createElement('button');
                standaloneBtn.id = 'na-floating-next';
                standaloneBtn.title = 'Increment URL Digit (Next File)';
                standaloneBtn.innerHTML = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" style="width:20px;height:20px;fill:currentColor;"><path d="M52.5 440.6c-9.5 7.9-22.8 9.7-34.1 4.4S0 428.4 0 416V96C0 83.6 7.2 72.3 18.4 67s24.5-3.6 34.1 4.4L224 214.3V96c0-12.4 7.2-23.7 18.4-29s24.5-3.6 34.1 4.4L448 214.3V96c0-17.7 14.3-32 32-32s32 14.3 32 32v320c0 17.7-14.3 32-32 32s-32-14.3-32-32V297.7L276.5 440.6c-9.5 7.9-22.8 9.7-34.1 4.4S224 428.4 224 416V297.7L52.5 440.6z"/></svg>';

                const standaloneScript = cleanDoc.createElement('script');
                standaloneScript.textContent = `
                    document.getElementById('na-floating-next').addEventListener('click', function() {
                        let urlObj;
                        try { urlObj = new URL(window.location.href); } catch(e) { return; }
                        let pathname = urlObj.pathname;
                        let regex = /(\\d+)(?=\\.html?${'$'})/i;
                        if (!regex.test(pathname)) { regex = /(\\d+)(?!.*\\d)/; }
                        if (regex.test(pathname)) {
                            urlObj.pathname = pathname.replace(regex, (match) => {
                                return (parseInt(match, 10) + 1).toString().padStart(match.length, '0');
                            });
                            window.location.href = urlObj.href;
                        } else { alert("No digit found in URL to increment."); }
                    });
                `;

                cleanDoc.body.appendChild(containerDiv);
                cleanDoc.body.appendChild(standaloneBtn);
                cleanDoc.body.appendChild(standaloneScript);

                const pageContent = '<!DOCTYPE html>\n' + cleanDoc.documentElement.outerHTML;
                saveChapterContent(novelTitle, chapterTitle, chapterNum, pageContent, fileName);
            }

            async function executeSaveHTML() {
                const isFullFrozen = sessionStorage.getItem(FULL_FROZEN_KEY) !== null
                    ? (sessionStorage.getItem(FULL_FROZEN_KEY) === 'true')
                    : (settings.fullFrozenHtml !== false);

                if (!isFullFrozen) {
                    return await executeSaveCleanHTML();
                }

                const clonedDoc = document.documentElement.cloneNode(true);
                ['#na-dl-root', '.na-dl-modal-backdrop', '#na-floating-next'].forEach(sel => {
                    clonedDoc.querySelectorAll(sel).forEach(n => n.remove());
                });

                const clonedHead = clonedDoc.querySelector('head') || clonedDoc;
                const clonedBody = clonedDoc.querySelector('body') || clonedDoc;

                clonedDoc.querySelectorAll('script').forEach((s) => s.remove());

                const standaloneBtn = document.createElement('button');
                standaloneBtn.id = 'na-floating-next';
                standaloneBtn.title = 'Increment URL Digit (Next File)';
                standaloneBtn.innerHTML = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" style="width:20px;height:20px;fill:currentColor;"><path d="M52.5 440.6c-9.5 7.9-22.8 9.7-34.1 4.4S0 428.4 0 416V96C0 83.6 7.2 72.3 18.4 67s24.5-3.6 34.1 4.4L224 214.3V96c0-12.4 7.2-23.7 18.4-29s24.5-3.6 34.1 4.4L448 214.3V96c0-17.7 14.3-32 32-32s32 14.3 32 32v320c0 17.7-14.3 32-32 32s-32-14.3-32-32V297.7L276.5 440.6c-9.5 7.9-22.8 9.7-34.1 4.4S224 428.4 224 416V297.7L52.5 440.6z"/></svg>';

                const inlineStyle = document.createElement('style');
                inlineStyle.textContent = `
                    #na-floating-next { position: fixed; bottom: 24px; right: 24px; z-index: 2147483000; background: #2563eb; color: #fff; width: 56px; height: 56px; border-radius: 50%; display: flex; align-items: center; justify-content: center; cursor: pointer; border: none; box-shadow: 0 4px 16px rgba(0,0,0,0.4); transition: transform 0.2s, background 0.2s; }
                    #na-floating-next:hover { transform: scale(1.08); background: #1d4ed8; }
                `;
                clonedHead.appendChild(inlineStyle);
                clonedBody.appendChild(standaloneBtn);

                const standaloneScript = document.createElement('script');
                standaloneScript.textContent = `
                    document.getElementById('na-floating-next').addEventListener('click', function() {
                        let urlObj;
                        try { urlObj = new URL(window.location.href); } catch(e) { return; }
                        let pathname = urlObj.pathname;
                        let regex = /(\\d+)(?=\\.html?${'$'})/i;
                        if (!regex.test(pathname)) { regex = /(\\d+)(?!.*\\d)/; }
                        if (regex.test(pathname)) {
                            urlObj.pathname = pathname.replace(regex, (match) => {
                                return (parseInt(match, 10) + 1).toString().padStart(match.length, '0');
                            });
                            window.location.href = urlObj.href;
                        } else { alert("No digit found in URL to increment."); }
                    });
                `;
                clonedBody.appendChild(standaloneScript);

                const linkStyles = Array.from(document.querySelectorAll('link[rel="stylesheet"]'));
                for (const link of linkStyles) {
                    try {
                        const res = await fetch(link.href);
                        if (res.ok) {
                            const cssText = await res.text();
                            const styleTag = document.createElement('style');
                            styleTag.textContent = cssText;
                            const clonedLink = clonedDoc.querySelector('link[href="' + link.getAttribute('href') + '"]');
                            if (clonedLink) clonedLink.replaceWith(styleTag);
                        }
                    } catch (e) { console.warn('Could not inline CSS file:', link.href); }
                }

                const images = Array.from(clonedDoc.querySelectorAll('img[src]'));
                for (const img of images) {
                    const src = img.src;
                    if (src && !src.startsWith('data:')) {
                        img.src = await toBase64(src);
                    }
                }

                const sessionNovel = sessionStorage.getItem(NOVEL_NAME_KEY);
                const sessionChap = sessionStorage.getItem(CHAP_NUM_KEY);
                const detected = detectNovelInfo();
                const novelTitle = sessionNovel || detected.novelName || 'Novel';
                const chapterNum = sessionChap || detected.chapterNum || '1';
                const fileName = getFormattedFileName(sessionNovel, sessionChap);
                const chapterTitle = novelTitle + ' Chapter ' + chapterNum;

                const pageContent = '<!DOCTYPE html>\n' + clonedDoc.outerHTML;
                saveChapterContent(novelTitle, chapterTitle, chapterNum, pageContent, fileName);
            }

            // =============================================================
            // 4. AUTO-DOWNLOAD LOOP & MODAL UI
            // =============================================================
            let activeTimer = null;
            let navWatchdog = null;
            let scheduledRunTimer = null;
            let isSaving = false;
            let modalEl = null;

            const root = document.createElement('div');
            root.id = 'na-dl-root';
            (document.body || document.documentElement).appendChild(root);

            const loopBtn = document.createElement('button');
            loopBtn.className = 'na-dl-btn';
            loopBtn.id = 'na-dl-btn';
            loopBtn.title = 'Start Multi-Chapter Download Loop';
            loopBtn.innerHTML = '<i class="fa-solid fa-rotate"></i>';
            loopBtn.addEventListener('click', toggleAutoLoop);
            root.appendChild(loopBtn);

            function setButtonState(state, label) {
                if (state === 'active') {
                    loopBtn.className = 'na-dl-btn active';
                    loopBtn.innerHTML = '<i class="fa-solid fa-stop"></i>';
                    loopBtn.title = 'Stop Auto-Download Loop';
                } else if (state === 'countdown' && label != null) {
                    loopBtn.className = 'na-dl-btn active';
                    loopBtn.innerHTML = '<span class="na-dl-badge">' + label + '</span>';
                    loopBtn.title = 'Auto-Download Loop running (click to stop)';
                } else if (state === 'working') {
                    loopBtn.className = 'na-dl-btn working';
                    loopBtn.innerHTML = '<i class="fa-solid fa-hourglass-half"></i>';
                    loopBtn.title = 'Saving chapter HTML…';
                } else {
                    loopBtn.className = 'na-dl-btn';
                    loopBtn.innerHTML = '<i class="fa-solid fa-rotate"></i>';
                    loopBtn.title = 'Start Multi-Chapter Download Loop';
                }
            }

            function stopAutorun(reason) {
                const wasActive = sessionStorage.getItem(ACTIVE_KEY) === 'true';
                const lastNovel = sessionStorage.getItem(NOVEL_NAME_KEY) || '';
                sessionStorage.setItem(ACTIVE_KEY, 'false');
                sessionStorage.removeItem(NOVEL_NAME_KEY);
                sessionStorage.removeItem(CHAP_NUM_KEY);
                sessionStorage.removeItem(FULL_FROZEN_KEY);
                sessionStorage.removeItem(DELAY_MS_KEY);
                sessionStorage.removeItem(RAN_HASHES_KEY);
                sessionStorage.removeItem(SAME_HASH_RETRIES_KEY);
                sessionStorage.removeItem(SAVING_LOCK_KEY);
                if (activeTimer) {
                    clearInterval(activeTimer);
                    activeTimer = null;
                }
                if (navWatchdog) {
                    clearTimeout(navWatchdog);
                    navWatchdog = null;
                }
                if (scheduledRunTimer) {
                    clearTimeout(scheduledRunTimer);
                    scheduledRunTimer = null;
                }
                isSaving = false;
                setButtonState('idle');
                notifyLoopState(false, 0, '');

                triggerFolderSync(lastNovel);

                if (wasActive) {
                    if (reason === 'completed') {
                        showToast('✅ Auto-download completed! All chapters saved.', 'success', 5000);
                    } else if (reason === 'unchanged_retries') {
                        showToast('🛑 Auto-download stopped: Page content remained unchanged after 3 retries.', 'error', 6000);
                    } else if (reason === 'no_next_btn') {
                        showToast('⚠️ Auto-download stopped: Next chapter button not found.', 'warn', 5000);
                    } else if (reason === 'error') {
                        showToast('❌ Auto-download stopped due to error.', 'error', 5000);
                    } else if (reason === 'user') {
                        showToast('⏹️ Auto-download stopped.', 'info', 3000);
                    }
                }
            }

            function closeSetupModal() {
                if (modalEl) {
                    modalEl.remove();
                    modalEl = null;
                }
            }

            function openSetupModal() {
                if (modalEl) return;

                let tabInfo = null;
                if (window.BrowserBridge && typeof window.BrowserBridge.getTabDownloadInfo === 'function') {
                    try {
                        const raw = window.BrowserBridge.getTabDownloadInfo();
                        if (raw) tabInfo = JSON.parse(raw);
                    } catch (e) {}
                }

                const detected = detectNovelInfo();
                const initialNovelName = (tabInfo && tabInfo.title) ? tabInfo.title : detected.novelName;
                const initialChapterNum = (tabInfo && tabInfo.chapter) ? tabInfo.chapter : detected.chapterNum;
                const currentLoops = (settings.loopCount && settings.loopCount > 0) ? settings.loopCount : 100;
                const currentDelay = (settings.countdownMs && settings.countdownMs >= 50) ? settings.countdownMs : 1000;
                const isFullFrozen = settings.fullFrozenHtml !== false;

                modalEl = document.createElement('div');
                modalEl.className = 'na-dl-modal-backdrop';
                modalEl.innerHTML = `
                    <div class="na-dl-modal">
                        <div class="na-dl-modal-header">
                            <h2><i class="fa-solid fa-rotate"></i> Multi-Chapter Downloader</h2>
                            <span class="na-dl-domain-tag">${'$'}{escapeHtml(DOMAIN)}</span>
                            <button class="na-dl-btn-sm primary" id="na-dl-top-start-btn" style="padding: 5px 12px; font-size: 11px; margin-left: auto; flex-shrink: 0;"><i class="fa-solid fa-play"></i> Start</button>
                            <button class="na-dl-close" id="na-dl-close-btn" title="Close"><i class="fa-solid fa-xmark"></i></button>
                        </div>
                        <div class="na-dl-modal-body">
                            <div id="na-dl-dup-alert" style="display:none;background:#7f1d1d;color:#fecaca;padding:12px;border-radius:10px;margin-bottom:12px;border:1px solid #ef4444;">
                                <div style="font-weight:700;margin-bottom:4px;display:flex;align-items:center;gap:6px;">
                                    <i class="fa-solid fa-triangle-exclamation"></i> Chapter Already Exists
                                </div>
                                <div id="na-dl-dup-msg" style="font-size:12px;margin-bottom:10px;line-height:1.4;"></div>
                                <div style="display:flex;gap:8px;justify-content:flex-end;">
                                    <button class="na-dl-btn-sm ghost" id="na-dl-dup-cancel-btn" style="background:transparent;border:1px solid #f87171;color:#fff;">Cancel</button>
                                    <button class="na-dl-btn-sm primary" id="na-dl-dup-continue-btn" style="background:#dc2626;">Download Anyways</button>
                                </div>
                            </div>
                            <div class="na-dl-section">
                                <h3><i class="fa-solid fa-book"></i> Novel & Chapter</h3>
                                <div class="na-dl-field">
                                    <label>Novel Name</label>
                                    <input class="na-dl-input" type="text" id="na-dl-novel-name" value="${'$'}{escapeAttr(initialNovelName)}" placeholder="e.g. Shadow Slave">
                                </div>
                                <div class="na-dl-field">
                                    <label>Start Chapter Number</label>
                                    <input class="na-dl-input" type="text" id="na-dl-start-chap" value="${'$'}{escapeAttr(initialChapterNum)}" placeholder="e.g. 1">
                                </div>
                            </div>

                            <div class="na-dl-section">
                                <h3><i class="fa-solid fa-gears"></i> Loop Parameters</h3>
                                <div class="na-dl-field">
                                    <label>Number of Chapters to Download (0 = infinite)</label>
                                    <input class="na-dl-input" type="number" id="na-dl-loop-count" min="0" step="1" value="${'$'}{currentLoops}">
                                </div>
                                <div class="na-dl-field">
                                    <label>Pause Between Chapters (ms)</label>
                                    <input class="na-dl-input" type="number" id="na-dl-countdown" min="50" step="50" value="${'$'}{currentDelay}">
                                </div>
                            </div>

                            <div class="na-dl-section">
                                <h3><i class="fa-solid fa-file-code"></i> Download Format</h3>
                                <div class="na-dl-toggle-row">
                                    <div class="na-dl-toggle-label">
                                        Full Frozen HTML Page
                                        <small>ON = original assets & styles. OFF = lightweight clean reader text (only p, h1, h2...) with minimal CSS.</small>
                                    </div>
                                    <label class="na-dl-toggle">
                                        <input type="checkbox" id="na-dl-full-frozen" ${'$'}{isFullFrozen ? 'checked' : ''}>
                                        <span class="slider"></span>
                                    </label>
                                </div>
                                <div class="na-dl-field" style="margin-top: 10px;">
                                    <label>Content Container Selector <small style="color:#94a3b8;font-weight:400;">(used for Clean HTML mode)</small></label>
                                    <input class="na-dl-input" type="text" id="na-dl-content-selector" placeholder="#chapter-content, .reading-content" value="${'$'}{escapeAttr(settings.contentSelector || '')}">
                                </div>
                            </div>

                            <div class="na-dl-section">
                                <h3><i class="fa-solid fa-forward-step"></i> Navigation</h3>
                                <div class="na-dl-field">
                                    <label>Next Chapter Button Selector</label>
                                    <input class="na-dl-input" type="text" id="na-dl-next-selector" value="${'$'}{escapeAttr(settings.nextSelector)}">
                                </div>
                            </div>
                        </div>
                        <div class="na-dl-modal-footer">
                            <button class="na-dl-btn-sm ghost" id="na-dl-cancel-btn">Cancel</button>
                            <button class="na-dl-btn-sm primary" id="na-dl-start-btn"><i class="fa-solid fa-play"></i> Start Loop</button>
                        </div>
                    </div>
                `;

                document.body.appendChild(modalEl);

                modalEl.addEventListener('click', (e) => {
                    if (e.target === modalEl) closeSetupModal();
                });
                modalEl.querySelector('#na-dl-close-btn').addEventListener('click', closeSetupModal);
                modalEl.querySelector('#na-dl-cancel-btn').addEventListener('click', closeSetupModal);

                let pendingStart = null;
                const dupAlert = modalEl.querySelector('#na-dl-dup-alert');
                const dupMsg = modalEl.querySelector('#na-dl-dup-msg');
                const dupCancelBtn = modalEl.querySelector('#na-dl-dup-cancel-btn');
                const dupContinueBtn = modalEl.querySelector('#na-dl-dup-continue-btn');

                if (dupCancelBtn) {
                    dupCancelBtn.addEventListener('click', () => {
                        if (dupAlert) dupAlert.style.display = 'none';
                        pendingStart = null;
                    });
                }
                if (dupContinueBtn) {
                    dupContinueBtn.addEventListener('click', () => {
                        if (dupAlert) dupAlert.style.display = 'none';
                        if (pendingStart) {
                            const fn = pendingStart;
                            pendingStart = null;
                            fn();
                        }
                    });
                }

                const startAction = () => {
                    const novelName = modalEl.querySelector('#na-dl-novel-name').value.trim() || initialNovelName || 'Novel';
                    const startChap = modalEl.querySelector('#na-dl-start-chap').value.trim() || initialChapterNum || '1';
                    const rawCount = parseInt(modalEl.querySelector('#na-dl-loop-count').value, 10);
                    const loopCount = isNaN(rawCount) ? 100 : rawCount;
                    const countdownMs = Math.max(50, parseInt(modalEl.querySelector('#na-dl-countdown').value, 10) || 1000);
                    const fullFrozen = modalEl.querySelector('#na-dl-full-frozen').checked;
                    const contentSelector = modalEl.querySelector('#na-dl-content-selector').value.trim();
                    const nextSelector = modalEl.querySelector('#na-dl-next-selector').value.trim() || DEFAULT_SETTINGS.nextSelector;

                    const proceedWithStart = () => {
                        settings.loopCount = loopCount;
                        settings.countdownMs = countdownMs;
                        settings.fullFrozenHtml = fullFrozen;
                        settings.contentSelector = contentSelector;
                        settings.nextSelector = nextSelector;
                        saveSettings(settings);

                        // Remember user's choice for this particular tab
                        if (window.BrowserBridge && typeof window.BrowserBridge.saveTabDownloadInfo === 'function') {
                            try {
                                window.BrowserBridge.saveTabDownloadInfo(novelName, startChap);
                            } catch (e) {}
                        }

                        // Reset ran hashes and retries for new run
                        sessionStorage.removeItem(RAN_HASHES_KEY);
                        sessionStorage.setItem(SAME_HASH_RETRIES_KEY, '0');
                        sessionStorage.removeItem(SAVING_LOCK_KEY);

                        if (scheduledRunTimer) {
                            clearTimeout(scheduledRunTimer);
                            scheduledRunTimer = null;
                        }
                        if (navWatchdog) {
                            clearTimeout(navWatchdog);
                            navWatchdog = null;
                        }

                        sessionStorage.setItem(LAST_URL_KEY, window.location.href);
                        sessionStorage.setItem(ACTIVE_KEY, 'true');
                        const countLimit = loopCount > 0 ? loopCount : 999999;
                        sessionStorage.setItem(COUNT_KEY, countLimit);
                        sessionStorage.setItem(NOVEL_NAME_KEY, novelName);
                        sessionStorage.setItem(CHAP_NUM_KEY, startChap);
                        sessionStorage.setItem(FULL_FROZEN_KEY, fullFrozen ? 'true' : 'false');
                        sessionStorage.setItem(DELAY_MS_KEY, countdownMs);

                        closeSetupModal();
                        setButtonState('active');
                        notifyLoopState(true, countLimit, 'Starting loop...');
                        showToast('🚀 Starting auto-download loop (' + (loopCount > 0 ? loopCount + ' chapters' : 'infinite') + ')...', 'info', 3000);
                        runSaveSequence();
                    };

                    // Check if chapter already exists in library
                    const chInt = parseInt(startChap, 10);
                    let chapterExists = false;
                    if (!isNaN(chInt) && window.BrowserBridge && typeof window.BrowserBridge.checkChapterExists === 'function') {
                        try {
                            chapterExists = window.BrowserBridge.checkChapterExists(novelName, chInt);
                        } catch (e) {}
                    }

                    if (chapterExists) {
                        if (dupAlert && dupMsg) {
                            dupMsg.textContent = 'Chapter ' + startChap + ' of "' + novelName + '" already exists in your library. Do you want to download it anyways?';
                            dupAlert.style.display = 'block';
                            pendingStart = proceedWithStart;
                            return;
                        } else if (confirm('Chapter ' + startChap + ' of "' + novelName + '" already exists in your library. Download anyways?')) {
                            proceedWithStart();
                            return;
                        } else {
                            return;
                        }
                    }

                    proceedWithStart();
                };

                modalEl.querySelector('#na-dl-start-btn').addEventListener('click', startAction);
                const topStartBtn = modalEl.querySelector('#na-dl-top-start-btn');
                if (topStartBtn) {
                    topStartBtn.addEventListener('click', startAction);
                }

                modalEl.querySelectorAll('input').forEach(inp => {
                    inp.addEventListener('keydown', (e) => {
                        if (e.key === 'Enter') {
                            e.preventDefault();
                            startAction();
                        }
                    });
                });
            }

            function toggleAutoLoop() {
                const isActive = sessionStorage.getItem(ACTIVE_KEY) === 'true';
                if (isActive) {
                    stopAutorun('user');
                    return;
                }
                openSetupModal();
            }

            // Expose globally and register toolbar button
            window.__novelMultiDownloadToggle = toggleAutoLoop;

            if (typeof GM_registerToolbarButton === 'function') {
                GM_registerToolbarButton({
                    id: 'multi_download',
                    title: 'Batch Download Loop',
                    icon: 'CloudDownload',
                    onClick: function () {
                        toggleAutoLoop();
                    }
                });
            }

            function scheduleNavWatchdog(delayMs) {
                if (navWatchdog) clearTimeout(navWatchdog);
                navWatchdog = setTimeout(() => {
                    navWatchdog = null;
                    if (sessionStorage.getItem(ACTIVE_KEY) === 'true' && !isSaving && !activeTimer && sessionStorage.getItem(SAVING_LOCK_KEY) !== 'true') {
                        runSaveSequence();
                    }
                }, delayMs || 4000);
            }

            async function runSaveSequence() {
                if (sessionStorage.getItem(ACTIVE_KEY) !== 'true') return;
                if (isSaving || sessionStorage.getItem(SAVING_LOCK_KEY) === 'true') return;

                if (navWatchdog) {
                    clearTimeout(navWatchdog);
                    navWatchdog = null;
                }
                if (activeTimer) {
                    clearInterval(activeTimer);
                    activeTimer = null;
                }
                if (scheduledRunTimer) {
                    clearTimeout(scheduledRunTimer);
                    scheduledRunTimer = null;
                }

                // 1. Compute hash of current page content
                const bodyContent = getPageContentForHash();
                const currentHash = md5(bodyContent);
                let ranHashes = getRanHashes();

                // 2. Compare current page hash with hashes from the current run
                if (ranHashes.includes(currentHash)) {
                    let retries = parseInt(sessionStorage.getItem(SAME_HASH_RETRIES_KEY) || '0', 10);
                    if (retries < 3) {
                        retries++;
                        sessionStorage.setItem(SAME_HASH_RETRIES_KEY, String(retries));
                        showToast('⏳ Page content unchanged. Pressing Next again and waiting 5s (retry ' + retries + '/3)...', 'warn', 5000);
                        notifyLoopState(true, parseInt(sessionStorage.getItem(COUNT_KEY), 10) || 0, 'Page unchanged. Waiting 5s (retry ' + retries + '/3)...');

                        // Press next again
                        const navOk = triggerNextNavigation();
                        if (!navOk) {
                            stopAutorun('no_next_btn');
                            return;
                        }

                        // Wait for 5 seconds with live countdown
                        let waitRemaining = 5.0;
                        setButtonState('countdown', '5.0');
                        activeTimer = setInterval(() => {
                            if (sessionStorage.getItem(ACTIVE_KEY) !== 'true') {
                                clearInterval(activeTimer);
                                activeTimer = null;
                                return;
                            }
                            waitRemaining = Math.max(0, waitRemaining - 0.1);
                            setButtonState('countdown', waitRemaining.toFixed(1));
                            if (waitRemaining <= 0) {
                                clearInterval(activeTimer);
                                activeTimer = null;
                                runSaveSequence();
                            }
                        }, 100);
                        return;
                    } else {
                        // Stopped because page content remained unchanged after 3 retries
                        stopAutorun('unchanged_retries');
                        return;
                    }
                }

                // New page content detected! Reset retries and record hash
                sessionStorage.setItem(SAME_HASH_RETRIES_KEY, '0');
                ranHashes.push(currentHash);
                saveRanHashes(ranHashes);

                isSaving = true;
                sessionStorage.setItem(SAVING_LOCK_KEY, 'true');
                setButtonState('working');

                try {
                    sessionStorage.setItem(LAST_URL_KEY, window.location.href);
                    await executeSaveHTML();

                    const curChap = sessionStorage.getItem(CHAP_NUM_KEY);
                    if (curChap) {
                        sessionStorage.setItem(CHAP_NUM_KEY, getNextChapterNumber(curChap));
                    }

                    const sessionNovel = sessionStorage.getItem(NOVEL_NAME_KEY);
                    const detected = detectNovelInfo();
                    const novelTitle = sessionNovel || detected.novelName || 'Novel';
                    const savedChapNum = curChap || detected.chapterNum || '1';

                    // Toast info: page downloaded
                    showToast('📥 Downloaded: ' + novelTitle + ' Chapter ' + savedChapNum, 'success', 3500);

                    const currentCount = parseInt(sessionStorage.getItem(COUNT_KEY), 10) || 1;
                    const remaining = currentCount - 1;
                    sessionStorage.setItem(COUNT_KEY, remaining);
                    notifyLoopState(true, remaining, 'Waiting for next chapter...');

                    if (remaining <= 0) {
                        stopAutorun('completed');
                        return;
                    }

                    // Respect configured delay between chapters
                    const delayMs = Math.max(50, parseInt(sessionStorage.getItem(DELAY_MS_KEY) || settings.countdownMs, 10) || 1000);
                    let remainingMs = delayMs;
                    const tick = 100;
                    if (activeTimer) clearInterval(activeTimer);
                    activeTimer = setInterval(() => {
                        if (sessionStorage.getItem(ACTIVE_KEY) !== 'true') {
                            clearInterval(activeTimer);
                            activeTimer = null;
                            return;
                        }
                        remainingMs -= tick;
                        setButtonState('countdown', Math.max(0, Math.ceil(remainingMs / 100) / 10).toFixed(1));
                        if (remainingMs <= 0) {
                            clearInterval(activeTimer);
                            activeTimer = null;
                            setButtonState('working');
                            const navOk = triggerNextNavigation();
                            if (!navOk) {
                                stopAutorun('no_next_btn');
                            } else {
                                scheduleNavWatchdog(4000);
                            }
                        }
                    }, tick);
                } catch (err) {
                    console.error('Error during auto-save cycle', err);
                    stopAutorun('error');
                } finally {
                    isSaving = false;
                    sessionStorage.removeItem(SAVING_LOCK_KEY);
                }
            }

            // =============================================================
            // 5. INITIAL STATE HYDRATION & SPA NAVIGATION LISTENER
            // =============================================================
            if (sessionStorage.getItem(ACTIVE_KEY) === 'true') {
                setButtonState('active');
                const lastUrl = sessionStorage.getItem(LAST_URL_KEY);
                const currentUrl = window.location.href;
                if (!lastUrl || lastUrl !== currentUrl) {
                    sessionStorage.setItem(LAST_URL_KEY, currentUrl);
                    if (scheduledRunTimer) clearTimeout(scheduledRunTimer);
                    scheduledRunTimer = setTimeout(() => {
                        scheduledRunTimer = null;
                        if (sessionStorage.getItem(ACTIVE_KEY) === 'true' && !isSaving && sessionStorage.getItem(SAVING_LOCK_KEY) !== 'true') {
                            runSaveSequence();
                        }
                    }, 1200);
                }
            }

            setInterval(() => {
                const isActive = sessionStorage.getItem(ACTIVE_KEY) === 'true';
                if (!isActive) return;
                if (!loopBtn.classList.contains('active') && !loopBtn.classList.contains('working')) {
                    setButtonState('active');
                }
                const lastUrl = sessionStorage.getItem(LAST_URL_KEY);
                const currentUrl = window.location.href;
                if (lastUrl !== currentUrl) {
                    sessionStorage.setItem(LAST_URL_KEY, currentUrl);
                    if (navWatchdog) {
                        clearTimeout(navWatchdog);
                        navWatchdog = null;
                    }
                    if (activeTimer) {
                        clearInterval(activeTimer);
                        activeTimer = null;
                    }
                    if (scheduledRunTimer) {
                        clearTimeout(scheduledRunTimer);
                        scheduledRunTimer = null;
                    }
                    scheduledRunTimer = setTimeout(() => {
                        scheduledRunTimer = null;
                        if (sessionStorage.getItem(ACTIVE_KEY) === 'true' && !isSaving && sessionStorage.getItem(SAVING_LOCK_KEY) !== 'true') {
                            runSaveSequence();
                        }
                    }, 1200);
                }
            }, 500);

        })();
    """.trimIndent()
}
