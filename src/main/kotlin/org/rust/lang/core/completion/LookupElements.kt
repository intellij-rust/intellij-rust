package org.rust.lang.core.completion

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.type.resolvedType

fun RustNamedElement.createLookupElement(): LookupElement {
    return when (this) {
        is RustFnItem -> LookupElementBuilder.createWithIcon(this)
            .withLookupString(name ?: "")
            .withTailText(parameters?.text ?: "()")
            .withTypeText(retType?.type?.text ?: "()")
        is RustImplMethodMember -> LookupElementBuilder.createWithIcon(this)
            .withLookupString(name ?: "")
            .withTailText(parameters?.text ?: "()")
            .withTypeText(retType?.type?.text ?: "()")
        is RustTraitMethodMember -> LookupElementBuilder.createWithIcon(this)
            .withLookupString(name ?: "")
            .withTailText(parameters?.text ?: "()")
            .withTypeText(retType?.type?.text ?: "()")
        is RustConstItem -> LookupElementBuilder.createWithIcon(this)
            .withLookupString(name ?: "")
            .withTypeText(type.text)
        is RustStaticItem -> LookupElementBuilder.createWithIcon(this)
            .withLookupString(name ?: "")
            .withTypeText(type.text)
        is RustPath -> {
            // re-export needs to be resolved
            val resolved = reference.resolve()
            resolved?.createLookupElement() ?: LookupElementBuilder.createWithIcon(this)
        }
        is RustUseGlob -> {
            // re-export needs to be resolved
            val resolved = reference?.resolve()
            resolved?.createLookupElement() ?: LookupElementBuilder.createWithIcon(this)
        }
        is RustStructItem -> {
            val tailText =
                if (structDeclArgs != null) " { ... }"
                else if (structTupleArgs != null) structTupleArgs?.text ?: ""
                else ""
            LookupElementBuilder.createWithIcon(this)
                .withLookupString(name ?: "")
                .withTailText(tailText)
        }
        is RustEnumVariant -> {
            val tailText =
                if (enumStructArgs != null) " { ... }"
                else if (enumTupleArgs != null) enumTupleArgs?.tupleFieldDeclList?.map { it.type.text }?.joinToString(prefix = "(", postfix = ")") ?: ""
                else ""
            LookupElementBuilder.createWithIcon(this)
                .withLookupString(name ?: "")
                .withTypeText(parentOfType<RustEnumItem>()?.name?.toString() ?: "")
                .withTailText(tailText)
        }
        is RustFieldDecl -> LookupElementBuilder.createWithIcon(this)
            .withLookupString(name ?: "")
            .withTypeText(type?.text ?: "")
        else -> LookupElementBuilder.createWithIcon(this).withLookupString(name ?: "")
    }
}
