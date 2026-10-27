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
        // @name         Multi-Download
        // @version      3.0.0
        // @description  Batch download loop for web novel chapters with centered progress dialog, clean text extraction, and auto-navigation.
        // @match        *://*/*
        // @match        file://*/*
        // @run-at       document-end
        // @grant        GM_registerToolbarButton
        // @grant        GM_notification
        // @grant        GM_setValue
        // @grant        GM_getValue
        // ==/UserScript==

        (function() {
            'use strict';

            var ACTIVE_KEY = 'novel_batch_dl_active';
            var NOVEL_KEY = 'novel_batch_dl_novel';
            var CHAPTER_KEY = 'novel_batch_dl_chapter';
            var REMAINING_KEY = 'novel_batch_dl_remaining';
            var TOTAL_KEY = 'novel_batch_dl_total';
            var DELAY_KEY = 'novel_batch_dl_delay';
            var LAST_URL_KEY = 'novel_batch_dl_last_url';

            var DEFAULT_IGNORE = [
                'nav', 'footer', 'header', 'aside', 'button', 'a',
                '[role="navigation"]', '.ads', '.ad', '.advertisement',
                'script', 'style', 'noscript', 'iframe',
                'form', 'input', 'textarea', 'select',
                '#custom-floating-bar', '#na-root', '#na-floating-next',
                '#na-player-bar', '#na-modal-backdrop-el',
                '#nd-modal-overlay', '#nd-countdown-pill',
                '.comments', '#comments', '.sidebar', '#sidebar',
                '.na-tts-highlight'
            ];

            function isIgnored(el) {
                if (!el) return true;
                try {
                    for (var i = 0; i < DEFAULT_IGNORE.length; i++) {
                        var sel = DEFAULT_IGNORE[i];
                        if (el.matches && (el.matches(sel) || el.closest(sel))) return true;
                    }
                } catch(e) {}
                return false;
            }

            function detectNovel() {
                var title = '';
                var chNum = 1;
                var pathname = window.location.pathname || '';
                var pageTitle = document.title || '';

                var chMatch = pathname.match(/(?:chapter|chap|ch|c)[-_]?(\d+)/i) || pathname.match(/(\d+)(?:\.html?)?${'$'}/i);
                if (chMatch) {
                    chNum = parseInt(chMatch[1], 10) || 1;
                }

                var pathSegments = pathname.split('/').filter(function(s) {
                    return s && !/^(?:chapter|chap|ch|c|\d+)/i.test(s) && ['novel', 'novels', 'read', 'book', 'books', 'chapter', 'chapters', 'html', 'index'].indexOf(s.toLowerCase()) === -1;
                });
                if (pathSegments.length > 0) {
                    var rawSlug = pathSegments[pathSegments.length - 1];
                    title = rawSlug.replace(/\.html?${'$'}/i, '').replace(/[-_]+/g, ' ').replace(/\b\w/g, function(c) { return c.toUpperCase(); }).trim();
                }

                if (!title && pageTitle) {
                    var titleMatch = pageTitle.match(/^(.*?)\s*[-–—|:]\s*Chapter\s+(\d+)/i) || pageTitle.match(/Chapter\s+(\d+).*?[-–—|]\s*(.*?)${'$'}/i);
                    if (titleMatch) {
                        var cand = titleMatch[1].trim();
                        if (cand.length > 2 && !/^\d+${'$'}/.test(cand)) {
                            title = cand;
                        }
                        if (chNum === 1 && titleMatch[2]) {
                            chNum = parseInt(titleMatch[2], 10) || 1;
                        }
                    }
                    if (!title) {
                        var cleanTitle = pageTitle.replace(/[-–—|:].*${'$'}/i, '').trim();
                        if (cleanTitle.length > 2) title = cleanTitle;
                    }
                }

                return { title: title || 'Web Novel', chapter: chNum };
            }

            function extractFilteredText() {
                var candidates = [
                    '#chapter-content', '.chapter-content', '#readcontent', '.read-content',
                    '.entry-content', '.text-content', '.reading-content', 'article',
                    '.content', '#chapter_content', '.novel-content', 'main', '#read-area'
                ];
                var container = null;
                for (var i = 0; i < candidates.length; i++) {
                    var el = document.querySelector(candidates[i]);
                    if (el && el.querySelectorAll('p').length >= 2) {
                        container = el;
                        break;
                    }
                }
                if (!container) container = document.body;

                var allNodes = Array.from(container.querySelectorAll('p, div, h1, h2, h3, h4, h5, h6, blockquote'));
                var paras = [];
                for (var j = 0; j < allNodes.length; j++) {
                    var node = allNodes[j];
                    if (isIgnored(node)) continue;
                    // Skip parent containers that contain nested paragraphs or divs
                    if (node.tagName.toLowerCase() === 'div' && node.querySelector('p, div')) continue;
                    var tag = node.tagName.toLowerCase();
                    var cs = window.getComputedStyle(node);
                    if (cs.display === 'none' || cs.visibility === 'hidden' || parseFloat(cs.opacity) === 0) continue;
                    var rect = node.getBoundingClientRect();
                    if (rect.width === 0 && rect.height === 0) continue;

                    var text = (node.textContent || '').replace(/\s+/g, ' ').trim();
                    if (text.length >= 2 && !/^(prev|next|table of contents|index|share|bookmark|chapter list)/i.test(text)) {
                        paras.push({ text: text, isHeading: tag.indexOf('h') === 0 });
                    }
                }
                return paras;
            }

            function buildCleanChapterHtml(novelTitle, chapterTitle, chNum, paragraphs) {
                var parasHtml = paragraphs.map(function(p) {
                    if (p.isHeading) {
                        return '    <h2>' + escapeHtml(p.text) + '</h2>';
                    }
                    return '    <p>' + escapeHtml(p.text) + '</p>';
                }).join('\n');

                return '<!DOCTYPE html>\n<html lang="en">\n<head>\n' +
                    '  <meta charset="utf-8">\n' +
                    '  <meta name="viewport" content="width=device-width, initial-scale=1.0">\n' +
                    '  <title>' + escapeHtml(chapterTitle) + '</title>\n' +
                    '  <style>\n' +
                    '    body { font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; line-height: 1.8; padding: 24px 16px; max-width: 800px; margin: 0 auto; color: #1e293b; background: #ffffff; }\n' +
                    '    h1 { font-size: 1.55rem; font-weight: 700; margin: 0 0 0.5em 0; color: #0f172a; line-height: 1.35; }\n' +
                    '    h2 { font-size: 1.35rem; font-weight: 700; margin: 1.2em 0 0.6em 0; color: #0f172a; line-height: 1.35; }\n' +
                    '    .chapter-meta { font-size: 0.85rem; color: #64748b; margin-bottom: 1.8em; border-bottom: 1px solid #e2e8f0; padding-bottom: 0.8em; }\n' +
                    '    p { margin: 0 0 1.2em 0; text-indent: 1.25em; font-size: 1.05rem; }\n' +
                    '  </style>\n' +
                    '</head>\n<body>\n' +
                    '  <h1>' + escapeHtml(chapterTitle) + '</h1>\n' +
                    '  <div class="chapter-meta">Novel: ' + escapeHtml(novelTitle) + ' | Chapter: ' + chNum + '</div>\n' +
                    '  <div id="chapter-content">\n' +
                    parasHtml + '\n' +
                    '  </div>\n' +
                    '</body>\n</html>';
            }

            function escapeHtml(s) {
                return (s || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
            }

            function saveChapter(novelTitle, chapterTitle, chNum, cleanHtml) {
                // Primary path: save directly through BookReader safe Android bridge
                if (window.BrowserBridge && typeof window.BrowserBridge.saveDownloadedChapter === 'function') {
                    window.BrowserBridge.saveDownloadedChapter(novelTitle, chapterTitle, chNum, cleanHtml);
                    return;
                }
                if (window.AndroidBridge && typeof window.AndroidBridge.saveDownloadedChapter === 'function') {
                    window.AndroidBridge.saveDownloadedChapter(novelTitle, chapterTitle, chNum, cleanHtml);
                    return;
                }

                // Fallback for standalone desktop userscript runners
                try {
                    var safeNovel = novelTitle.replace(/[\\/:*?"<>|\r\n]+/g, '_').trim() || 'Web Novel';
                    var safeChap = String(chNum).padStart(4, '0') + '_' + chapterTitle.replace(/[\\/:*?"<>|\r\n]+/g, '_').trim();
                    var fileName = safeNovel + '/' + safeChap + '.html';
                    var blob = new Blob([cleanHtml], { type: 'text/html;charset=utf-8' });
                    var url = URL.createObjectURL(blob);
                    var a = document.createElement('a');
                    a.href = url;
                    a.download = fileName;
                    document.body.appendChild(a);
                    a.click();
                    setTimeout(function() {
                        document.body.removeChild(a);
                        URL.revokeObjectURL(url);
                    }, 500);
                } catch(e) {
                    console.error('[Multi-Download] Fallback blob download failed:', e);
                }
            }

            function isCandidateValidNext(el) {
                if (!el) return false;
                var txt = (el.innerText || el.textContent || el.title || el.getAttribute('aria-label') || '').trim().toLowerCase();
                var href = (el.getAttribute('href') || '').trim().toLowerCase();

                // Negative filter: Never match Home, Index, TOC, Prev, or novel root pages
                if (/home|index|toc|contents|catalog|chapter\s*list|all\s*chapters|prev|previous|back/i.test(txt)) return false;
                if (href === '#' || href === '' || href.indexOf('javascript:') === 0) return false;
                if (href === '/' || href === location.pathname.toLowerCase() || href === location.href.toLowerCase()) return false;
                var hasChapterIdentifier = /chapter|chap|ch\d+|\d+/i.test(href);
                if (!hasChapterIdentifier && (/\/novel\/[^\/]+\/?${'$'}/i.test(href) || /\/book\/[^\/]+\/?${'$'}/i.test(href))) return false;

                return true;
            }

            function resolveNextElement(rawEl) {
                if (!rawEl) return null;
                var tag = (rawEl.tagName || '').toLowerCase();
                if (tag === 'a' || tag === 'button') {
                    if (isCandidateValidNext(rawEl)) return rawEl;
                    return null;
                }
                var childLinks = Array.from(rawEl.querySelectorAll('a, button'));
                for (var i = 0; i < childLinks.length; i++) {
                    var link = childLinks[i];
                    if (isCandidateValidNext(link)) {
                        var txt = (link.innerText || link.textContent || link.title || link.getAttribute('aria-label') || '').trim().toLowerCase();
                        var rel = (link.getAttribute('rel') || '').toLowerCase();
                        if (rel === 'next' || /^(next(\s*(chapter|chap|page|part)?)?|\s*»|\s*>)\s*${'$'}/i.test(txt) || txt.indexOf('next') !== -1) {
                            return link;
                        }
                    }
                }
                return null;
            }

            function getNextChapterElement() {
                var selectors = [
                    'a[rel="next"]',
                    'button[title*="Next Chapter" i]', 'a[title*="Next Chapter" i]',
                    'button[title*="Next chapter" i]', 'a[title*="Next chapter" i]',
                    'button[title="Next" i]', 'a[title="Next" i]',
                    'a.next_page', 'a#next_chapter', 'a.next-chapter', 'a.btn-next', 'a.next',
                    '.nav-next a', '.chapter-next a', '#next-chapter a', '.next a', '.nav-links .nav-next a',
                    '#next_chap', '.next-chap', '.next-chapter', '.btn-next'
                ];
                for (var i = 0; i < selectors.length; i++) {
                    try {
                        var el = document.querySelector(selectors[i]);
                        var resolved = resolveNextElement(el);
                        if (resolved && resolved.offsetParent !== null) return resolved;
                    } catch(e) {}
                }

                try {
                    var clickables = Array.from(document.querySelectorAll('a, button, span[role="button"], div[role="button"]'));
                    for (var j = 0; j < clickables.length; j++) {
                        var item = clickables[j];
                        if (isCandidateValidNext(item)) {
                            var txt = (item.innerText || item.title || item.textContent || item.getAttribute('aria-label') || '').trim().toLowerCase();
                            if (txt === 'next chapter' || txt === 'next >' || txt === 'next »' || txt === 'next' || txt.indexOf('next chapter') !== -1 || /^(next(\s*(chapter|chap|page|part)?)?|\s*»|\s*>)\s*${'$'}/i.test(txt)) {
                                if (item.offsetParent !== null) return item;
                            }
                        }
                    }
                } catch(e) {}
                return null;
            }

            function incrementUrlNext() {
                try {
                    var urlObj = new URL(window.location.href);
                    var pathname = urlObj.pathname;
                    var chapRegex = /(chapter[-_]?|chap[-_]?|ch[-_]?|c[-_]?)(\d+)(?=(\.html?|\/|${'$'}))/gi;
                    var chapMatches = Array.from(pathname.matchAll(chapRegex));
                    var match = chapMatches.length > 0 ? chapMatches[chapMatches.length - 1] : null;

                    if (!match) {
                        var numRegex = /(\/|-|_)?(\d+)(?=(\.html?|\/|${'$'}))/gi;
                        var numMatches = Array.from(pathname.matchAll(numRegex));
                        if (numMatches.length > 0) {
                            match = numMatches[numMatches.length - 1];
                        }
                    }

                    if (match && match.index !== undefined) {
                        var prefix = match[1] || '';
                        var digits = match[2];
                        var len = digits.length;
                        var nextNum = parseInt(digits, 10) + 1;
                        var nextStr = nextNum.toString();
                        while (nextStr.length < len) nextStr = '0' + nextStr;
                        urlObj.pathname = pathname.substring(0, match.index) + prefix + nextStr + pathname.substring(match.index + match[0].length);
                        window.location.href = urlObj.href;
                        return true;
                    }
                } catch(e) {}
                return false;
            }

            function showCountdownPill(novelTitle, currentCh, remaining, totalSecs) {
                var existing = document.getElementById('nd-countdown-pill');
                if (existing) existing.remove();

                var total = parseInt(sessionStorage.getItem(TOTAL_KEY), 10) || (remaining + 1);
                var completed = Math.max(0, total - remaining);
                var percent = total > 0 ? Math.min(100, Math.round((completed / total) * 100)) : 0;

                var pill = document.createElement('div');
                pill.id = 'nd-countdown-pill';
                pill.style.cssText = 'position:fixed;bottom:24px;left:50%;transform:translateX(-50%);background:#1e293b;color:#f8fafc;padding:12px 20px;border-radius:30px;box-shadow:0 10px 25px rgba(0,0,0,0.45);display:flex;align-items:center;gap:14px;z-index:2147483647;font-family:system-ui,sans-serif;font-size:0.9rem;font-weight:600;border:1px solid #334155;';

                pill.innerHTML = 
                    '<span>📥 ' + escapeHtml(novelTitle) + ' #' + currentCh + ' (' + percent + '%)</span>' +
                    '<span id="nd-timer-val" style="background:#0f172a;padding:4px 10px;border-radius:12px;color:#38bdf8;">' + totalSecs.toFixed(1) + 's</span>' +
                    '<button id="nd-stop-btn" style="background:#ef4444;color:#fff;border:none;padding:5px 12px;border-radius:14px;font-weight:700;font-size:0.8rem;cursor:pointer;">Stop</button>';

                document.body.appendChild(pill);

                var stopBtn = document.getElementById('nd-stop-btn');
                if (stopBtn) {
                    stopBtn.onclick = function() {
                        stopBatchLoop();
                    };
                }
            }

            function stopBatchLoop() {
                sessionStorage.removeItem(ACTIVE_KEY);
                sessionStorage.removeItem(NOVEL_KEY);
                sessionStorage.removeItem(CHAPTER_KEY);
                sessionStorage.removeItem(REMAINING_KEY);
                sessionStorage.removeItem(TOTAL_KEY);
                sessionStorage.removeItem(DELAY_KEY);
                sessionStorage.removeItem(LAST_URL_KEY);
                if (countdownInterval) clearInterval(countdownInterval);
                var container = document.getElementById('nd-countdown-pill');
                if (container) container.remove();
                isStepRunning = false;
            }

            var countdownInterval = null;
            var isStepRunning = false;

            function runBatchStep() {
                if (sessionStorage.getItem(ACTIVE_KEY) !== 'true') return;
                if (isStepRunning) return;
                isStepRunning = true;

                var novelTitle = sessionStorage.getItem(NOVEL_KEY) || 'Web Novel';
                var currentCh = parseInt(sessionStorage.getItem(CHAPTER_KEY), 10) || 1;
                var remaining = parseInt(sessionStorage.getItem(REMAINING_KEY), 10) || 1;
                var delayMs = Math.max(300, parseInt(sessionStorage.getItem(DELAY_KEY), 10) || 1000);

                var paras = extractFilteredText();
                var chapterTitle = novelTitle + ' Chapter ' + currentCh;
                var cleanHtml = buildCleanChapterHtml(novelTitle, chapterTitle, currentCh, paras);

                saveChapter(novelTitle, chapterTitle, currentCh, cleanHtml);

                remaining--;
                currentCh++;
                sessionStorage.setItem(REMAINING_KEY, remaining.toString());
                sessionStorage.setItem(CHAPTER_KEY, currentCh.toString());
                sessionStorage.setItem(LAST_URL_KEY, window.location.href);

                if (remaining <= 0) {
                    stopBatchLoop();
                    if (typeof GM_notification === 'function') {
                        GM_notification('Batch download complete for ' + novelTitle + '!');
                    }
                    return;
                }

                var timeLeftMs = delayMs;
                var tickMs = 100;
                showCountdownPill(novelTitle, currentCh, remaining, timeLeftMs / 1000);

                countdownInterval = setInterval(function() {
                    if (sessionStorage.getItem(ACTIVE_KEY) !== 'true') {
                        clearInterval(countdownInterval);
                        isStepRunning = false;
                        return;
                    }
                    timeLeftMs -= tickMs;
                    var timerVal = document.getElementById('nd-timer-val');
                    if (timerVal) timerVal.textContent = Math.max(0, timeLeftMs / 1000).toFixed(1) + 's';

                    if (timeLeftMs <= 0) {
                        clearInterval(countdownInterval);
                        var nextEl = getNextChapterElement();
                        var isLocalFile = (location.protocol === 'file:' || /\.html?${'$'}/i.test(location.pathname));
                        if (nextEl) {
                            var oldUrl = window.location.href;
                            var aTag = (nextEl.tagName && nextEl.tagName.toLowerCase() === 'a') ? nextEl : nextEl.closest('a');
                            var fullHref = (aTag && aTag.href && aTag.href.indexOf('http') === 0 && !aTag.href.startsWith('javascript:')) ? aTag.href : '';
                            if (fullHref && fullHref !== oldUrl) {
                                window.location.href = fullHref;
                            } else {
                                try { nextEl.click(); } catch(e) {}
                                setTimeout(function() {
                                    if (window.location.href === oldUrl && fullHref && fullHref !== oldUrl) {
                                        window.location.href = fullHref;
                                    }
                                }, 1200);
                            }
                        } else if (isLocalFile || incrementUrlNext()) {
                            // URL digit increment handled
                        } else {
                            stopBatchLoop();
                            alert('Next chapter button not found. Stopped batch download.');
                        }
                        isStepRunning = false;
                    }
                }, tickMs);
            }

            function showDialog() {
                if (sessionStorage.getItem(ACTIVE_KEY) === 'true') {
                    if (confirm('Batch download is currently running. Do you want to stop it?')) {
                        stopBatchLoop();
                    }
                    return;
                }

                var existing = document.getElementById('nd-modal-overlay');
                if (existing) existing.remove();

                var detected = detectNovel();
                var overlay = document.createElement('div');
                overlay.id = 'nd-modal-overlay';
                overlay.style.cssText = 'position:fixed;top:0;left:0;width:100vw;height:100vh;background:rgba(0,0,0,0.65);display:flex;align-items:center;justify-content:center;z-index:2147483647;backdrop-filter:blur(3px);font-family:system-ui,sans-serif;box-sizing:border-box;';

                overlay.innerHTML = 
                    '<div style="background:#1e293b;color:#f8fafc;width:92%;max-width:420px;border-radius:18px;padding:24px;box-shadow:0 16px 40px rgba(0,0,0,0.45);border:1px solid #334155;box-sizing:border-box;">' +
                    '  <h2 style="margin:0 0 6px 0;font-size:1.25rem;color:#f8fafc;display:flex;align-items:center;gap:8px;">📥 Batch Download Novel</h2>' +
                    '  <p style="margin:0 0 16px 0;font-size:0.82rem;color:#94a3b8;line-height:1.4;">Extracts clean filtered text into readable HTML and auto-navigates.</p>' +
                    '  <div style="margin-bottom:12px;">' +
                    '    <label style="display:block;font-size:0.75rem;font-weight:700;color:#cbd5e1;margin-bottom:4px;text-transform:uppercase;">Novel Name</label>' +
                    '    <input id="nd-input-title" type="text" style="width:100%;padding:10px 12px;border-radius:10px;border:1px solid #475569;background:#0f172a;color:#f8fafc;font-size:0.95rem;box-sizing:border-box;" value="' + detected.title.replace(/"/g, '&quot;') + '" />' +
                    '  </div>' +
                    '  <div style="margin-bottom:12px;">' +
                    '    <label style="display:block;font-size:0.75rem;font-weight:700;color:#cbd5e1;margin-bottom:4px;text-transform:uppercase;">Starting Chapter #</label>' +
                    '    <input id="nd-input-ch" type="number" min="1" style="width:100%;padding:10px 12px;border-radius:10px;border:1px solid #475569;background:#0f172a;color:#f8fafc;font-size:0.95rem;box-sizing:border-box;" value="' + detected.chapter + '" />' +
                    '  </div>' +
                    '  <div style="margin-bottom:12px;">' +
                    '    <label style="display:block;font-size:0.75rem;font-weight:700;color:#cbd5e1;margin-bottom:4px;text-transform:uppercase;">Chapters Quantity</label>' +
                    '    <input id="nd-input-qty" type="number" min="1" style="width:100%;padding:10px 12px;border-radius:10px;border:1px solid #475569;background:#0f172a;color:#f8fafc;font-size:0.95rem;box-sizing:border-box;" value="20" />' +
                    '  </div>' +
                    '  <div style="margin-bottom:16px;">' +
                    '    <label style="display:block;font-size:0.75rem;font-weight:700;color:#cbd5e1;margin-bottom:4px;text-transform:uppercase;">Delay Between Chapters (seconds)</label>' +
                    '    <input id="nd-input-delay" type="number" step="0.5" min="0.5" style="width:100%;padding:10px 12px;border-radius:10px;border:1px solid #475569;background:#0f172a;color:#f8fafc;font-size:0.95rem;box-sizing:border-box;" value="1.0" />' +
                    '  </div>' +
                    '  <div style="display:flex;justify-content:flex-end;gap:10px;margin-top:20px;">' +
                    '    <button id="nd-btn-cancel" style="background:transparent;color:#94a3b8;border:none;padding:10px 16px;border-radius:10px;font-size:0.9rem;font-weight:600;cursor:pointer;">Cancel</button>' +
                    '    <button id="nd-btn-start" style="background:#2563eb;color:#fff;border:none;padding:10px 18px;border-radius:10px;font-size:0.9rem;font-weight:600;cursor:pointer;">Start Batch Download</button>' +
                    '  </div>' +
                    '</div>';

                document.body.appendChild(overlay);

                document.getElementById('nd-btn-cancel').onclick = function() {
                    overlay.remove();
                };

                document.getElementById('nd-btn-start').onclick = function() {
                    var titleInput = document.getElementById('nd-input-title').value.trim() || 'Web Novel';
                    var chInput = parseInt(document.getElementById('nd-input-ch').value, 10) || 1;
                    var qtyInput = parseInt(document.getElementById('nd-input-qty').value, 10) || 20;
                    var delayInput = parseFloat(document.getElementById('nd-input-delay').value) || 1.0;

                    overlay.remove();

                    sessionStorage.setItem(ACTIVE_KEY, 'true');
                    sessionStorage.setItem(NOVEL_KEY, titleInput);
                    sessionStorage.setItem(CHAPTER_KEY, chInput.toString());
                    sessionStorage.setItem(REMAINING_KEY, qtyInput.toString());
                    sessionStorage.setItem(TOTAL_KEY, qtyInput.toString());
                    sessionStorage.setItem(DELAY_KEY, Math.round(delayInput * 1000).toString());
                    sessionStorage.setItem(LAST_URL_KEY, window.location.href);

                    runBatchStep();
                };
            }

            if (typeof GM_registerToolbarButton === 'function') {
                GM_registerToolbarButton({
                    id: 'multi_download',
                    title: 'Batch Download Loop',
                    icon: 'CloudDownload',
                    onClick: function() {
                        showDialog();
                    }
                });
            }

            // SPA URL change watcher
            var lastProcessedUrl = sessionStorage.getItem(LAST_URL_KEY) || window.location.href;
            setInterval(function() {
                var isActive = sessionStorage.getItem(ACTIVE_KEY) === 'true';
                if (!isActive) return;
                var currentUrl = window.location.href;
                if (currentUrl !== lastProcessedUrl) {
                    lastProcessedUrl = currentUrl;
                    sessionStorage.setItem(LAST_URL_KEY, currentUrl);
                    setTimeout(function() {
                        runBatchStep();
                    }, 1500);
                }
            }, 500);

            // Auto-resume across page navigations if loop active
            if (sessionStorage.getItem(ACTIVE_KEY) === 'true') {
                setTimeout(function() {
                    runBatchStep();
                }, 1000);
            }
        })();
    """.trimIndent()
}
