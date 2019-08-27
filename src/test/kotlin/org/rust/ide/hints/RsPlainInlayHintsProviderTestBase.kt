/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hints

import com.intellij.codeInsight.daemon.impl.HintRenderer
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase

abstract class RsPlainInlayHintsProviderTestBase : RsTestBase() {
    protected fun checkByText(
        @Language("Rust") code: String,
        enabledHints: RsPlainHint? = null,
        smart: Boolean = true
    ) {
        InlineFile(code.replace(HINT_COMMENT_PATTERN, "<$1/>"))
        if (enabledHints != null) {
            for (hintType in RsPlainHint.values) {
                hintType.option.set(hintType == enabledHints)
            }
        }
        RsPlainHint.SMART_HINTING.set(smart)

        try {
            myFixture.testInlays({ (it.renderer as HintRenderer).text }) { it.renderer is HintRenderer }
        } finally {
            RsPlainHint.values.forEach { it.option.set(true) }
            RsPlainHint.SMART_HINTING.set(true)
        }
    }

    companion object {
        private val HINT_COMMENT_PATTERN = Regex("""/\*(hint.*?)\*/""")
    }
}
