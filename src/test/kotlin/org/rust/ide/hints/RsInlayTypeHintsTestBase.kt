/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hints

import org.intellij.lang.annotations.Language

abstract class RsInlayTypeHintsTestBase : RsInlayTypeHintsTestPlatformBase() {
    protected fun checkByText(@Language("Rust") code: String) {
        InlineFile(code.replace(HINT_COMMENT_PATTERN, "<$1/>"))
        checkInlays()
    }

    companion object {
        private val HINT_COMMENT_PATTERN = Regex("""/\*(hint.*?)\*/""")
    }
}
