package com.novelarrow.reader.data.parser

internal object ContentFilterRules {

    val IGNORE_SELECTORS = listOf(
        // HTML5 non-content tags
        "script", "style", "nav", "footer", "header", "aside",
        "button", "a", "form", "input", "textarea", "select",
        "noscript", "iframe", "svg", "dialog", "menu", "canvas",
        // Footers and bottom metadata
        ".footer", "#footer", ".site-footer", ".chapter-footer", ".page-footer",
        ".entry-footer", ".post-footer", ".bottom-bar", ".page-foot", ".foot",
        // Headers and top navigation
        ".header", "#header", ".site-header", ".chapter-header", ".page-header",
        ".top-header", ".entry-header", ".post-header",
        // Navigation, pagers, pagination
        ".nav", ".navbar", ".navigation", ".chapter-nav", ".chapter-navigation",
        ".nav-links", ".nav-buttons", ".pagination", ".pager", ".paginate",
        ".prev-next", ".next-prev", ".chapter-pager", ".chapter-navs",
        ".breadcrumb", ".breadcrumbs",
        // Buttons, ARIA roles
        ".btn", ".button", ".btn-group", "[role=button]", "[role=navigation]",
        "[role=banner]", "[role=contentinfo]", "[role=complementary]",
        // Comments and social widgets
        ".comments", "#comments", ".comment-section", ".comment-box",
        ".disqus", "#disqus_thread", ".share", ".social-share", ".share-buttons", ".social",
        // Ads and sponsors
        ".ads", ".ad", ".advertisement", ".ad-container", ".ad-wrapper", ".ad-box",
        ".banner-ad", ".adsbygoogle", ".patreon", ".donate", ".ko-fi", ".sponsor",
        // Sidebars & miscellaneous widgets
        ".sidebar", "#sidebar", ".widget", ".widget-area", ".author-info", ".rating", ".vote", ".meta-info"
    )

    val NON_CONTENT_REGEXES = listOf(
        Regex("""^(<|«|<<)?\s*(prev|previous|back)\s*(chapter|chap|page|part)?\s*(>|»|>>)?$""", RegexOption.IGNORE_CASE),
        Regex("""^(<|«|<<)?\s*(next|forward)\s*(chapter|chap|page|part)?\s*(>|»|>>)?$""", RegexOption.IGNORE_CASE),
        Regex("""^(table of contents|index|contents|all chapters|chapter list|back to index|return to index)$""", RegexOption.IGNORE_CASE),
        Regex("""^(bookmark( this chapter)?|report (chapter|error|broken)|like|share)$""", RegexOption.IGNORE_CASE),
        Regex("""^(join (our )?discord( server)?|support (the author|the translator|us)( on patreon| on ko-fi)?)$""", RegexOption.IGNORE_CASE),
        Regex("""^(leave a comment|comment(s)?\s*(\(\d+\))?|\d+\s+comments?)$""", RegexOption.IGNORE_CASE),
        Regex("""^(next|prev|previous)\s*[|•/»>]\s*(next|prev|previous|index|home)$""", RegexOption.IGNORE_CASE),
        Regex("""^copyright\s*(©|\(c\))?.*all rights reserved.*$""", RegexOption.IGNORE_CASE)
    )

    fun isNonContentText(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.length < 2) return true
        return NON_CONTENT_REGEXES.any { it.matches(trimmed) }
    }
}
