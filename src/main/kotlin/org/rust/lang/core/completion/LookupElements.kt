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
    val base = LookupElementBuilder.create(element, scopeName)
        .withIcon(if (element is RsFile) RsIcons.MODULE else element.getIcon(0))

    return when (element) {
        is RsMod -> if (scopeName == "self" || scopeName == "super") {
            base.withTailText("::")
                .withInsertHandler({ ctx, _ ->
                    val offset = ctx.tailOffset - 1
                    val inSelfParam = PsiTreeUtil.findElementOfClassAtOffset(ctx.file, offset, RsSelfParameter::class.java, false) != null
                    if (!(ctx.isInUseGroup || inSelfParam)) {
                        ctx.addSuffix("::")
                    }
                })
        } else {
            base
        }

        is RsConstant -> base
            .withTypeText(element.typeReference?.text)
            .withInsertHandler({ context: InsertionContext, _ -> appendSemicolon(context) })
        is RsFieldDecl -> base.withTypeText(element.typeReference?.text)
        is RsTraitItem -> base.withInsertHandler({ context: InsertionContext, _ -> appendSemicolon(context) })

        is RsFunction -> base
            .withTypeText(element.retType?.typeReference?.text ?: "()")
            .withTailText(element.valueParameterList?.text?.replace("\\s+".toRegex(), " ") ?: "()")
            .appendTailText(element.extraTailText, true)
            .withInsertHandler handler@ { context: InsertionContext, _: LookupElement ->
                val curUseItem = context.getItemOfType<RsUseItem>()
                if (curUseItem != null) {
                    val hasSemicolon = curUseItem.lastChild!!.elementType == RsElementTypes.SEMICOLON
                    if (!(hasSemicolon || context.isInUseGroup)) {
                        context.addSuffix(";")
                    }
                    return@handler
                }

                if (!context.alreadyHasCallParens) {
                    context.document.insertString(context.selectionEndOffset, "()")
                }
                EditorModificationUtil.moveCaretRelatively(context.editor, if (element.valueParameters.isEmpty()) 2 else 1)
                if (!element.valueParameters.isEmpty()) {
                    AutoPopupController.getInstance(element.project)?.autoPopupParameterInfo(context.editor, element)
                }
            }

        is RsStructItem -> base
            .withTailText(when {
                element.blockFields != null -> " { ... }"
                element.tupleFields != null -> element.tupleFields!!.text
                else -> ""
            })
            .withInsertHandler({ context: InsertionContext, _: LookupElement -> appendSemicolon(context) })

        is RsEnumVariant -> base
            .withTypeText(element.ancestorStrict<RsEnumItem>()?.name ?: "")
            .withTailText(when {
                element.blockFields != null -> " { ... }"
                element.tupleFields != null ->
                    element.tupleFields!!.tupleFieldDeclList.joinToString(prefix = "(", postfix = ")") { it.typeReference.text }
                else -> ""
            })
            .withInsertHandler handler@ { context, _ ->
                if (context.getItemOfType<RsUseItem>() != null) return@handler
                val (text, shift) = when {
                    element.tupleFields != null -> Pair("()", 1)
                    element.blockFields != null -> Pair(" {}", 2)
                    else -> return@handler
                }
                if (!(context.alreadyHasPatternParens || context.alreadyHasCallParens)) {
                    context.document.insertString(context.selectionEndOffset, text)
                }
                EditorModificationUtil.moveCaretRelatively(context.editor, shift)
            }

        is RsPatBinding -> base
            .withTypeText(element.type.let {
                when (it) {
                    is TyUnknown -> ""
                    else -> it.toString()
                }
            })

        is RsMacroBinding -> base.withTypeText(element.fragmentSpecifier)

        is RsMacroDefinition -> {
            val parens = when (element.name) {
                "vec" -> "[]"
                else -> "()"
            }
            base
                .withTailText("!")
                .withInsertHandler { context: InsertionContext, _: LookupElement ->
                    context.document.insertString(context.selectionEndOffset, "!$parens")
                    EditorModificationUtil.moveCaretRelatively(context.editor, 2)
                }
                .withPriority(MACRO_PRIORITY)
        }
        else -> base
    }
}

fun LookupElementBuilder.withPriority(priority: Double): LookupElement =
    PrioritizedLookupElement.withPriority(this, priority)

private fun appendSemicolon(context: InsertionContext) {
    val curUseItem = context.getItemOfType<RsUseItem>()
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
