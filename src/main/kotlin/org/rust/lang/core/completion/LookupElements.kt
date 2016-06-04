package org.rust.lang.core.completion

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.RustPathElementImpl
import org.rust.lang.core.psi.impl.RustUseGlobElementImpl
import org.rust.lang.core.psi.util.parentOfType

fun RustNamedElement.createLookupElement(): LookupElement {
    return when (this) {
        is RustFnItemElement -> LookupElementBuilder.createWithIcon(this)
            .withLookupString(name ?: "")
            .withTailText(parameters?.text ?: "()")
            .withTypeText(retType?.type?.text ?: "()")
        is RustImplMethodMemberElement -> LookupElementBuilder.createWithIcon(this)
            .withLookupString(name ?: "")
            .withTailText(parameters?.text ?: "()")
            .withTypeText(retType?.type?.text ?: "()")
        is RustTraitMethodMemberElement -> LookupElementBuilder.createWithIcon(this)
            .withLookupString(name ?: "")
            .withTailText(parameters?.text ?: "()")
            .withTypeText(retType?.type?.text ?: "()")
        is RustConstItemElement -> LookupElementBuilder.createWithIcon(this)
            .withLookupString(name ?: "")
            .withTypeText(type.text)
        is RustStaticItemElement -> LookupElementBuilder.createWithIcon(this)
            .withLookupString(name ?: "")
            .withTypeText(type.text)
        is RustPathElementImpl -> {
            // re-export needs to be resolved
            val resolved = reference.resolve()
            resolved?.createLookupElement() ?: LookupElementBuilder.createWithIcon(this)
        }
        is RustUseGlobElement -> {
            // There is a problem here with ambiguous getReference(): RustReference.
            // Completely possible this is a compiler issue. Not sure why though.
            val that = this as RustNamedElement
            // re-export needs to be resolved
            val resolved = that.reference?.resolve()
            resolved?.createLookupElement() ?: LookupElementBuilder.createWithIcon(this)
        }
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
        else -> LookupElementBuilder.createWithIcon(this).withLookupString(name ?: "")
    }
}
