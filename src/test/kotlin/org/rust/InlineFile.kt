/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.rust.lang.core.macros.macroExpansionManagerIfCreated

class InlineFile(fixture: CodeInsightTestFixture, private val code: String, val name: String) {
    private val hasCaretMarker = "/*caret*/" in code || "<caret>" in code

    init {
        fixture.configureByText(name, replaceCaretMarker(code))
        fixture.project.macroExpansionManagerIfCreated?.updateInUnitTestMode()
    }

    fun withCaret() {
        check(hasCaretMarker) {
            "Please, add `/*caret*/` or `<caret>` marker to\n$code"
        }
    }
}

