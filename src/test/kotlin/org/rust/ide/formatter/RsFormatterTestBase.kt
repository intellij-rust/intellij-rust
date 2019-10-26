    /*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.formatter

import com.intellij.psi.formatter.FormatterTestCase
import com.intellij.ext.TestCase
import org.intellij.lang.annotations.Language
import kotlin.reflect.KMutableProperty0

abstract class RsFormatterTestBase : FormatterTestCase() {
    override fun getTestDataPath() = "src/test/resources"

    override fun getBasePath() = "org/rust/ide/formatter/fixtures"

    override fun getFileExtension() = "rs"

    override fun getTestName(lowercaseFirstLetter: Boolean): String {
        val camelCase = super.getTestName(lowercaseFirstLetter)
        return TestCase.camelOrWordsToSnake(camelCase)
    }

    override fun doTextTest(@Language("Rust") text: String, @Language("Rust") textAfter: String) {
        check(text.trimIndent() != textAfter.trimIndent())
        super.doTextTest(text.trimIndent(), textAfter.trimIndent())
    }

    fun checkNotChanged(@Language("Rust") text: String) {
        super.doTextTest(text.trimIndent(), text.trimIndent())
    }

    fun doTextTest(
        optionProperty: KMutableProperty0<Boolean>,
        @Language("Rust") before: String,
        @Language("Rust") afterOn: String = before,
        @Language("Rust") afterOff: String = before
    ) {
        val initialValue = optionProperty.get()
        optionProperty.set(true)
        try {
            super.doTextTest(before.trimIndent(), afterOn.trimIndent())
            optionProperty.set(false)
            super.doTextTest(before.trimIndent(), afterOff.trimIndent())
        } finally {
            optionProperty.set(initialValue)
        }
    }
}
