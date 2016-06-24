package org.rust.lang.core.completion

import com.intellij.codeInsight.completion.util.ParenthesesInsertHandler
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.util.parentOfType

fun RustNamedElement.createLookupElement(): LookupElement {
    return when (this) {
        is RustFnItemElement -> LookupElementBuilder.createWithIcon(this)
            .withLookupString(name ?: "")
            .withTailText(parameters?.text ?: "()")
            .withTypeText(retType?.type?.text ?: "()")
            .withInsertHandler(ParenthesesInsertHandler.getInstance((this.parameters?.parameterList?.isNotEmpty() ?: false)))
        is RustImplMethodMemberElement -> LookupElementBuilder.createWithIcon(this)
            .withLookupString(name ?: "")
            .withTailText(parameters?.text ?: "()")
            .withTypeText(retType?.type?.text ?: "()")
            .withInsertHandler(ParenthesesInsertHandler.getInstance((this.parameters?.parameterList?.isNotEmpty() ?: false)))
        is RustTraitMethodMemberElement -> LookupElementBuilder.createWithIcon(this)
            .withLookupString(name ?: "")
            .withTailText(parameters?.text ?: "()")
            .withTypeText(retType?.type?.text ?: "()")
            .withInsertHandler(ParenthesesInsertHandler.getInstance((this.parameters?.parameterList?.isNotEmpty() ?: false)))
        is RustConstItemElement -> LookupElementBuilder.createWithIcon(this)
            .withLookupString(name ?: "")
            .withTypeText(type.text)
        is RustStaticItemElement -> LookupElementBuilder.createWithIcon(this)
            .withLookupString(name ?: "")
            .withTypeText(type.text)
        is RustStructItemElement -> {
            val tailText =
                if (structDeclArgs != null) " { ... }"
                else if (structTupleArgs != null) structTupleArgs?.text ?: ""
                else ""
            LookupElementBuilder.createWithIcon(this)
                .withLookupString(name ?: "")
                .withTailText(tailText)
        }
        is RustEnumVariantElement -> {
            val tailText =
                if (enumStructArgs != null) " { ... }"
                else if (enumTupleArgs != null) enumTupleArgs?.tupleFieldDeclList?.map { it.type.text }?.joinToString(prefix = "(", postfix = ")") ?: ""
                else ""
            LookupElementBuilder.createWithIcon(this)
                .withLookupString(name ?: "")
                .withTypeText(parentOfType<RustEnumItemElement>()?.name?.toString() ?: "")
                .withTailText(tailText)
        }
        is RustFieldDeclElement -> LookupElementBuilder.createWithIcon(this)
            .withLookupString(name ?: "")
            .withTypeText(type?.text ?: "")
        is RustMod -> {
            val n = modName
            if (n != null) {
                LookupElementBuilder.create(this, n).withIcon(getIcon(0)).withInsertHandler { context, element ->
                    val editor = context.editor
                    val doc = context.document
                    context.commitDocument()
                    if (context.completionChar == '\t') {
                        doc.insertString(editor.caretModel.offset, "::")
                        editor.caretModel.moveToOffset(editor.caretModel.offset + 2)
                    }
                }
            } else {
                LookupElementBuilder.createWithIcon(this)
            }
        }
        else -> LookupElementBuilder.createWithIcon(this).withLookupString(name ?: "")
    }
}
