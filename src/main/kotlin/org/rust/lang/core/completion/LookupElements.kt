package org.rust.lang.core.completion

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.completion.util.ParenthesesInsertHandler
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import org.rust.cargo.util.cargoProject
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.RustFile
import org.rust.lang.core.psi.util.module
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
            if (this is RustFile && isCrateRoot) {
                // This is an external crate, so we need to resolve it from the module name.
                val externCrateName = module?.cargoProject?.packages?.find {
                    it.contentRoot == virtualFile.parent
                }?.name

                LookupElementBuilder.create(this, externCrateName ?: modName ?: name)
                    .withIcon(getIcon(0))
                    .withInsertHandler(ModInsertHandler)
            } else {
                val name = modName ?: name ?: "<anonymous>"
                LookupElementBuilder.create(this, name)
                    .withIcon(getIcon(0))
                    .withInsertHandler(ModInsertHandler)
            }
        }
        else -> LookupElementBuilder.createWithIcon(this).withLookupString(name ?: "")
    }
}

object ModInsertHandler : InsertHandler<LookupElement> {
    override fun handleInsert(context: InsertionContext?, item: LookupElement?) {
        if (context == null) return
        if (item == null) return

        val editor = context.editor
        val doc = context.document
        context.commitDocument()
        if (context.completionChar == '\t') {
            doc.insertString(editor.caretModel.offset, "::")
            editor.caretModel.moveToOffset(editor.caretModel.offset + 2)
        }
    }
}
