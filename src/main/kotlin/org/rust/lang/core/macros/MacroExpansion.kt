/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil
import org.rust.lang.RsFileType
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsMacroCall
import org.rust.lang.core.psi.RsMacroDefinition
import org.rust.lang.core.psi.RsMacroDefinitionBody
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.descendantOfTypeStrict
import org.rust.lang.core.psi.ext.macroName

fun expandMacro(call: RsMacroCall): List<ExpansionResult>? {
    val context = call.context as? RsElement ?: return null
    val result = when {
        call.macroName == "lazy_static" -> expandLazyStatic(call)?.let { listOf(it) }
        else -> {
            val def = call.reference.resolve() as? RsMacroDefinition ?: return null
            expandMacro(def, call)
        }
    }
    result?.forEach { it.setContext(context) }
    return result
}

fun createMacroExpansionFile(project: Project, text: String): RsFile {
    return PsiFileFactory.getInstance(project)
        .createFileFromText("EXPANSION_MACRO.rs", RsFileType, text)
        as RsFile
}

inline fun <reified I : RsElement> createInMemoryPsi(project: Project, code: String): I? {
    return createMacroExpansionFile(project, code)
        .descendantOfTypeStrict()
}

private val RsMacroDefinition.body: RsMacroDefinitionBody?
    get() {
        val stub = stub ?: return macroDefinitionBody
        val text = stub.macroBody ?: return null
        return createInMemoryPsi(project, "macro_rules m! $text")
    }

private fun expandMacro(def: RsMacroDefinition, call: RsMacroCall): List<ExpansionResult>? {
    val case = def.body?.
        macroDefinitionCaseList?.singleOrNull()
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

    val expandedFile = PsiFileFactory.getInstance(call.project)
        .createFileFromText(
            "MACRO.rs",
            RsFileType,
            newText
        ) as? RsFile ?: return null

    val ctx = call.context as? RsElement ?: return null
    return PsiTreeUtil.getChildrenOfTypeAsList(expandedFile, ExpansionResult::class.java)
        .onEach { it.setContext(ctx) }
}

fun parseBodyAsItemList(call: RsMacroCall): List<RsElement>? {
    val text = call.macroArgument?.braceListBodyText() ?: return null
    val file = PsiFileFactory.getInstance(call.project).createFileFromText(
        "macro_scratch_space.rs",
        RsFileType,
        text
    ) as? RsFile ?: return null

    return PsiTreeUtil.getChildrenOfTypeAsList(file, RsElement::class.java)
}

private fun PsiElement.braceListBodyText(): CharSequence? {
    return textBetweenParens(firstChild, lastChild)
}

private fun PsiElement.textBetweenParens(bra: PsiElement?, ket: PsiElement?): CharSequence? {
    if (bra == null || ket == null || bra == ket) return null
    return containingFile.text.subSequence(
        bra.textRange.endOffset + 1,
        ket.textRange.startOffset
    )
}
