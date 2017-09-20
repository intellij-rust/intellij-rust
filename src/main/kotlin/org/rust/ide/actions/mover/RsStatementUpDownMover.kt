/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions.mover

import com.intellij.codeInsight.editorActions.moveUpDown.LineRange
import com.intellij.codeInsight.editorActions.moveUpDown.StatementUpDownMover
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import org.rust.ide.utils.findElementAtIgnoreWhitespaceAfter
import org.rust.ide.utils.findElementAtIgnoreWhitespaceBefore
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.ancestors
import org.rust.lang.core.psi.ext.elementType

val PsiElement.line: Int? get() = containingFile.viewProvider.document?.getLineNumber(textRange.startOffset)

open class RsStatementUpDownMover : StatementUpDownMover() {
    open val containers: List<IElementType> = listOf(
        RsElementTypes.FUNCTION,
        RsElementTypes.MOD_ITEM
    )
    open val jumpOver: List<IElementType> = listOf(
        RsElementTypes.FUNCTION,
        RsElementTypes.TRAIT_ITEM,
        RsElementTypes.IMPL_ITEM,
        RsElementTypes.MACRO_CALL,
        RsElementTypes.STRUCT_ITEM,
        RsElementTypes.MACRO_DEFINITION,
        RsElementTypes.EXTERN_CRATE_ITEM,
        RsElementTypes.USE_ITEM,
        RsElementTypes.MOD_ITEM
    )

    open fun collectedElement(element: PsiElement): Pair<PsiElement, List<Int>>? {
        val possibleTypes = listOf(
            RsElementTypes.TRAIT_ITEM,
            RsElementTypes.IMPL_ITEM,
            RsElementTypes.MACRO_CALL,
            RsElementTypes.MACRO_DEFINITION,
            RsElementTypes.STRUCT_ITEM,
            RsElementTypes.MOD_ITEM,
            RsElementTypes.USE_ITEM
        )
        val collectedElement = element.ancestors
            .takeWhile { it !is RsFile }
            .firstOrNull { parent -> possibleTypes.any { parent.elementType == it } } ?: return null
        val firstLine = collectedElement.line
        val secondLine = when (collectedElement) {
            is RsImplItem -> collectedElement.impl.line
            is RsTraitItem -> collectedElement.trait.line
            is RsMacroCall -> collectedElement.referenceNameElement.line
            is RsMacroDefinition -> collectedElement.nameIdentifier?.line
            is RsModItem -> collectedElement.mod.line
            is RsStructItem -> collectedElement.struct?.line
            else -> null
        }
        return collectedElement to listOf(firstLine, secondLine).mapNotNull { it }
    }

    private fun calculateStartLine(editor: Editor, down: Boolean, range: LineRange): Int? {
        val line = if (!down) {
            range.startLine - 1
        } else {
            range.endLine + 1
        }
        if (line < 0 || line >= editor.document.lineCount) {
            return null
        }
        return line
    }

    private fun findStartElement(editor: Editor, file: PsiFile, line: Int, down: Boolean): PsiElement? {
        val offset = editor.document.getLineStartOffset(line)

        return if (!down) {
            file.findElementAtIgnoreWhitespaceBefore(offset)
        } else {
            file.findElementAtIgnoreWhitespaceAfter(offset)
        }
    }

    private fun lookupForElement(startElement: PsiElement, collectedElement: PsiElement): PsiElement? {
        var element: PsiElement? = startElement
        while (element != null && element !is RsFile) {
            if (checkContainerOutOfBound(element, collectedElement)) {
                return null
            }
            if (jumpOver.any { element?.elementType == it }) {
                break
            }
            val parent = element.parent
            if (parent is RsFile) {
                break
            }
            element = parent
        }
        return element
    }

    private fun checkContainerOutOfBound(element: PsiElement, collectedElement: PsiElement): Boolean {
        return containers.any { element.elementType == it } && collectedElement.parent.ancestors
            .takeWhile { it !is RsFile }
            .any { it == element }
    }

    private fun calculateMoveToRange(
        element: PsiElement,
        down: Boolean,
        collectedElement: PsiElement,
        line: Int,
        range: LineRange
    ): LineRange {
        var toMove2 = LineRange(element)
        if (down) {
            if (toMove2.startLine - 1 == range.endLine || element == collectedElement) {
                toMove2 = LineRange(line - 1, line)
            }
        } else {
            if (toMove2.startLine == range.startLine) {
                toMove2 = LineRange(line, line + 1)
            }
        }
        return toMove2
    }

    override fun checkAvailable(editor: Editor, file: PsiFile, info: MoveInfo, down: Boolean): Boolean {
        if (file !is RsFile) {
            return false
        }

        val selectionRange = getLineRangeFromSelection(editor)
        val start = file.findElementAtIgnoreWhitespaceBefore(
            editor.document.getLineStartOffset(selectionRange.startLine)
        ) ?: return false
        val (collectedElement, possibleStartLines) = collectedElement(start) ?: return false
        if (!possibleStartLines.contains(selectionRange.startLine)) {
            return false
        }
        val range = LineRange(collectedElement)
        info.toMove = range

        val line = calculateStartLine(editor, down, range) ?: return info.prohibitMove()
        val startElement = findStartElement(editor, file, line, down) ?: return info.prohibitMove()
        val element = lookupForElement(startElement, collectedElement) ?: return info.prohibitMove()
        info.toMove2 = calculateMoveToRange(element, down, collectedElement, line, range)
        return true
    }
}
