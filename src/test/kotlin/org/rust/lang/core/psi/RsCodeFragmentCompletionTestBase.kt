/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.util.parentOfType
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.intellij.lang.annotations.Language
import org.rust.InlineFile
import org.rust.RsTestBase
import org.rust.lang.core.completion.RsCompletionTestFixtureBase
import org.rust.lang.core.psi.RsCodeFragmentCompletionTestFixture.Data
import org.rust.lang.core.psi.ext.RsElement

abstract class RsCodeFragmentCompletionTestBase(
    private val fragmentConstructor: (Project, String, RsElement) -> RsCodeFragment
) : RsTestBase() {

    private lateinit var completionFixture: RsCodeFragmentCompletionTestFixture

    override fun setUp() {
        super.setUp()
        completionFixture = RsCodeFragmentCompletionTestFixture(myFixture, fragmentConstructor)
        completionFixture.setUp()
    }

    override fun tearDown() {
        completionFixture.tearDown()
        super.tearDown()
    }

    protected fun checkContainsCompletion(@Language("Rust") context: String, fragment: String, variant: String) {
        completionFixture.checkContainsCompletion(Data(context, fragment), listOf(variant))
    }

    protected fun checkNotContainsCompletion(@Language("Rust") context: String, fragment: String, variant: String) {
        completionFixture.checkNotContainsCompletion(Data(context, fragment), setOf(variant))
    }
}

private class RsCodeFragmentCompletionTestFixture(
    fixture: CodeInsightTestFixture,
    private val fragmentConstructor: (Project, String, RsElement) -> RsCodeFragment
) : RsCompletionTestFixtureBase<Data>(fixture) {

    override fun prepare(code: Data) {
        val (context, fragment) = code
        InlineFile(myFixture, context, "main.rs").withCaret()
        check("<caret>" in fragment) {
            "Please, add `<caret>` marker to\n$fragment"
        }
        val contextElement = myFixture.file.findElementAt(myFixture.caretOffset)?.parentOfType<RsElement>()!!
        val codeFragment = fragmentConstructor(project, fragment, contextElement)
        myFixture.configureFromExistingVirtualFile(codeFragment.virtualFile)
    }

    data class Data(val context: String, val fragment: String)
}
