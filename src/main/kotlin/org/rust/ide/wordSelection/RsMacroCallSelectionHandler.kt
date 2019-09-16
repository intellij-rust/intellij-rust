/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.wordSelection

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase
import com.intellij.codeInsight.editorActions.SelectWordUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.rust.lang.core.macros.findExpansionElements
import org.rust.lang.core.macros.findMacroCallExpandedFromNonRecursive
import org.rust.lang.core.macros.mapRangeFromExpansionToCallBodyStrict
import org.rust.lang.core.psi.RsMacroArgument
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psi.ext.expansion
import org.rust.lang.core.psi.ext.startOffset

class RsMacroCallSelectionHandler : ExtendWordSelectionHandlerBase() {
    override fun canSelect(e: PsiElement): Boolean = e.ancestorStrict<RsMacroArgument>() != null

    override fun select(e: PsiElement, editorText: CharSequence, cursorOffset: Int, editor: Editor): List<TextRange>? {
        val elementInExpansion = e.findExpansionElements()?.firstOrNull() ?: return null
        val offsetInExpansion = elementInExpansion.startOffset + (cursorOffset - e.startOffset)
        val macroCall = elementInExpansion.findMacroCallExpandedFromNonRecursive() ?: return null // impossible?
        val expansion = macroCall.expansion ?: return null // impossible?
        val expansionText = expansion.file.text
        val factory = EditorFactory.getInstance()
        val expansionEditor = factory.createEditor(factory.createDocument(expansionText))

        val ranges = mutableListOf<TextRange>()
        try {
            SelectWordUtil.processRanges(elementInExpansion, expansionText, offsetInExpansion, expansionEditor) {
                ranges.add(it)
                false // Continue processing (do not believe `Processor`'s docs)
            }
        } finally {
            factory.releaseEditor(expansionEditor)
        }
        return ranges.mapNotNull { macroCall.mapRangeFromExpansionToCallBodyStrict(it) }
    }
}
