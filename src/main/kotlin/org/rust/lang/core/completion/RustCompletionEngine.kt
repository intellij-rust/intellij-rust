package org.rust.lang.core.completion

import com.intellij.codeInsight.lookup.LookupElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.mixin.basePath
import org.rust.lang.core.psi.impl.mixin.asRustPath
import org.rust.lang.core.psi.util.fields
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.resolve.RustResolveEngine
import org.rust.lang.core.resolve.SearchFor
import org.rust.lang.core.resolve.indexes.RustImplIndex
import org.rust.lang.core.resolve.scope.RustResolveScope
import org.rust.lang.core.types.RustStructType
import org.rust.lang.core.types.util.resolvedType
import org.rust.lang.core.types.util.stripAllRefsIfAny

object RustCompletionEngine {
    fun complete(ref: RustPathElement): Array<out Any> =
        collectNamedElements(ref).toVariantsArray()

    fun completeFieldName(field: RustStructExprFieldElement): Array<out LookupElement> =
        field.parentOfType<RustStructExprElement>()
                ?.let       { it.fields }
                 .orEmpty()
                 .toVariantsArray()

    fun completeFieldOrMethod(field: RustFieldExprElement): Array<out LookupElement> {
        val dispatchType = field.expr.resolvedType.stripAllRefsIfAny()

        // Needs type ascription to please Kotlin's type checker, https://youtrack.jetbrains.com/issue/KT-12696.
        val fields: List<RustNamedElement> = (dispatchType as? RustStructType)?.item?.fields.orEmpty()

        val methods = RustImplIndex.findNonStaticMethodsFor(dispatchType, field.project)

        return (fields + methods.toList()).toVariantsArray()
    }

    fun completeUseGlob(glob: RustUseGlobElement): Array<out Any> =
        glob.basePath?.reference?.resolve()
            .completionsFromResolveScope()
            .toVariantsArray()

    private fun collectNamedElements(ref: RustPathElement): Collection<RustNamedElement> {
        // TODO: handle aliased items properly
        val path = ref.asRustPath ?: return emptyList()

        return if (path.segments.isNotEmpty()) {
            val qual = path.copy(segments = path.segments.subList(0, path.segments.size - 1))
            RustResolveEngine.resolve(qual, ref).element.completionsFromResolveScope()
        } else {
            RustResolveEngine.enumerateScopesFor(ref)
                .flatMap { RustResolveEngine.declarations(it, pivot = ref) }
                .toList()
        }
    }

}

private fun RustNamedElement?.completionsFromResolveScope(): Collection<RustNamedElement> =
    when (this) {
        is RustResolveScope -> RustResolveEngine.declarations(this, searchFor = SearchFor.PRIVATE).toList()
        else                -> emptyList()
    }

private fun Collection<RustNamedElement>.toVariantsArray(): Array<out LookupElement> =
    filter { it.name != null }.map { it.createLookupElement() }.toTypedArray()
