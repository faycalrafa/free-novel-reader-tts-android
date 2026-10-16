# NovelArrow Userscript API & Development Guide

Welcome to the **NovelArrow Userscript Engine**! Userscripts allow you to customize, automate, style, and enhance novel reading websites directly in NovelArrow Reader's integrated browser.

---

## 1. Script Metadata Header Block

Every userscript must begin with a metadata block enclosed between `// ==UserScript==` and `// ==/UserScript==`. NovelArrow parses these directives to configure when, where, and how your script runs.

```javascript
// ==UserScript==
// @name         My Novel Helper
// @version      1.0.0
// @description  Custom userscript for enhanced reading
// @author       NovelArrow Reader
// @match        *://novelbin.me/*
// @match        *://*.novelfull.com/*
// @run-at       document-end
// @grant        GM_notification
// ==/UserScript==
```

### Supported Header Keys

| Directive | Description | Example |
|---|---|---|
| `@name` | Name of the userscript displayed in the Extensions Manager. | `// @name Novel Cleaner` |
| `@version` | Script version number (semver). | `// @version 1.2.0` |
| `@description` | Short explanation of what the script accomplishes. | `// @description Removes sidebars and ads.` |
| `@author` | Author or creator name. | `// @author JohnDoe` |
| `@match` | URL pattern where the script should execute. Supports wildcards. | `// @match *://*.novelbin.*/*` |
| `@include` | Alternative pattern syntax, treated as a match pattern. | `// @include https://wuxiaworld.com/*` |
| `@run-at` | Injection timing: `document-start`, `document-end`, or `document-idle`. | `// @run-at document-end` |
| `@grant` | Required API permissions (e.g. `GM_notification`). | `// @grant GM_notification` |

### URL Match Pattern Syntax
- `*://*/*` : Matches all HTTP and HTTPS websites.
- `*://*.novelbin.*/*` : Matches `novelbin.com`, `novelbin.me`, `novelbin.net`, etc.
- `https://site.com/chapter-*` : Matches chapters on `site.com`.

---

## 2. Supported APIs & Bridge Methods

### `GM_notification(details, ondone)`
Displays a native Android Toast notification on the user's screen.
- **String syntax**:
  ```javascript
  GM_notification("Chapter 42 cleaned and ready!");
  ```
- **Object syntax**:
  ```javascript
  GM_notification({
      text: "Next chapter detected automatically.",
      title: "NovelArrow"
  });
  ```

---

### `window.BrowserBridge` / `window.AndroidBridge`
NovelArrow exposes native JavaScript interfaces for deep reader integration:

- `window.BrowserBridge.gmNotification(message: string)`
  Directly triggers a native Android Toast.

- `window.BrowserBridge.onParagraphClicked(index: number)`
  Signals the native TTS engine to seek and begin playback from paragraph `index`.

- `window.BrowserBridge.onScrolledToBottom()`
  Signals that the user or page has reached the end of the chapter, triggering auto-read or next chapter transition.

---

## 3. Storage & Physical File Location

Unlike other browsers that hide scripts in internal databases, NovelArrow stores your physical script code directly in your public **Downloads** directory:

```text
/storage/emulated/0/Download/NovelArrow_Scripts/
  ├── NovelBin_Cleaner.user.js
  ├── Auto_Scroller.user.js
  └── Font_Customizer.user.js
```

- You can edit scripts inside the app using the built-in **Monaco Editor**, or use an external editor on your phone/PC.
- Any `.user.js` files dropped into `Download/NovelArrow_Scripts/` will be automatically detected!
- Deleting a script in the Userscript Manager will delete both the database entry and the physical file.

---

## 4. Practical Script Examples

### Example 1: Remove Ads and Sidebars
```javascript
// ==UserScript==
// @name         Remove Novel Sidebars & Ads
// @version      1.0.0
// @match        *://*/*
// @run-at       document-end
// @grant        GM_notification
// ==/UserScript==

(function() {
    'use strict';
    const removeSelectors = [
        '.ads', '.ad-container', '#sidebar', 'aside',
        '.header-advert', '.footer-advert', 'iframe'
    ];
    let removedCount = 0;
    removeSelectors.forEach(sel => {
        document.querySelectorAll(sel).forEach(el => {
            el.remove();
            removedCount++;
        });
    });
    if (removedCount > 0) {
        GM_notification('Cleaned ' + removedCount + ' ad elements.');
    }
})();
```

### Example 2: Custom Reading Font & Comfortable Sepia Theme
```javascript
// ==UserScript==
// @name         Sepia Reader Theme
// @version      1.0.0
// @match        *://*/*
// @run-at       document-end
// ==/UserScript==

(function() {
    'use strict';
    const style = document.createElement('style');
    style.textContent = `
        body, #chapter-content, .entry-content, article {
            background-color: #fbf0d9 !important;
            color: #5f4b32 !important;
            font-family: 'Georgia', serif !important;
            font-size: 19px !important;
            line-height: 1.8 !important;
        }
    `;
    document.head.appendChild(style);
})();
```

### Example 3: Automatic Next Chapter on Page Key Press
```javascript
// ==UserScript==
// @name         Arrow Key Chapter Navigation
// @version      1.0.0
// @match        *://*/*
// @run-at       document-end
// ==/UserScript==

(function() {
    'use strict';
    window.addEventListener('keydown', function(e) {
        if (e.key === 'ArrowRight') {
            const nextBtn = document.querySelector('a.next, .btn-next, #next_chap, a[rel="next"]');
            if (nextBtn) nextBtn.click();
        } else if (e.key === 'ArrowLeft') {
            const prevBtn = document.querySelector('a.prev, .btn-prev, #prev_chap, a[rel="prev"]');
            if (prevBtn) prevBtn.click();
        }
    });
})();
```
