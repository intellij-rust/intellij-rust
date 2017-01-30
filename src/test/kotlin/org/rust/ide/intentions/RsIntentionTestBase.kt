package org.rust.ide.intentions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.util.io.StreamUtil
import org.intellij.lang.annotations.Language
import org.rust.ide.utils.loadCodeSampleResource
import org.rust.lang.RsTestBase

abstract class RsIntentionTestBase(val intention: IntentionAction) : RsTestBase() {
    final override val dataPath: String get() = ""

    fun testIntentionHasDocumentation() {
        val directory = "intentionDescriptions/${intention.javaClass.simpleName}"
        val files = listOf("before.rs.template", "after.rs.template", "description.html")
        for (file in files) {
            val stream = intention.javaClass.classLoader.getResourceAsStream("$directory/$file")
                ?: error("No inspection description for ${intention.javaClass}.\n" +
                "Add ${files.joinToString()} to src/main/resources/$directory")

            if (file.endsWith(".html")) {
                val text = StreamUtil.readText(stream, Charsets.UTF_8)
                checkHtmlStyle(text)
            }
        }
    }

    protected fun doAvailableTest(@Language("Rust") before: String, @Language("Rust") after: String) {
        InlineFile(before).withCaret()
        myFixture.launchAction(intention)
        myFixture.checkResult(replaceCaretMarker(after))
    }

    protected fun doUnavailableTest(@Language("Rust") before: String) {
        InlineFile(before).withCaret()
        check(intention.familyName !in myFixture.availableIntentions.mapNotNull { it.familyName }) {
            "\"$intention\" intention should not be available"
        }
    }

    private fun checkHtmlStyle(html: String) {
        // http://stackoverflow.com/a/1732454
        val re = "<body>(.*)</body>".toRegex(RegexOption.DOT_MATCHES_ALL)
        val body = re.find(html)!!.groups[1]!!.value.trim()
        check(body[0].isUpperCase()) {
            "Please start description with the capital latter"
        }

        check(body.last() == '.') {
            "Please end description with a period"
        }
    }
}
