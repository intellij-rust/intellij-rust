/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import com.intellij.openapi.project.Project
import com.intellij.psi.util.parentOfType
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.fileTreeFromText
import org.rust.lang.core.psi.RsCodeFragment
import org.rust.lang.core.psi.ext.RsElement

abstract class RsAnnotationTestBase : RsTestBase() {

    protected lateinit var annotationFixture: RsAnnotationTestFixture<Unit>

    override fun setUp() {
        super.setUp()
        annotationFixture = createAnnotationFixture()
        annotationFixture.setUp()
    }

    override fun tearDown() {
        annotationFixture.tearDown()
        super.tearDown()
    }

    protected abstract fun createAnnotationFixture(): RsAnnotationTestFixture<Unit>

    protected fun checkHighlighting(@Language("Rust") text: String, ignoreExtraHighlighting: Boolean = true) =
        annotationFixture.checkHighlighting(text, ignoreExtraHighlighting)

    protected fun checkInfo(@Language("Rust") text: String) = annotationFixture.checkInfo(text)
    protected fun checkWarnings(@Language("Rust") text: String) = annotationFixture.checkWarnings(text)
    protected fun checkErrors(@Language("Rust") text: String) = annotationFixture.checkErrors(text)

    protected fun checkByText(
        @Language("Rust") text: String,
        checkWarn: Boolean = true,
        checkInfo: Boolean = false,
        checkWeakWarn: Boolean = false,
        ignoreExtraHighlighting: Boolean = false,
    ) = annotationFixture.checkByText(text, checkWarn, checkInfo, checkWeakWarn, ignoreExtraHighlighting)

    protected fun checkFixByText(
        fixName: String,
        @Language("Rust") before: String,
        @Language("Rust") after: String,
        checkWarn: Boolean = true,
        checkInfo: Boolean = false,
        checkWeakWarn: Boolean = false,
        preview: Preview? = SamePreviewAsResult,
    ) = annotationFixture.checkFixByText(fixName, before, after, checkWarn, checkInfo, checkWeakWarn, preview)

    protected fun checkFixByTextWithoutHighlighting(
        fixName: String,
        @Language("Rust") before: String,
        @Language("Rust") after: String,
        preview: Preview? = SamePreviewAsResult,
    ) = annotationFixture.checkFixByTextWithoutHighlighting(fixName, before, after, preview)

    protected fun checkByFileTree(
        @Language("Rust") text: String,
        checkWarn: Boolean = true,
        checkInfo: Boolean = false,
        checkWeakWarn: Boolean = false,
        ignoreExtraHighlighting: Boolean = false,
        stubOnly: Boolean = true,
    ) = annotationFixture.checkByFileTree(text, checkWarn, checkInfo, checkWeakWarn, ignoreExtraHighlighting, stubOnly)

    protected fun checkFixByFileTree(
        fixName: String,
        @Language("Rust") before: String,
        @Language("Rust") after: String,
        checkWarn: Boolean = true,
        checkInfo: Boolean = false,
        checkWeakWarn: Boolean = false,
        stubOnly: Boolean = true,
        preview: Preview? = SamePreviewAsResult,
    ) = annotationFixture.checkFixByFileTree(fixName, before, after, checkWarn, checkInfo, checkWeakWarn, stubOnly, preview)

    protected fun checkFixByFileTreeWithoutHighlighting(
        fixName: String,
        @Language("Rust") before: String,
        @Language("Rust") after: String,
        stubOnly: Boolean = true,
        preview: Preview? = SamePreviewAsResult,
    ) = annotationFixture.checkFixByFileTreeWithoutHighlighting(fixName, before, after, stubOnly, preview)

    protected fun checkFixByTextWithLiveTemplate(
        fixName: String,
        @Language("Rust") before: String,
        toType: String,
        @Language("Rust") after: String,
        fileName: String = "main.rs",
        checkWarn: Boolean = true,
        checkInfo: Boolean = false,
        checkWeakWarn: Boolean = false
    ) {
        checkByTextWithLiveTemplate(before, after.trimIndent(), toType, fileName) {
            annotationFixture.checkFixPartial(fixName, before, checkWarn, checkInfo, checkWeakWarn)
        }
    }

    protected fun checkFixIsUnavailable(
        fixName: String,
        @Language("Rust") text: String,
        checkWarn: Boolean = true,
        checkInfo: Boolean = false,
        checkWeakWarn: Boolean = false,
        ignoreExtraHighlighting: Boolean = false,
    ) = annotationFixture.checkFixIsUnavailable(fixName, text, checkWarn, checkInfo, checkWeakWarn, ignoreExtraHighlighting)

    protected fun checkFixIsUnavailableByFileTree(
        fixName: String,
        @Language("Rust") text: String,
        checkWarn: Boolean = true,
        checkInfo: Boolean = false,
        checkWeakWarn: Boolean = false,
        ignoreExtraHighlighting: Boolean = false,
        stubOnly: Boolean = true,
    ) = annotationFixture.checkFixIsUnavailableByFileTree(fixName, text, checkWarn, checkInfo, checkWeakWarn, ignoreExtraHighlighting, stubOnly)

    protected fun checkDontTouchAstInOtherFiles(@Language("Rust") text: String, checkInfo: Boolean = false, filePath: String? = null) {
        fileTreeFromText(text).create()
        val testFilePath = filePath ?: "main.rs"
        (myFixture as CodeInsightTestFixtureImpl) // meh
            .setVirtualFileFilter { !it.path.endsWith(testFilePath) }

        myFixture.configureFromTempProjectFile(testFilePath)
        myFixture.testHighlighting(false, checkInfo, false)
    }

    protected fun checkByCodeFragment(
        @Language("Rust") context: String,
        fragment: String,
        fragmentConstructor: (Project, String, RsElement) -> RsCodeFragment,
        checkWarn: Boolean = true,
        checkInfo: Boolean = false,
        checkWeakWarn: Boolean = false
    ) {
        InlineFile(context).withCaret()
        val contextElement = myFixture.file.findElementAt(myFixture.caretOffset)?.parentOfType<RsElement>()!!
        val codeFragment = fragmentConstructor(project, fragment, contextElement)
        myFixture.configureFromExistingVirtualFile(codeFragment.virtualFile)
        myFixture.testHighlighting(checkWarn, checkInfo, checkWeakWarn)
    }
}
