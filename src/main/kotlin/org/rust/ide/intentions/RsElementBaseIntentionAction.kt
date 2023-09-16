/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.codeInsight.intention.BaseElementAtCaretIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.ide.experiments.RsExperiments
import org.rust.ide.intentions.util.macros.IntentionInMacroUtil
import org.rust.ide.intentions.util.macros.InvokeInside
import org.rust.lang.core.macros.findExpansionElements
import org.rust.lang.core.psi.RsMacroArgument
import org.rust.lang.core.psi.ext.ancestorOrSelf
import org.rust.lang.core.psi.ext.isIntentionPreviewElement
import org.rust.lang.core.psi.ext.startOffset
import org.rust.openapiext.checkReadAccessAllowed
import org.rust.openapiext.checkWriteAccessAllowed
import org.rust.openapiext.isFeatureEnabled

/**
 * A base class for implementing intentions: actions available via "light bulb" / `Alt+Enter`.
 *
 * The cool thing about intentions is their UX: there is a huge number of intentions,
 * and they all can be invoked with a single `Alt + Enter` shortcut. This is possible
 * because at the position of the cursor only small number of intentions is applicable.
 *
 * So, intentions consists of two functions: [findApplicableContext] functions determines
 * if the intention can be applied at the given position, it is used to populate "light bulb" list.
 * [invoke] is called when the user selects the intention from the list and must apply the changes.
 *
 * The context collected by [findApplicableContext] is gathered into [Ctx] object and is passed to
 * [invoke]. In general, [invoke] should be infallible: if you need to check if some element is not
 * null, do this in [findApplicableContext] and pass the element via the context.
 *
 * [findApplicableContext] is executed under a read action, and [invoke] under a write action.
 */
abstract class RsElementBaseIntentionAction<Ctx> : BaseElementAtCaretIntentionAction() {

    /**
     * Return `null` if the intention is not applicable, otherwise collect and return
     * all the necessary info to actually apply the intention.
     */
    abstract fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Ctx?

    abstract fun invoke(project: Project, editor: Editor, ctx: Ctx)

    final override fun invoke(project: Project, editor: Editor, element: PsiElement) {
        if (startInWriteAction() && !element.isIntentionPreviewElement) {
            checkWriteAccessAllowed()
        }

        val possiblyExpandedElement = findTargetElement(element) ?: return
        if (possiblyExpandedElement != element) {
            invokeInsideMacroExpansion(project, editor, element.containingFile, possiblyExpandedElement)
        } else {
            invokeInner(project, editor, element)
        }
    }

    private fun findTargetElement(element: PsiElement): PsiElement? {
        val expandedElements = element.findExpansionElements()
        return if (expandedElements == null) {
            // The element is NOT inside a macro call - use the original element
            element
        } else {
            // The element is inside a macro call
            val isFunctionLike = element.ancestorOrSelf<RsMacroArgument>() != null
            val strategy = if (isFunctionLike) functionLikeMacroHandlingStrategy else attributeMacroHandlingStrategy
            if (strategy == InvokeInside.MACRO_CALL) return element
            if (isFunctionLike && !isFeatureEnabled(RsExperiments.INTENTIONS_IN_FN_LIKE_MACROS)) return element
            // Use the expanded element if it is single; The intention is unavailable if
            // there are multiple or none expanded elements (e.g. if the macro expansion is failed)
            expandedElements.singleOrNull()
        }
    }

    private fun invokeInner(project: Project, editor: Editor, element: PsiElement) {
        val ctx = findApplicableContext(project, editor, element) ?: return
        invoke(project, editor, ctx)
    }

    private fun invokeInsideMacroExpansion(
        project: Project,
        originalEditor: Editor,
        originalFile: PsiFile,
        expandedElement: PsiElement
    ) {
        IntentionInMacroUtil.runActionInsideMacroExpansionCopy(
            project,
            originalEditor,
            originalFile,
            expandedElement
        ) { editorCopy, expandedElementCopy ->
            invokeInner(project, editorCopy, expandedElementCopy)
            return@runActionInsideMacroExpansionCopy true
        }
    }

    final override fun isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean {
        checkReadAccessAllowed()
        val possiblyExpandedElement = findTargetElement(element) ?: return false
        return if (possiblyExpandedElement != element) {
            IntentionInMacroUtil.doActionAvailabilityCheckInsideMacroExpansion(
                editor,
                element.containingFile,
                possiblyExpandedElement,
                possiblyExpandedElement.startOffset + (editor.caretModel.offset - element.startOffset)
            ) { fakeEditor ->
                findApplicableContext(project, fakeEditor, possiblyExpandedElement) != null
            }
        } else {
            findApplicableContext(project, editor, possiblyExpandedElement) != null
        }
    }

    /**
     * Controls how the intention action behaves inside an attribute macro call:
     * ```
     * #[a_macro_foo]
     * fn foo() {
     *     /*caret*/
     * }
     * ```
     *
     * If it returns [InvokeInside.MACRO_CALL] then the original PSI element under the caret
     * is passed to [findApplicableContext]. If it returns [InvokeInside.MACRO_EXPANSION] then
     * mapped element inside the expansion of macro `a_macro_foo` is passed to [findApplicableContext]
     * (see [PsiElement.findExpansionElements]).
     *
     * Choose [InvokeInside.MACRO_CALL] if the intention is syntax-based and does not involve
     * name resolution or type inference, or if the intention handles macros by itself
     */
    open val attributeMacroHandlingStrategy: InvokeInside get() = InvokeInside.MACRO_EXPANSION

    /**
     * Controls how the intention action behaves inside a function-like macro call:
     * ```
     * foo! {
     *     foo /*caret*/bar baz
     * }
     * ```
     *
     * If it returns [InvokeInside.MACRO_CALL] then the original PSI element under the caret
     * is passed to [findApplicableContext]. If it returns [InvokeInside.MACRO_EXPANSION] then
     * mapped element inside the expansion of macro `foo` is passed to [findApplicableContext]
     * (see [PsiElement.findExpansionElements]).
     *
     * Choose [InvokeInside.MACRO_CALL] if the intention is text-based and does not need a rich PSI
     * structure, or if the intention handles macros by itself
     */
    open val functionLikeMacroHandlingStrategy: InvokeInside get() = InvokeInside.MACRO_EXPANSION
}
