package org.rust.lang.core.completion

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.mixin.asRustPath
import org.rust.lang.core.psi.impl.mixin.basePath
import org.rust.lang.core.psi.util.fields
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.resolve.*
import org.rust.lang.core.resolve.indexes.RustImplIndex
import org.rust.lang.core.resolve.scope.RustResolveScope
import org.rust.lang.core.types.RustStructType
import org.rust.lang.core.types.util.resolvedType
import org.rust.lang.core.types.util.stripAllRefsIfAny

object RustCompletionEngine {
    fun completePath(ref: RustPathElement, namespace: Namespace?): Array<out LookupElement> {
        val path = ref.asRustPath ?: return emptyArray()

        return if (path.segments.isNotEmpty()) {
            val qual = path.copy(segments = path.segments.subList(0, path.segments.size - 1))
            RustResolveEngine.resolve(qual, ref, Namespace.Types).element.completionsFromResolveScope()
        } else {
            RustResolveEngine.enumerateScopesFor(ref)
                .flatMap { RustResolveEngine.declarations(it, pivot = ref) }
                .filterByNamespace(namespace)
                .completionsFromScopeEntries()
        }
    }

    fun completeUseGlob(glob: RustUseGlobElement): Array<out LookupElement> =
        glob.basePath?.reference?.resolve()
            .completionsFromResolveScope()

    fun completeFieldName(field: RustStructExprFieldElement): Array<out LookupElement> =
        field.parentOfType<RustStructExprElement>()
            ?.fields.orEmpty()
            .completionsFromNamedElements()

    fun completeFieldOrMethod(field: RustFieldExprElement): Array<out LookupElement> {
        val dispatchType = field.expr.resolvedType.stripAllRefsIfAny()

        // Needs type ascription to please Kotlin's type checker, https://youtrack.jetbrains.com/issue/KT-12696.
        val fields: List<RustNamedElement> = (dispatchType as? RustStructType)?.item?.fields.orEmpty()

        val methods = RustImplIndex.findNonStaticMethodsFor(dispatchType, field.project)

        return (fields + methods.toList()).completionsFromNamedElements()
    }
}

private fun RustNamedElement?.completionsFromResolveScope(): Array<LookupElement> =
    if (this is RustResolveScope)
        RustResolveEngine.declarations(this, searchFor = SearchFor.PRIVATE).completionsFromScopeEntries()
    else
        emptyArray()

private fun Sequence<ScopeEntry>.completionsFromScopeEntries(): Array<LookupElement> =
    mapNotNull {
        it.element?.createLookupElement(it.name)
    }.toList().toTypedArray()

private fun Collection<RustNamedElement>.completionsFromNamedElements(): Array<LookupElement> =
    mapNotNull {
        val name = it.name ?: return@mapNotNull null
        it.createLookupElement(name)
    }.toTypedArray()

fun RustNamedElement.createLookupElement(scopeName: String): LookupElement {
    val base = LookupElementBuilder.create(this, scopeName)
        .withIcon(getIcon(0))

    return when (this) {
        is RustConstItemElement -> base.withTypeText(type?.text)
        is RustStaticItemElement -> base.withTypeText(type?.text)
        is RustFieldDeclElement -> base.withTypeText(type?.text)

        is RustFnElement -> base
            .withTypeText(retType?.type?.text ?: "()")
            .withTailText(parameters?.text?.replace("\\s+".toRegex(), " ") ?: "()")

        is RustStructItemElement -> base
            .withTailText(when {
                blockFields != null -> " { ... }"
                tupleFields != null -> tupleFields!!.text
                else -> ""
            })

        is RustEnumVariantElement -> base
            .withTypeText(parentOfType<RustEnumItemElement>()?.name?.toString() ?: "")
            .withTailText(when {
                blockFields != null -> " { ... }"
                tupleFields != null ->
                    tupleFields!!.tupleFieldDeclList.map { it.type.text }.joinToString(prefix = "(", postfix = ")")
                else -> ""
            })

        else -> base
    }
}
