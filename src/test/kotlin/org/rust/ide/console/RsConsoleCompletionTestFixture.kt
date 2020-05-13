/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.console

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.SingleRootFileViewProvider
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.junit.Assert.assertNotNull
import org.rust.InlineFile
import org.rust.ide.console.RsConsoleCompletionTestFixture.Before
import org.rust.lang.RsLanguage
import org.rust.lang.core.completion.RsCompletionTestFixtureBase
import org.rust.lang.core.parser.RustParserDefinition
import org.rust.lang.core.psi.RsReplCodeFragment

class RsConsoleCompletionTestFixture(fixture: CodeInsightTestFixture) : RsCompletionTestFixtureBase<Before>(fixture) {

    override fun prepare(code: Before) {
        val (previousCommands, lastCommand) = code

        // create main.rs file, which will be used as crate root
        InlineFile(myFixture, "", "main.rs")

        InlineFile(myFixture, lastCommand.trimIndent(), RsConsoleView.VIRTUAL_FILE_NAME).withCaret()
        val codeFragment = myFixture.file as RsReplCodeFragment
        codeFragment.setContext(myFixture.project, previousCommands.map { it.trimIndent() })
        assertNotNull(codeFragment.crateRoot)
    }

    private fun RsReplCodeFragment.setContext(project: Project, previousCommands: List<String>) {
        val codeFragmentContext = RsConsoleCodeFragmentContext(this)
        for (command in previousCommands) {
            val codeFragment = createReplCodeFragment(project, command)
            val oneCommandContext = RsConsoleOneCommandContext(codeFragment)
            codeFragmentContext.addToContext(oneCommandContext)
        }
        codeFragmentContext.updateContext(project, this)
    }

    private fun createReplCodeFragment(project: Project, text: String): RsReplCodeFragment {
        val virtualFile = LightVirtualFile(RsConsoleView.VIRTUAL_FILE_NAME, RsLanguage, text)
        val viewProvider = SingleRootFileViewProvider(PsiManager.getInstance(project), virtualFile, false)
        return RustParserDefinition().createFile(viewProvider) as RsReplCodeFragment
    }

    data class Before(val previousCommands: List<String>, val lastCommand: String)
}
