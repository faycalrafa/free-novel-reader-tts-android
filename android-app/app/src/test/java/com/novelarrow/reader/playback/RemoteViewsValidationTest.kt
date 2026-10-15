package com.novelarrow.reader.playback

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Validates that all XML layouts used in RemoteViews strictly contain
 * classes annotated with @RemoteView by the Android Framework.
 *
 * Plain 'android.view.View', 'Space', 'ConstraintLayout', etc. are strictly
 * forbidden in RemoteViews and will crash Android SystemUI with BadForegroundServiceNotificationException.
 */
class RemoteViewsValidationTest {

    companion object {
        // Official Android Framework @RemoteView permitted tags
        private val ALLOWED_REMOTE_VIEWS_TAGS = setOf(
            "LinearLayout",
            "RelativeLayout",
            "FrameLayout",
            "GridLayout",
            "TextView",
            "ImageView",
            "Button",
            "ImageButton",
            "ProgressBar",
            "Chronometer",
            "TextClock",
            "AnalogClock",
            "AdapterViewFlipper",
            "StackView",
            "ViewFlipper",
            "GridView",
            "ListView"
        )
    }

    @Test
    fun testCompactToolbarLayoutHasOnlyPermittedRemoteViewsTags() {
        val file = findLayoutFile("notification_quick_toolbar.xml")
        validateAllXmlTags(file)
    }

    @Test
    fun testExpandedToolbarLayoutHasOnlyPermittedRemoteViewsTags() {
        val file = findLayoutFile("notification_quick_toolbar_expanded.xml")
        validateAllXmlTags(file)
    }

    private fun findLayoutFile(fileName: String): File {
        val candidates = listOf(
            File("src/main/res/layout/$fileName"),
            File("app/src/main/res/layout/$fileName"),
            File("android-app/app/src/main/res/layout/$fileName")
        )
        val found = candidates.firstOrNull { it.exists() }
        assertTrue("Layout file $fileName must exist", found != null && found.exists())
        return found!!
    }

    private fun validateAllXmlTags(file: File) {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(file)
        doc.documentElement.normalize()

        val illegalTags = mutableListOf<String>()
        collectElementTags(doc.documentElement, illegalTags)

        assertTrue(
            "Found illegal RemoteViews tags in ${file.name}: $illegalTags. " +
                    "Only classes with @RemoteView (such as FrameLayout, LinearLayout, TextView, ImageView) are permitted in RemoteViews layouts.",
            illegalTags.isEmpty()
        )
    }

    private fun collectElementTags(node: Node, illegalTags: MutableList<String>) {
        if (node.nodeType == Node.ELEMENT_NODE) {
            val tagName = (node as Element).tagName
            if (!ALLOWED_REMOTE_VIEWS_TAGS.contains(tagName)) {
                illegalTags.add(tagName)
            }
        }
        val children = node.childNodes
        for (i in 0 until children.length) {
            collectElementTags(children.item(i), illegalTags)
        }
    }
}
