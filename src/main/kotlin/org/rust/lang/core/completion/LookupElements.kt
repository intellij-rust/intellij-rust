/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.rust.ide.icons.RsIcons
import org.rust.ide.presentation.stubOnlyText
import org.rust.ide.refactoring.RsNamesValidator
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.ty.TyUnknown
import org.rust.lang.core.types.type

const val KEYWORD_PRIORITY = 10.0
private const val ENUM_VARIANT_PRIORITY = 4.0
private const val FIELD_DECL_PRIORITY = 3.0
private const val INHERENT_IMPL_MEMBER_PRIORITY = 2.0
private const val DEFAULT_PRIORITY = 0.0
private const val MACRO_PRIORITY = -0.1

fun createLookupElement(
    element: RsElement,
    scopeName: String,
    locationString: String? = null,
    insertHandler: InsertHandler<LookupElement> = RsDefaultInsertHandler()
): LookupElement {
    val base = element.getLookupElementBuilder(scopeName)
        .withInsertHandler(insertHandler)
        .let { if (locationString != null) it.appendTailText(" ($locationString)", true) else it }

    val priority = when {
        element is RsMacro -> MACRO_PRIORITY
        element is RsEnumVariant -> ENUM_VARIANT_PRIORITY
        element is RsNamedFieldDecl -> FIELD_DECL_PRIORITY
        element is RsAbstractable && element.owner.isInherentImpl -> INHERENT_IMPL_MEMBER_PRIORITY
        else -> DEFAULT_PRIORITY
    }

    return base.withPriority(priority)
}

fun LookupElementBuilder.withPriority(priority: Double): LookupElement =
    if (priority == DEFAULT_PRIORITY) this else PrioritizedLookupElement.withPriority(this, priority)

private fun RsElement.getLookupElementBuilder(scopeName: String): LookupElementBuilder {
    val base = LookupElementBuilder.createWithSmartPointer(scopeName, this)
        .withIcon(if (this is RsFile) RsIcons.MODULE else this.getIcon(0))

    return when (this) {
        is RsMod -> if (scopeName == "self" || scopeName == "super" || scopeName == "crate") {
            base.withTailText("::")
        } else {
            base
        }

        is RsConstant -> base
            .withTypeText(typeReference?.stubOnlyText)
        is RsNamedFieldDecl -> base.withTypeText(typeReference?.stubOnlyText)
        is RsTraitItem -> base

        is RsFunction -> base
            .withTypeText(retType?.typeReference?.stubOnlyText ?: "()")
            .withTailText(valueParameterList?.stubOnlyText ?: "()")
            .appendTailText(extraTailText, true)

        is RsStructItem -> base
            .withTailText(getFieldsOwnerTailText(this))

        is RsEnumVariant -> base
            .withTypeText(ancestorStrict<RsEnumItem>()?.name ?: "")
            .withTailText(getFieldsOwnerTailText(this))

        is RsPatBinding -> base
            .withTypeText(type.let {
                when (it) {
                    is TyUnknown -> ""
                    else -> it.toString()
                }
            })

        is RsMacroBinding -> base.withTypeText(fragmentSpecifier)

        is RsMacro -> base.withTailText("!")

        else -> base
    }
}

private fun getFieldsOwnerTailText(owner: RsFieldsOwner): String = when {
    owner.blockFields != null -> " { ... }"
    owner.tupleFields != null ->
        owner.positionalFields.joinToString(prefix = "(", postfix = ")") { it.typeReference.stubOnlyText }
    else -> ""
}

class RsDefaultInsertHandler : InsertHandler<LookupElement> {

    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val element = item.psiElement as? RsElement ?: return
        val scopeName = item.lookupString

        val curUseItem = context.getElementOfType<RsUseItem>()
        if (element is RsNameIdentifierOwner && !RsNamesValidator.isIdentifier(scopeName) && scopeName !in CAN_NOT_BE_ESCAPED) {
            context.document.insertString(context.startOffset, RS_RAW_PREFIX)
        }
        when (element) {

            is RsMod -> {
                when (scopeName) {
                    "self",
                    "super" -> {
                        val inSelfParam = context.getElementOfType<RsSelfParameter>() != null
                        if (!(context.isInUseGroup || inSelfParam)) {
                            context.addSuffix("::")
                        }
                    }
                    "crate" -> context.addSuffix("::")
                }
            }

            is RsConstant -> appendSemicolon(context, curUseItem)
            is RsTraitItem -> appendSemicolon(context, curUseItem)
            is RsStructItem -> appendSemicolon(context, curUseItem)

            is RsFunction -> {
                if (curUseItem != null) {
                    appendSemicolon(context, curUseItem)
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
                    // Currently this works only for enum variants (and not for structs). It's because in the case of
                    // struct you may want to append an associated function call after a struct name (e.g. `::new()`)
                    val (text, shift) = when {
                        element.tupleFields != null -> Pair("()", 1)
                        element.blockFields != null -> Pair(" {}", 2)
                        else -> Pair("", 0)
                    }
                    if (!(context.alreadyHasStructBraces || context.alreadyHasCallParens)) {
                        context.document.insertString(context.selectionEndOffset, text)
                    }
                    EditorModificationUtil.moveCaretRelatively(context.editor, shift)
                }
            }

            is RsMacro -> {
                if (curUseItem == null) {
                    val parens = when (element.name) {
                        "vec" -> "[]"
                        else -> "()"
                    }
                    context.document.insertString(context.selectionEndOffset, "!$parens")
                    EditorModificationUtil.moveCaretRelatively(context.editor, 2)
                } else {
                    appendSemicolon(context, curUseItem)
                }
            }
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

inline fun <reified T : PsiElement> InsertionContext.getElementOfType(strict: Boolean = false): T? =
    PsiTreeUtil.findElementOfClassAtOffset(file, tailOffset - 1, T::class.java, strict)

private val InsertionContext.isInUseGroup: Boolean
    get() = getElementOfType<RsUseGroup>() != null

private val InsertionContext.alreadyHasCallParens: Boolean
    get() = nextCharIs('(')

private val InsertionContext.alreadyHasStructBraces: Boolean
    get() = nextCharIs('{')

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
