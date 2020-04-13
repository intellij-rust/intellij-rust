/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.console

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.junit.Assert.assertNotNull
import org.rust.InlineFile
import org.rust.ide.console.RsConsoleCompletionTestFixture.Before
import org.rust.lang.core.completion.RsCompletionTestFixtureBase
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsReplCodeFragment

class RsConsoleCompletionTestFixture(fixture: CodeInsightTestFixture) : RsCompletionTestFixtureBase<Before>(fixture) {

    override fun prepare(code: Before) {
        val (previousLines, lastLine) = code

        // create main.rs file, which will be used as crate root
        InlineFile(myFixture, "", "main.rs")

        InlineFile(myFixture, lastLine.trimIndent(), RsConsoleView.VIRTUAL_FILE_NAME).withCaret()

        val codeFragment = myFixture.file as RsReplCodeFragment
        val crateRoot = codeFragment.crateRoot as RsFile?
        codeFragment.context = RsConsoleCodeFragmentContext.createContext(myFixture.project, crateRoot, previousLines.trimIndent())
        assertNotNull(codeFragment.crateRoot)
    }

    data class Before(val previousLines: String, val lastLine: String)
}
