/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.childrenOfType

class MacroExpander(project: Project) {
    val psiFactory = RsPsiFactory(project)

    fun expandMacro(def: RsMacroDefinition, call: RsMacroCall): List<ExpansionResult>? {
        val case = def.macroDefinitionBodyStubbed
            ?.macroDefinitionCaseList
            ?.singleOrNull()
            ?: return null

        val patGroup = case.macroPattern.macroBindingGroupList.singleOrNull()
            ?: return null
        val patBinding = patGroup.macroBindingList.singleOrNull()
            ?: return null
        val name = patBinding.colon.prevSibling?.text
        val type = patBinding.colon.nextSibling?.text
        if (!(name != null && type != null && type == "item")) return null

        val expGroup = case.macroExpansion.macroExpansionReferenceGroupList.singleOrNull()
            ?: return null
        val expansionText = expGroup.textBetweenParens(expGroup.lparen, expGroup.rparen)?.toString() ?: return null

        val items = parseBodyAsItemList(call) ?: return null
        val newText = items.joinToString("\n\n") { item ->
            expansionText.replace("$" + name, item.text)
        }

        val expandedFile = psiFactory.createFile(newText)

        return expandedFile.childrenOfType()
    }


    private val RsMacroDefinition.macroDefinitionBodyStubbed: RsMacroDefinitionBody?
        get() {
            val stub = stub ?: return macroDefinitionBody
            val text = stub.macroBody ?: return null
            return CachedValuesManager.getCachedValue(this) {
                CachedValueProvider.Result.create(
                    psiFactory.createMacroDefinitionBody(text),
                    PsiModificationTracker.MODIFICATION_COUNT
                )
            }
        }

    private fun parseBodyAsItemList(call: RsMacroCall): List<RsElement>? {
        val text = call.macroArgument?.braceListBodyText() ?: return null
        val file = psiFactory.createFile(text) as? RsFile ?: return null
        return file.childrenOfType()
    }
}

private fun PsiElement.braceListBodyText(): CharSequence? =
    textBetweenParens(firstChild, lastChild)

private fun PsiElement.textBetweenParens(bra: PsiElement?, ket: PsiElement?): CharSequence? {
    if (bra == null || ket == null || bra == ket) return null
    return containingFile.text.subSequence(
        bra.textRange.endOffset + 1,
        ket.textRange.startOffset
    )
}
