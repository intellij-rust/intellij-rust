/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.codeInsight.intention.BaseElementAtCaretIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.impl.PsiFileFactoryImpl
import com.intellij.testFramework.LightVirtualFile
import org.rust.lang.RsFileType
import org.rust.lang.RsLanguage
import org.rust.lang.core.macros.*
import org.rust.lang.core.psi.ext.bodyTextRange
import org.rust.lang.core.psi.ext.expansion
import org.rust.lang.core.psi.ext.startOffset
import org.rust.openapiext.checkReadAccessAllowed
import org.rust.openapiext.checkWriteAccessAllowed

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
        val expandedElement = element.findExpansionElementOrSelf()
        val isMacroExpansion = expandedElement != element

        data class Change(val origRange: TextRange, val newText: CharSequence)
        val changes = mutableListOf<Change>()
        lateinit var ranges: RangeMap

        val target = if (isMacroExpansion) {
            val macroCall = expandedElement.findMacroCallExpandedFromNonRecursive()
            val expansion = macroCall?.expansion!!
            ranges = generateSequence(macroCall) { macroCall.findMacroCallExpandedFromNonRecursive() }
                .toList()
                .asReversed()
                .map { it.expansion!!.ranges }
                .reduce { acc, range -> acc.mapAll(range) }
            val text = expansion.file.text
            val doc = DocumentImpl(text)

            doc.addDocumentListener(object : DocumentListener {
                override fun documentChanged(event: DocumentEvent) {
                    changes += Change(TextRange(event.offset, event.offset + event.oldLength), event.newFragment)
                }
            })

            val file = LightVirtualFile("foo.rs", RsFileType, text)
            FileDocumentManagerImpl.registerDocument(doc, file)
            val psi = (PsiFileFactory.getInstance(project) as PsiFileFactoryImpl).trySetupPsiForFile(file, RsLanguage, true, false)!!
            psi.findElementAt(expandedElement.startOffset)!!
        } else {
            element
        }
        val ctx = findApplicableContext(project, editor, target) ?: return

        if (startInWriteAction()) {
            checkWriteAccessAllowed()
        }

        invoke(project, editor, ctx)

        // TODO support multiple changes (mutate the range map)
        // TODO do reformat
        if (isMacroExpansion) {
            val start = expandedElement.findMacroCallExpandedFrom()!!.bodyTextRange!!.startOffset
            val changes = changes.map { change ->
                val range = if (change.origRange.length == 0) {
                    ranges.mapOffsetFromExpansionToCallBody(change.origRange.startOffset)
                        ?.let { TextRange(it, it) }
                } else {
                    ranges.mapTextRangeFromExpansionToCallBody(change.origRange)
                        .singleOrNull()
                        ?.srcRange
                }
                    ?.takeIf { it.length == change.origRange.length }
                    ?.shiftRight(start)
                    ?: return

                Change(range, change.newText)
            }

            for (change in changes) {
                editor.document.replaceString(change.origRange.startOffset, change.origRange.endOffset, change.newText)
            }
        }
    }

    final override fun isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean {
        checkReadAccessAllowed()
        return findApplicableContext(project, editor, element.findExpansionElementOrSelf()) != null
    }
}
