/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.codeInsight.intention.FileModifier
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.ide.intentions.util.macros.IntentionInMacroUtil
import org.rust.lang.core.macros.findMacroCallExpandedFrom
import org.rust.lang.core.macros.isExpandedFromMacro

/**
 * A base class for implementing quick fixes.
 *
 * [org.rust.ide.intentions.RsElementBaseIntentionAction]
 */
abstract class RsQuickFixBase<E: PsiElement>(element: E) : LocalQuickFixAndIntentionActionOnPsiElement(element),
                                                           LocalQuickFix {
    abstract override fun getFamilyName(): String
    abstract override fun getText(): String

    override fun startInWriteAction(): Boolean = true
    override fun availableInBatchMode(): Boolean = true

    abstract fun invoke(project: Project, editor: Editor?, element: E)

    final override fun invoke(
        project: Project,
        file: PsiFile,
        editor: Editor?,
        startElement: PsiElement,
        endElement: PsiElement
    ) {
        if (startElement.isExpandedFromMacro) {
            invokeInsideMacroExpansion(project, editor, file, startElement)
        } else {
            @Suppress("UNCHECKED_CAST")
            invoke(project, editor, startElement as E)
        }
    }

    private fun invokeInsideMacroExpansion(
        project: Project,
        originalEditor: Editor?,
        originalFile: PsiFile,
        expandedElement: PsiElement
    ) {
        IntentionInMacroUtil.runActionInsideMacroExpansionCopy(
            project,
            originalEditor,
            originalFile,
            expandedElement
        ) { editorCopy, expandedElementCopy ->
            @Suppress("UNCHECKED_CAST")
            invoke(project, editorCopy, expandedElementCopy as E)
            return@runActionInsideMacroExpansionCopy true
        }
    }

    final override fun isAvailable(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement): Boolean {
        return super.isAvailable(project, file, startElement, endElement)
    }

    final override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
        super.invoke(project, file, startElement, endElement)
    }

    final override fun isAvailable(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement): Boolean {
        return super.isAvailable(project, file, editor, startElement, endElement)
    }

    final override fun applyFix(): Unit = super.applyFix()

    // TODO investigate why `AutoImportFix` needs to override it and make it final again
//    final override fun isAvailable(): Boolean = super.isAvailable()

    override fun getElementToMakeWritable(currentFile: PsiFile): PsiElement? {
        if (!startInWriteAction()) return null
        val element = super.getStartElement()
        val macroCall = element.findMacroCallExpandedFrom()
        val originalContainingFile = if (macroCall != null) {
            macroCall.containingFile
        } else {
            element.containingFile
        }

        return if (originalContainingFile == currentFile.originalFile) {
            // Intention preview
            currentFile
        } else {
            originalContainingFile
        }
    }

    override fun getFileModifierForPreview(target: PsiFile): FileModifier? {
        return if (!super.getStartElement().isExpandedFromMacro) {
            super<LocalQuickFixAndIntentionActionOnPsiElement>.getFileModifierForPreview(target)
        } else {
            // Check field safety in subclass
            if (super<LocalQuickFix>.getFileModifierForPreview(target) !== this) return null
            this
        }
    }

    @Deprecated("In the case of a macro, this method returns a wrong PSI element", ReplaceWith("element"))
    final override fun getStartElement(): PsiElement? = super.getStartElement()

    @Deprecated("It is always null", ReplaceWith("null"))
    final override fun getEndElement(): PsiElement? = null
}


