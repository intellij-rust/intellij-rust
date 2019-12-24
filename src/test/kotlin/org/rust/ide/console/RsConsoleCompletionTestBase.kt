/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.console

import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.lang.core.completion.RsCompletionTestBase
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsReplCodeFragment

@ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
abstract class RsConsoleCompletionTestBase : RsCompletionTestBase() {

    protected fun checkReplCompletion(previousLines: String, lastLine: String, variant: String) {
        checkReplCompletion(previousLines, lastLine, arrayOf(variant))
    }

    protected fun checkReplCompletion(previousLinesOriginal: String, lastLineOriginal: String, variants: Array<String>) {
        val previousLines = previousLinesOriginal.trimIndent()
        val lastLine = lastLineOriginal.trimIndent()

        doCheckReplCompletion(previousLines, lastLine, variants)
        if (previousLines.isBlank()) return

        val lastLines = previousLines + lastLine
        doCheckReplCompletion("", lastLines, variants)
        doCheckReplCompletion("", "if true { $lastLines }", variants)
        doCheckReplCompletion("", "fn main() { $lastLines }", variants)
    }

    private fun doCheckReplCompletion(previousLines: String, lastLine: String, variants: Array<String>) {
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

    private fun inlineReplCodeFragment(lastLine: String, previousLines: String) {
        // create main.rs file, which will be used as crate root
        InlineFile("")

        InlineFile(lastLine, RsConsoleView.VIRTUAL_FILE_NAME).withCaret()

        val codeFragment = myFixture.file as RsReplCodeFragment
        codeFragment.context = RsConsoleCodeFragmentContext.createContext(project, codeFragment.crateRoot as RsFile?, previousLines)
        assert(codeFragment.crateRoot != null)
    }
}
