    /*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.formatter

import com.intellij.psi.formatter.FormatterTestCase
import org.intellij.lang.annotations.Language
import org.rust.lang.RsTestBase

abstract class RsFormatterTestBase : FormatterTestCase() {
    override fun getTestDataPath() = "src/test/resources"

    override fun getBasePath() = "org/rust/ide/formatter/fixtures"

    override fun getFileExtension() = "rs"

    override fun getTestName(lowercaseFirstLetter: Boolean): String {
        val camelCase = super.getTestName(lowercaseFirstLetter)
        return RsTestBase.camelOrWordsToSnake(camelCase)
    }

    override fun doTextTest(@Language("Rust") text: String, @Language("Rust") textAfter: String) {
        check(text.trimIndent() != textAfter.trimIndent())
        super.doTextTest(text.trimIndent(), textAfter.trimIndent())
    }

    fun checkNotChanged(@Language("Rust") text: String) {
        super.doTextTest(text.trimIndent(), text.trimIndent())
    }

}
