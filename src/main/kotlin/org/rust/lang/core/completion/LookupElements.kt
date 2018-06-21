/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.psi.util.PsiTreeUtil
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.ty.TyUnknown
import org.rust.lang.core.types.type

const val KEYWORD_PRIORITY = 10.0
private const val MACRO_PRIORITY = -0.1

fun createLookupElement(element: RsElement, scopeName: String): LookupElement {
    val base = element.getLookupElementBuilder(scopeName)
        .withInsertHandler { context: InsertionContext, _ -> getInsertHandler(element, scopeName, context) }

    if (element is RsMacroDefinition) return base.withPriority(MACRO_PRIORITY)

    return base
}


fun LookupElementBuilder.withPriority(priority: Double): LookupElement =
    PrioritizedLookupElement.withPriority(this, priority)

private fun RsElement.getLookupElementBuilder(scopeName: String): LookupElementBuilder {
    val base = LookupElementBuilder.create(this, scopeName)
        .withIcon(if (this is RsFile) RsIcons.MODULE else this.getIcon(0))

    return when (this) {
        is RsMod -> if (scopeName == "self" || scopeName == "super") {
            base.withTailText("::")
        } else {
            base
        }

        is RsConstant -> base
            .withTypeText(typeReference?.text)
        is RsFieldDecl -> base.withTypeText(typeReference?.text)
        is RsTraitItem -> base

        is RsFunction -> base
            .withTypeText(retType?.typeReference?.text ?: "()")
            .withTailText(valueParameterList?.text?.replace("\\s+".toRegex(), " ") ?: "()")
            .appendTailText(extraTailText, true)

        is RsStructItem -> base
            .withTailText(when {
                blockFields != null -> " { ... }"
                tupleFields != null -> tupleFields!!.text
                else -> ""
            })

        is RsEnumVariant -> base
            .withTypeText(ancestorStrict<RsEnumItem>()?.name ?: "")
            .withTailText(when {
                blockFields != null -> " { ... }"
                tupleFields != null ->
                    tupleFields!!.tupleFieldDeclList.joinToString(prefix = "(", postfix = ")") { it.typeReference.text }
                else -> ""
            })

        is RsPatBinding -> base
            .withTypeText(type.let {
                when (it) {
                    is TyUnknown -> ""
                    else -> it.toString()
                }
            })

        is RsMacroBinding -> base.withTypeText(fragmentSpecifier)

        is RsMacroDefinition -> base.withTailText("!")

        else -> base
    }
}

private fun getInsertHandler(element: RsElement, scopeName: String, context: InsertionContext) {
    val curUseItem = context.getItemOfType<RsUseItem>()
    when (element) {

        is RsMod -> if (scopeName == "self" || scopeName == "super") {
            val offset = context.tailOffset - 1
            val inSelfParam = PsiTreeUtil.findElementOfClassAtOffset(context.file, offset, RsSelfParameter::class.java, false) != null
            if (!(context.isInUseGroup || inSelfParam)) {
                context.addSuffix("::")
            }
        }

        is RsConstant -> appendSemicolon(context, curUseItem)
        is RsTraitItem -> appendSemicolon(context, curUseItem)
        is RsStructItem -> appendSemicolon(context, curUseItem)

        is RsFunction -> {
            if (curUseItem != null) {
                appendSemicolon(context, curUseItem);
            } else {
                if (!context.alreadyHasCallParens) {
                    context.document.insertString(context.selectionEndOffset, "()")
                }
                EditorModificationUtil.moveCaretRelatively(context.editor, if (element.valueParameters.isEmpty()) 2 else 1)
                if (!element.valueParameters.isEmpty()) {
                    AutoPopupController.getInstance(element.project)?.autoPopupParameterInfo(context.editor, element)
                }
            }
        }

        is RsEnumVariant -> {
            if (curUseItem == null) {
                val (text, shift) = when {
                    element.tupleFields != null -> Pair("()", 1)
                    element.blockFields != null -> Pair(" {}", 2)
                    else -> Pair("", 0)
                }
                if (!(context.alreadyHasPatternParens || context.alreadyHasCallParens)) {
                    context.document.insertString(context.selectionEndOffset, text)
                }
                EditorModificationUtil.moveCaretRelatively(context.editor, shift)
            }
        }

        is RsMacroDefinition -> {
            val parens = when (element.name) {
                "vec" -> "[]"
                else -> "()"
            }
            context.document.insertString(context.selectionEndOffset, "!$parens")
            EditorModificationUtil.moveCaretRelatively(context.editor, 2)
        }

    }
}


private fun appendSemicolon(context: InsertionContext, curUseItem: RsUseItem?) {
    if (curUseItem != null) {
        val hasSemicolon = curUseItem.lastChild!!.elementType == RsElementTypes.SEMICOLON
        if (!(hasSemicolon || context.isInUseGroup)) {
            context.addSuffix(";")
        }
    }
}

private inline fun <reified T : RsItemElement> InsertionContext.getItemOfType(strict: Boolean = false): T? =
    PsiTreeUtil.findElementOfClassAtOffset(this.file, this.tailOffset - 1, T::class.java, strict)

private val InsertionContext.isInUseGroup: Boolean
    get() = PsiTreeUtil.findElementOfClassAtOffset(file, tailOffset - 1, RsUseGroup::class.java, false) != null

private val InsertionContext.alreadyHasCallParens: Boolean
    get() = nextCharIs('(')

private val InsertionContext.alreadyHasPatternParens: Boolean
    get() {
        val pat = file.findElementAt(startOffset)!!.ancestorStrict<RsPatEnum>()
            ?: return false
        return pat.path.textRange.contains(startOffset)
    }

private val RsFunction.extraTailText: String
    get() = ancestorStrict<RsImplItem>()?.traitRef?.text?.let { " of $it" } ?: ""


fun InsertionContext.nextCharIs(c: Char): Boolean =
    document.charsSequence.indexOfSkippingSpace(c, tailOffset) != null

private fun CharSequence.indexOfSkippingSpace(c: Char, startIndex: Int): Int? {
    for (i in startIndex until this.length) {
        val currentChar = this[i]
        if (c == currentChar) return i
        if (currentChar != ' ' && currentChar != '\t') return null
    }
    return null
}
