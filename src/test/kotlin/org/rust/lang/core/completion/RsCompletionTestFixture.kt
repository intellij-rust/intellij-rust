/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.rust.InlineFile

class RsCompletionTestFixture(
    fixture: CodeInsightTestFixture,
    private val defaultFileName: String = "main.rs"
) : RsCompletionTestFixtureBase<String>(fixture) {

    override fun prepare(code: String) {
        InlineFile(myFixture, code.trimIndent(), defaultFileName).withCaret()
    }
}
