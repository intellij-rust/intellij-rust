/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.CodeInsightTestFixture.*
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import junit.framework.TestCase
import org.intellij.lang.annotations.Language
import org.rust.TestProject
import org.rust.createAndOpenFileWithCaretMarker
import org.rust.fileTreeFromText
import org.rust.lang.core.macros.MacroExpansionManager
import kotlin.reflect.KClass

open class RsAnnotationTestFixture<C>(
    testCase: TestCase,
    codeInsightFixture: CodeInsightTestFixture,
    annotatorClasses: List<KClass<out AnnotatorBase>> = emptyList(),
    inspectionClasses: List<KClass<out InspectionProfileEntry>> = emptyList(),
    override val baseFileName: String = "main.rs"
) : AnnotationTestFixtureBase(testCase, codeInsightFixture, annotatorClasses, inspectionClasses) {

    fun checkByFileTree(
        @Language("Rust") text: String,
        checkWarn: Boolean = true,
        checkInfo: Boolean = false,
        checkWeakWarn: Boolean = false,
        ignoreExtraHighlighting: Boolean = false,
        stubOnly: Boolean = true,
    ) = check(
        text,
        checkWarn = checkWarn,
        checkInfo = checkInfo,
        checkWeakWarn = checkWeakWarn,
        ignoreExtraHighlighting = ignoreExtraHighlighting,
        configure = { configureByFileTree(it, stubOnly) },
    )

    fun checkFixByFileTree(
        fixName: String,
        @Language("Rust") before: String,
        @Language("Rust") after: String,
        checkWarn: Boolean = true,
        checkInfo: Boolean = false,
        checkWeakWarn: Boolean = false,
        stubOnly: Boolean = true,
        preview: Preview? = SamePreviewAsResult,
    ) = checkFix(
        fixName,
        before,
        after,
        configure = { configureByFileTree(it, stubOnly) },
        checkBefore = { codeInsightFixture.checkHighlighting(checkWarn, checkInfo, checkWeakWarn) },
        checkAfter = this::checkByFileTree,
        preview = preview,
    )

    fun checkFixByFileTreeWithoutHighlighting(
        fixName: String,
        @Language("Rust") before: String,
        @Language("Rust") after: String,
        stubOnly: Boolean = true,
        preview: Preview? = SamePreviewAsResult,
    ) = checkFix(
        fixName,
        before,
        after,
        configure = { configureByFileTree(it, stubOnly) },
        checkBefore = {},
        checkAfter = this::checkByFileTree,
        preview = preview,
    )

    fun checkFixIsUnavailableByFileTree(
        fixName: String,
        @Language("Rust") text: String,
        checkWarn: Boolean = true,
        checkInfo: Boolean = false,
        checkWeakWarn: Boolean = false,
        ignoreExtraHighlighting: Boolean = false,
        stubOnly: Boolean = true,
    ) = checkFixIsUnavailable(
        fixName,
        text,
        checkWarn = checkWarn,
        checkInfo = checkInfo,
        checkWeakWarn = checkWeakWarn,
        ignoreExtraHighlighting = ignoreExtraHighlighting,
        configure = { configureByFileTree(it, stubOnly) },
    )

    fun checkByFile(
        file: VirtualFile,
        context: C? = null,
        checkWarn: Boolean = true,
        checkInfo: Boolean = false,
        checkWeakWarn: Boolean = false,
        ignoreExtraHighlighting: Boolean = false,
    ) = check(
        file,
        checkWarn = checkWarn,
        checkInfo = checkInfo,
        checkWeakWarn = checkWeakWarn,
        ignoreExtraHighlighting = ignoreExtraHighlighting,
        configure = { configureByFile(file, context) },
    )

    private fun checkByFileTree(text: String) {
        fileTreeFromText(replaceCaretMarker(text)).check(codeInsightFixture)
    }

    override fun configureByText(text: String) {
        super.configureByText(text.replaceHighlightingComments())
    }

    private fun configureByFileTree(text: String, stubOnly: Boolean) {
        val testProject = configureByFileTree(text)
        if (stubOnly) {
            (codeInsightFixture as CodeInsightTestFixtureImpl).setVirtualFileFilter {
                !it.path.endsWith(testProject.fileWithCaret)
                    && !MacroExpansionManager.isExpansionFile(it)
            }
        }
    }

    protected open fun configureByFile(file: VirtualFile, context: C?) {
        codeInsightFixture.configureFromExistingVirtualFile(file)
    }

    private fun configureByFileTree(text: String): TestProject {
        return fileTreeFromText(text.replaceHighlightingComments()).createAndOpenFileWithCaretMarker(codeInsightFixture)
    }

    private fun String.replaceHighlightingComments(): String {
        return replace(HIGHLIGHTING_TAG_RE) {
            if (it.groups[3] == null) "<${it.groupValues[1]}>" else "</${it.groupValues[1]}>"
        }
    }

    companion object {
        private val HIGHLIGHTING_TAG_RE =
            """/\*(($ERROR_MARKER|$WARNING_MARKER|$WEAK_WARNING_MARKER|$INFO_MARKER).*?)(\*)?\*/""".toRegex()
    }
}
