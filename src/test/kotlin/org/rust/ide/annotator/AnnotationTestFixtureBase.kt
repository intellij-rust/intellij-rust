/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.codeInsight.daemon.impl.SeveritiesProvider
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionActionDelegate
import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.SuppressIntentionActionFromFix
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.InspectionTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.impl.BaseFixture
import junit.framework.TestCase
import org.intellij.lang.annotations.Language
import org.rust.findAnnotationInstance
import org.rust.ide.DeferredPreviewCheck
import org.rust.ide.checkNoPreview
import org.rust.ide.checkPreviewAndLaunchAction
import org.rust.lang.core.macros.macroExpansionManagerIfCreated
import kotlin.reflect.KClass

abstract class AnnotationTestFixtureBase(
    protected val testCase: TestCase,
    protected val codeInsightFixture: CodeInsightTestFixture,
    private val annotatorClasses: List<KClass<out AnnotatorBase>> = emptyList(),
    private val inspectionClasses: List<KClass<out InspectionProfileEntry>> = emptyList()
) : BaseFixture() {

    val project: Project get() = codeInsightFixture.project
    lateinit var enabledInspections: List<InspectionProfileEntry>

    protected abstract val baseFileName: String
    protected open val isWrappingActive: Boolean get() = false

    override fun setUp() {
        super.setUp()
        annotatorClasses.forEach { AnnotatorBase.enableAnnotator(it.java, testRootDisposable) }
        enabledInspections = InspectionTestUtil.instantiateTools(inspectionClasses.map { it.java })
        codeInsightFixture.enableInspections(*enabledInspections.toTypedArray())
        if (testCase.findAnnotationInstance<BatchMode>() != null) {
            enableBatchMode()
        }
    }

    @Suppress("UnstableApiUsage")
    private fun enableBatchMode() {
        // Unfortunately, `DefaultHighlightVisitor` has package private visibility
        // so use reflection to create its instance
        val visitorClass = Class.forName("com.intellij.codeInsight.daemon.impl.DefaultHighlightVisitor")
        val constructor = visitorClass.getDeclaredConstructor(
            Project::class.java,
            Boolean::class.java,
            Boolean::class.java,
            Boolean::class.java
        )
        constructor.isAccessible = true
        val visitor = constructor.newInstance(
            project,
            /* highlightErrorElements = */ true,
            /* runAnnotators = */ true,
            /* batchMode = */ true
        ) as HighlightVisitor
        ExtensionTestUtil.maskExtensions(HighlightVisitor.EP_HIGHLIGHT_VISITOR, listOf(visitor), testRootDisposable, true, project)
    }

    protected fun replaceCaretMarker(text: String) = text.replace("/*caret*/", "<caret>")

    fun checkHighlighting(text: String, ignoreExtraHighlighting: Boolean) = checkByText(
        text,
        checkWarn = false,
        checkWeakWarn = false,
        checkInfo = false,
        ignoreExtraHighlighting = ignoreExtraHighlighting
    )
    fun checkInfo(text: String) = checkByText(text, checkWarn = false, checkWeakWarn = false, checkInfo = true)
    fun checkWarnings(text: String) = checkByText(text, checkWarn = true, checkWeakWarn = true, checkInfo = false)
    fun checkErrors(text: String) = checkByText(text, checkWarn = false, checkWeakWarn = false, checkInfo = false)

    protected open fun configureByText(text: String) {
        codeInsightFixture.configureByText(baseFileName, replaceCaretMarker(text.trimIndent()))
        codeInsightFixture.project.macroExpansionManagerIfCreated?.updateInUnitTestMode()
    }

    fun checkByText(
        text: String,
        checkWarn: Boolean = true,
        checkInfo: Boolean = false,
        checkWeakWarn: Boolean = false,
        ignoreExtraHighlighting: Boolean = false,
    ) = check(
        text,
        checkWarn = checkWarn,
        checkInfo = checkInfo,
        checkWeakWarn = checkWeakWarn,
        ignoreExtraHighlighting = ignoreExtraHighlighting,
        configure = this::configureByText,
    )

    fun checkFixByText(
        fixName: String,
        before: String,
        after: String,
        checkWarn: Boolean = true,
        checkInfo: Boolean = false,
        checkWeakWarn: Boolean = false,
        preview: Preview? = SamePreviewAsResult,
    ) = checkFix(
        fixName,
        before,
        after,
        configure = this::configureByText,
        checkBefore = {
            checkWarningFlags(before, checkWarn, checkWeakWarn)
            checkHighlighting(checkWarn, checkInfo, checkWeakWarn, ignoreExtraHighlighting = false)
        },
        checkAfter = this::checkByText,
        preview = preview,
    )

    fun checkFixPartial(
        fixName: String,
        before: String,
        checkWarn: Boolean = true,
        checkInfo: Boolean = false,
        checkWeakWarn: Boolean = false
    ) = checkFix(fixName, before, before,
        configure = this::configureByText,
        checkBefore = {
            checkWarningFlags(before, checkWarn, checkWeakWarn)
            checkHighlighting(checkWarn, checkInfo, checkWeakWarn, ignoreExtraHighlighting = false)
        },
        checkAfter = { },
        preview = SamePreviewAsResult,
    )

    fun checkFixIsUnavailable(
        fixName: String,
        text: String,
        checkWarn: Boolean = true,
        checkInfo: Boolean = false,
        checkWeakWarn: Boolean = false,
        ignoreExtraHighlighting: Boolean = false,
    ) = checkFixIsUnavailable(
        fixName,
        text,
        checkWarn = checkWarn,
        checkInfo = checkInfo,
        checkWeakWarn = checkWeakWarn,
        ignoreExtraHighlighting = ignoreExtraHighlighting,
        configure = this::configureByText,
    )

    protected fun checkFixIsUnavailable(
        fixName: String,
        text: String,
        checkWarn: Boolean,
        checkInfo: Boolean,
        checkWeakWarn: Boolean,
        ignoreExtraHighlighting: Boolean,
        configure: (String) -> Unit,
    ) {
        check(text, checkWarn, checkInfo, checkWeakWarn, ignoreExtraHighlighting, configure)
        check(codeInsightFixture.filterAvailableIntentions(fixName).isEmpty()) {
            "Fix $fixName should not be possible to apply."
        }
    }

    fun checkFixByTextWithoutHighlighting(
        fixName: String,
        before: String,
        after: String,
        preview: Preview? = SamePreviewAsResult,
    ) = checkFix(
        fixName,
        before,
        after,
        configure = this::configureByText,
        checkBefore = {},
        checkAfter = this::checkByText,
        preview = preview,
    )

    open fun checkFixAvailableInSelectionOnly(
        fixName: String,
        before: String,
        checkWarn: Boolean = true,
        checkInfo: Boolean = false,
        checkWeakWarn: Boolean = false,
    ) {
        configureByText(before.replace("<selection>", "<selection><caret>"))
        checkHighlighting(checkWarn, checkInfo, checkWeakWarn, ignoreExtraHighlighting = false)
        val selections = codeInsightFixture.editor.selectionModel.let { model ->
            model.blockSelectionStarts.zip(model.blockSelectionEnds)
                .map { TextRange(it.first, it.second + 1) }
        }
        for (pos in codeInsightFixture.file.text.indices) {
            codeInsightFixture.editor.caretModel.moveToOffset(pos)
            val expectAvailable = selections.any { it.contains(pos) }
            val isAvailable = codeInsightFixture.filterAvailableIntentions(fixName).size == 1
            check(isAvailable == expectAvailable) {
                "Expect ${if (expectAvailable) "available" else "unavailable"}, " +
                    "actually ${if (isAvailable) "available" else "unavailable"} " +
                    "at `${StringBuilder(codeInsightFixture.file.text).insert(pos, "/*caret*/")}`"
            }
        }
    }

    protected open fun <T> check(
        content: T,
        checkWarn: Boolean,
        checkInfo: Boolean,
        checkWeakWarn: Boolean,
        ignoreExtraHighlighting: Boolean,
        configure: (T) -> Unit,
    ) {
        if (content is String) {
            checkWarningFlags(content, checkWarn, checkWeakWarn)
        }
        configure(content)
        checkHighlighting(checkWarn, checkInfo, checkWeakWarn, ignoreExtraHighlighting)
    }

    private fun checkWarningFlags(content: String, checkWarn: Boolean, checkWeakWarn: Boolean) {
        if ("</warning>" in content || "/*warning**/" in content) {
            check(checkWarn) { "Use `checkWarn = true`" }
        }
        if ("</weak_warning>" in content || "/*weak_warning**/" in content) {
            check(checkWeakWarn) { "Use `checkWeakWarn = true`" }
        }
    }

    protected open fun checkFix(
        fixName: String,
        before: String,
        after: String,
        configure: (String) -> Unit,
        checkBefore: () -> Unit,
        checkAfter: (String) -> Unit,
        preview: Preview?,
    ) {
        configure(before)
        checkBefore()
        val previewChecker = applyQuickFix(fixName, preview)
        checkAfter(after)
        previewChecker.checkPreview()
    }

    protected open fun checkHighlighting(
        checkWarn: Boolean,
        checkInfo: Boolean,
        checkWeakWarn: Boolean,
        ignoreExtraHighlighting: Boolean,
    ) {
        codeInsightFixture.checkHighlighting(checkWarn, checkInfo, checkWeakWarn, ignoreExtraHighlighting)
    }

    open fun checkByText(text: String) {
        codeInsightFixture.checkResult(replaceCaretMarker(text.trimIndent()))
    }

    fun applyQuickFix(name: String, preview: Preview?): DeferredPreviewCheck {
        val action = codeInsightFixture.findSingleIntention(name)
        return if (!skipPreview(action)) {
            if (preview != null) {
                val previewText = (preview as? ExplicitPreview)?.text
                codeInsightFixture.checkPreviewAndLaunchAction(action, previewText, isWrappingActive)
            } else {
                val previewChecker = codeInsightFixture.checkNoPreview(action)
                codeInsightFixture.launchAction(action)
                previewChecker
            }
        } else {
            codeInsightFixture.launchAction(action)
            DeferredPreviewCheck.IgnorePreview
        }
    }

    private fun skipPreview(intention: IntentionAction): Boolean {
        val unwrapped = IntentionActionDelegate.unwrap(intention)
        return unwrapped is SuppressIntentionActionFromFix
    }

    open fun registerSeverities(severities: List<HighlightSeverity>) {
        val testSeverityProvider = TestSeverityProvider(severities)
        SeveritiesProvider.EP_NAME.point.registerExtension(testSeverityProvider, testRootDisposable)
    }
}

sealed interface Preview
object SamePreviewAsResult : Preview
class ExplicitPreview(@Language("Rust") val text: String): Preview
