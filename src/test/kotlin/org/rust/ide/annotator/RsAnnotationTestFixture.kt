/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.ide.annotator.AnnotationTestFixtureBase
import com.intellij.ide.annotator.AnnotatorBase
import com.intellij.openapiext.Testmark
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import org.intellij.lang.annotations.Language
import org.rust.TestProject
import org.rust.createAndOpenFileWithCaretMarker
import org.rust.fileTreeFromText
import kotlin.reflect.KClass

class RsAnnotationTestFixture(
    codeInsightFixture: CodeInsightTestFixture,
    annotatorClasses: List<KClass<out AnnotatorBase>> = emptyList(),
    inspectionClasses: List<KClass<out InspectionProfileEntry>> = emptyList(),
    override val baseFileName: String = "main.rs"
) : AnnotationTestFixtureBase(codeInsightFixture, annotatorClasses, inspectionClasses) {

    fun checkByFileTree(
        @Language("Rust") text: String,
        checkWarn: Boolean = true,
        checkInfo: Boolean = false,
        checkWeakWarn: Boolean = false,
        ignoreExtraHighlighting: Boolean = false,
        stubOnly: Boolean = true,
        testmark: Testmark? = null
    ) = check(text,
        checkWarn = checkWarn,
        checkInfo = checkInfo,
        checkWeakWarn = checkWeakWarn,
        ignoreExtraHighlighting = ignoreExtraHighlighting,
        configure = { configureByFileTree(it, stubOnly) },
        testmark = testmark)

    fun checkFixByFileTree(
        fixName: String,
        @Language("Rust") before: String,
        @Language("Rust") after: String,
        checkWarn: Boolean = true,
        checkInfo: Boolean = false,
        checkWeakWarn: Boolean = false,
        stubOnly: Boolean = true,
        testmark: Testmark? = null
    ) = checkFix(fixName, before, after,
        configure = { configureByFileTree(it, stubOnly) },
        checkBefore = { codeInsightFixture.checkHighlighting(checkWarn, checkInfo, checkWeakWarn) },
        checkAfter = this::checkByFileTree,
        testmark = testmark)

    fun checkFixByFileTreeWithoutHighlighting(
        fixName: String,
        @Language("Rust") before: String,
        @Language("Rust") after: String,
        stubOnly: Boolean = true,
        testmark: Testmark? = null
    ) = checkFix(fixName, before, after,
        configure = { configureByFileTree(it, stubOnly) },
        checkBefore = {},
        checkAfter = this::checkByFileTree,
        testmark = testmark)

    fun checkFixIsUnavailableByFileTree(
        fixName: String,
        @Language("Rust") text: String,
        checkWarn: Boolean = true,
        checkInfo: Boolean = false,
        checkWeakWarn: Boolean = false,
        ignoreExtraHighlighting: Boolean = false,
        stubOnly: Boolean = true,
        testmark: Testmark? = null
    ) = checkFixIsUnavailable(fixName, text,
        checkWarn = checkWarn,
        checkInfo = checkInfo,
        checkWeakWarn = checkWeakWarn,
        ignoreExtraHighlighting = ignoreExtraHighlighting,
        configure = { configureByFileTree(it, stubOnly) },
        testmark = testmark)

    override fun check(
        text: String,
        checkWarn: Boolean,
        checkInfo: Boolean,
        checkWeakWarn: Boolean,
        ignoreExtraHighlighting: Boolean,
        configure: (String) -> Unit,
        testmark: Testmark?
    ) {
        val newConfigure: (String) -> Unit = {
            configure(it)
        }
        super.check(text, checkWarn, checkInfo, checkWeakWarn, ignoreExtraHighlighting, newConfigure, testmark)
    }

    override fun checkFix(
        fixName: String,
        before: String,
        after: String,
        configure: (String) -> Unit,
        checkBefore: () -> Unit,
        checkAfter: (String) -> Unit,
        testmark: Testmark?
    ) {
        val newConfigure: (String) -> Unit = {
            configure(it)
        }
        super.checkFix(fixName, before, after, newConfigure, checkBefore, checkAfter, testmark)
    }

    private fun checkByFileTree(text: String) {
        fileTreeFromText(replaceCaretMarker(text)).check(codeInsightFixture)
    }

    private fun configureByFileTree(text: String, stubOnly: Boolean) {
        val testProject = configureByFileTree(text)
        if (stubOnly) {
            (codeInsightFixture as CodeInsightTestFixtureImpl)
                .setVirtualFileFilter { !it.path.endsWith(testProject.fileWithCaret) }
        }
    }

    private fun configureByFileTree(text: String): TestProject {
        return fileTreeFromText(text).createAndOpenFileWithCaretMarker(codeInsightFixture)
    }
}
