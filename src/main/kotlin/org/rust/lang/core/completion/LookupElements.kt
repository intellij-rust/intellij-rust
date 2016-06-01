package org.rust.lang.core.completion

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.type.resolvedType

fun RustNamedElement.createLookupElement(): LookupElement {
    return when (this) {
        is RustFnItem            -> createLookupElement()
        is RustImplMethodMember  -> createLookupElement()
        is RustTraitMethodMember -> createLookupElement()
        is RustConstItem         -> createLookupElement()
        is RustStaticItem        -> createLookupElement()
        is RustPath              -> createLookupElement()
        is RustTraitItem         -> createLookupElement()
        is RustStructItem        -> createLookupElement()
        is RustEnumVariant       -> createLookupElement()
        is RustFieldDecl         -> createLookupElement()
        else                     -> LookupElementBuilder.createWithIcon(this).withLookupString(name ?: "")
    }
}

fun RustFnItem.createLookupElement(): LookupElement {
    return LookupElementBuilder.createWithIcon(this)
        .withLookupString(name ?: "")
        .withTailText(parameters?.text ?: "()")
        .withTypeText(retType?.type?.text ?: "()")
}

fun RustImplMethodMember.createLookupElement(): LookupElement {
    return LookupElementBuilder.createWithIcon(this)
        .withLookupString(name ?: "")
        .withTailText(parameters?.text ?: "()")
        .withTypeText(retType?.type?.text ?: "()")
}

fun RustTraitMethodMember.createLookupElement(): LookupElement {
    return LookupElementBuilder.createWithIcon(this)
        .withLookupString(name ?: "")
        .withTailText(parameters?.text ?: "()")
        .withTypeText(retType?.type?.text ?: "()")
}

fun RustConstItem.createLookupElement(): LookupElement {
    return LookupElementBuilder.createWithIcon(this)
        .withLookupString(name ?: "")
        .withTypeText(type.text)
}

fun RustStaticItem.createLookupElement(): LookupElement {
    return LookupElementBuilder.createWithIcon(this)
        .withLookupString(name ?: "")
        .withTypeText(type.text)
}

fun RustTraitItem.createLookupElement(): LookupElement {
    return LookupElementBuilder.createWithIcon(this)
        .withLookupString(name ?: "")
}

fun RustStructItem.createLookupElement(): LookupElement {
    val tailText =
        if (structDeclArgs != null) " { ... }"
        else if (structTupleArgs != null) structTupleArgs?.text ?: ""
        else ""
    return LookupElementBuilder.createWithIcon(this)
        .withLookupString(name ?: "")
        .withTailText(tailText)
}

fun RustEnumVariant.createLookupElement(): LookupElement {
    val tailText =
        if (enumStructArgs != null) " { ... }"
        else if (enumTupleArgs != null) enumTupleArgs?.tupleFieldDeclList?.map { it.type.text }?.joinToString(prefix = "(", postfix = ")") ?: ""
        else ""
    return LookupElementBuilder.createWithIcon(this)
        .withLookupString(name ?: "")
        .withTypeText(parentOfType<RustEnumItem>()?.name?.toString() ?: "")
        .withTailText(tailText)
}

fun RustFieldDecl.createLookupElement(): LookupElement {
    return LookupElementBuilder.createWithIcon(this)
        .withLookupString(name ?: "")
        .withTypeText(type?.text ?: "")
}

fun RustPath.createLookupElement(): LookupElement {
    // re-export needs to be resolved
    val resolved = reference.resolve()
    return resolved?.createLookupElement() ?: LookupElementBuilder.createWithIcon(this)
}
