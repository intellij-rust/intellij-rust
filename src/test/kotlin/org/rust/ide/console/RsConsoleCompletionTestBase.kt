/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.console

import org.rust.ProjectDescriptor
import org.rust.RsTestBase
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.lang.core.completion.RsCompletionTestFixture
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsReplCodeFragment

@ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
abstract class RsConsoleCompletionTestBase : RsTestBase() {

    private lateinit var completionFixture: RsCompletionTestFixture

    override fun setUp() {
        super.setUp()
        completionFixture = RsCompletionTestFixture(myFixture)
        completionFixture.setUp()
    }

    override fun tearDown() {
        completionFixture.tearDown()
        super.tearDown()
    }

    protected fun checkContainsCompletion(previousLines: String, lastLine: String, variants: Array<String>) {
        val previousLinesTrimmed = previousLines.trimIndent()
        val lastLineTrimmed = lastLine.trimIndent()

        doCheckContainsCompletion(previousLinesTrimmed, lastLineTrimmed, variants)
        if (previousLines.isBlank()) return

        val lastLines = previousLinesTrimmed + lastLineTrimmed
        doCheckContainsCompletion("", lastLines, variants)
        doCheckContainsCompletion("", "if true { $lastLines }", variants)
        doCheckContainsCompletion("", "fn main() { $lastLines }", variants)
    }

    private fun doCheckContainsCompletion(previousLines: String, lastLine: String, variants: Array<String>) {
        inlineReplCodeFragment(lastLine, previousLines)

        val lookups = myFixture.completeBasic()
        checkNotNull(lookups) {
            "Expected completions that contain all ${variants.contentToString()}, but no completions found"
        }
        for (variant in variants) {
            if (lookups.all { it.lookupString != variant }) {
                error("Expected completions that contain `$variant`, but got ${lookups.map { it.lookupString }}")
            }
        }
    }

    protected fun checkSingleCompletion(previousLines: String, lastLineBefore: String, lastLineAfter: String) {
        inlineReplCodeFragment(lastLineBefore.trimIndent(), previousLines.trimIndent())
        completionFixture.executeSoloCompletion()
        myFixture.checkResult(lastLineAfter.trimIndent())
    }

    private fun inlineReplCodeFragment(lastLine: String, previousLines: String) {
        // create main.rs file, which will be used as crate root
        InlineFile("")

        InlineFile(lastLine, RsConsoleView.VIRTUAL_FILE_NAME).withCaret()

        val codeFragment = myFixture.file as RsReplCodeFragment
        val crateRoot = codeFragment.crateRoot as RsFile?
        codeFragment.context = RsConsoleCodeFragmentContext.createContext(project, crateRoot, previousLines)
        assertNotNull(codeFragment.crateRoot)
    }
}
