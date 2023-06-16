/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.CodeInsightTestFixture.*
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import junit.framework.TestCase
import org.intellij.lang.annotations.Language
import org.rust.*
import org.rust.lang.core.macros.MacroExpansionManager
import org.rust.lang.core.macros.macroExpansionManagerIfCreated
import kotlin.reflect.KClass

open class RsAnnotationTestFixture<C>(
    testCase: TestCase,
    codeInsightFixture: CodeInsightTestFixture,
    annotatorClasses: List<KClass<out AnnotatorBase>> = emptyList(),
    inspectionClasses: List<KClass<out InspectionProfileEntry>> = emptyList(),
    override val baseFileName: String = "main.rs"
) : AnnotationTestFixtureBase(testCase, codeInsightFixture, annotatorClasses, inspectionClasses) {

    private val extraSeverities: MutableSet<String> = mutableSetOf()
    private val testWrapping: TestWrapping = (testCase as? RsTestBase)?.testWrapping ?: TestWrapping.NONE
    private var testWrappingUnwrapper: TestUnwrapper? = null
    override val isWrappingActive: Boolean get() = testWrappingUnwrapper != null

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

    override fun checkFixAvailableInSelectionOnly(
        fixName: String,
        before: String,
        checkWarn: Boolean,
        checkInfo: Boolean,
        checkWeakWarn: Boolean,
    ) {
        if (testWrapping == TestWrapping.NONE) {
            super.checkFixAvailableInSelectionOnly(fixName, before, checkWarn, checkInfo, checkWeakWarn)
        } else {
            // Pass
        }
    }

    private fun checkByFileTree(text: String) {
        fileTreeFromText(replaceCaretMarker(text)).check(codeInsightFixture)
    }

    override fun configureByText(text: String) {
        if (testWrapping == TestWrapping.NONE) {
            super.configureByText(text.replaceHighlightingCommentsWithXmlTags())
        } else {
            val (text2, unwrapper) = testWrapping.wrapCode(
                project,
                text.trimIndent().replaceHighlightingXmlTagsWithRustComments()
            )
            codeInsightFixture.configureByText(
                baseFileName,
                replaceCaretMarker(text2.replaceHighlightingCommentsWithXmlTags())
            )
            codeInsightFixture.project.macroExpansionManagerIfCreated?.updateInUnitTestMode()
            unwrapper?.init(codeInsightFixture.file)
            this.testWrappingUnwrapper = unwrapper
        }
    }

    override fun checkByText(text: String) {
        testWrappingUnwrapper?.unwrap()
        codeInsightFixture.checkResult(replaceCaretMarker(text.trimIndent()))
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
        return fileTreeFromText(text.replaceHighlightingCommentsWithXmlTags())
            .createAndOpenFileWithCaretMarker(codeInsightFixture)
    }

    override fun registerSeverities(severities: List<HighlightSeverity>) {
        severities.mapTo(extraSeverities) { it.name }
        super.registerSeverities(severities)
    }

    private fun String.replaceHighlightingCommentsWithXmlTags(): String {
        return replace(commentHighlightingTagRegex()) {
            if (it.groups[3] == null) "<${it.groupValues[1]}>" else "</${it.groupValues[1]}>"
        }
    }

    private fun String.replaceHighlightingXmlTagsWithRustComments(): String {
        return replace(xmlHighlightingOpenTagRegex()) {
            "/*" + it.groupValues[2] + (if (it.groups[1] == null) "*/" else "**/")
        }
    }

    private fun buildSeveritiesList(): String {
        val severities = listOf(ERROR_MARKER, WARNING_MARKER, WEAK_WARNING_MARKER, INFO_MARKER) +
            extraSeverities
        return severities.joinToString(separator = "|")
    }

    /** A regex that matches highlighting tags in XML style like `<error=...>` and `</error>` */
    private fun xmlHighlightingOpenTagRegex(): Regex {
        return ("<(/)?((${buildSeveritiesList()})" +
            "(?:\\s+descr=\"((?:[^\"]|\\\\\"|\\\\\\\\\"|\\\\\\[|\\\\])*)\")?" +
            "(?:\\s+type=\"([0-9A-Z_]+)\")?" +
            "(?:\\s+foreground=\"([0-9xa-f]+)\")?" +
            "(?:\\s+background=\"([0-9xa-f]+)\")?" +
            "(?:\\s+effectcolor=\"([0-9xa-f]+)\")?" +
            "(?:\\s+effecttype=\"([A-Z]+)\")?" +
            "(?:\\s+fonttype=\"([0-9]+)\")?" +
            "(?:\\s+textAttributesKey=\"((?:[^\"]|\\\\\"|\\\\\\\\\"|\\\\\\[|\\\\])*)\")?" +
            "(?:\\s+bundleMsg=\"((?:[^\"]|\\\\\"|\\\\\\\\\")*)\")?" +
            "(?:\\s+tooltip=\"((?:[^\"]|\\\\\"|\\\\\\\\\")*)\")?" +
            "?)(/)?>").toRegex(RegexOption.MULTILINE)
    }

    /** A regex that matches highlighting tags in rust comment style like `/*error=...*/` and `/*error**/` */
    private fun commentHighlightingTagRegex(): Regex =
        """/\*((${buildSeveritiesList()}).*?)(\*)?\*/""".toRegex(RegexOption.MULTILINE)
}
