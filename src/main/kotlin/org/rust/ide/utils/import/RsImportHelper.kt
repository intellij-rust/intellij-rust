/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.utils.import

import org.rust.ide.settings.RsCodeInsightSettings
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.psi.RsVisitor
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.RsQualifiedNamedElement
import org.rust.lang.core.psi.ext.typeParameters
import org.rust.lang.core.resolve.*
import org.rust.lang.core.types.Substitution
import org.rust.lang.core.types.emptySubstitution
import org.rust.lang.core.types.infer.TypeVisitor
import org.rust.lang.core.types.infer.substitute
import org.rust.lang.core.types.normType
import org.rust.lang.core.types.ty.*
import org.rust.stdext.intersects

object RsImportHelper {

    fun importTypeReferencesFromElement(context: RsElement, element: RsElement) =
        importTypeReferencesFromElements(context, listOf(element))

    private fun importTypeReferencesFromElements(context: RsElement, elements: Collection<RsElement>) {
        val (toImport, _) = getTypeReferencesInfoFromElements(context, elements)
        importElements(context, toImport)
    }

    fun importTypeReferencesFromTys(
        context: RsElement,
        tys: Collection<Ty>,
        useAliases: Boolean = true,
        skipUnchangedDefaultTypeArguments: Boolean = true
    ) {
        val (toImport, _) = getTypeReferencesInfoFromTys(
            context,
            *tys.toTypedArray(),
            useAliases = useAliases,
            skipUnchangedDefaultTypeArguments = skipUnchangedDefaultTypeArguments
        )
        importElements(context, toImport)
    }

    fun importTypeReferencesFromTy(
        context: RsElement,
        ty: Ty,
        useAliases: Boolean = true,
        skipUnchangedDefaultTypeArguments: Boolean = true
    ) {
        importTypeReferencesFromTys(context, listOf(ty), useAliases, skipUnchangedDefaultTypeArguments)
    }

    fun importElement(context: RsElement, element: RsQualifiedNamedElement) =
        importElements(context, setOf(element))

    fun importElements(context: RsElement, elements: Set<RsQualifiedNamedElement>) {
        if (!RsCodeInsightSettings.getInstance().importOutOfScopeItems) return
        val importContext = ImportContext.from(context, ImportContext.Type.OTHER) ?: return
        for (element in elements) {
            val candidate = ImportCandidatesCollector.findImportCandidate(importContext, element)
            candidate?.import(context)
        }
    }

    // finds path to `element` from `context.containingMod`, taking into account reexports and glob imports
    fun findPath(context: RsElement, element: RsQualifiedNamedElement): String? {
        val importContext = ImportContext.from(context, ImportContext.Type.OTHER) ?: return null
        val candidate = ImportCandidatesCollector.findImportCandidate(importContext, element)
        return candidate?.info?.usePath
    }

    /**
     * Traverses type references in `elements` and collects all items that unresolved in current context.
     */
    private fun getTypeReferencesInfoFromElements(
        context: RsElement,
        elements: Collection<RsElement>,
    ): TypeReferencesInfo = getTypeReferencesInfo(context, elements) { ty, result ->
        collectImportSubjectsFromTypeReferences(ty, result)
    }

    /**
     * Traverse types in `elemTy` and collects all items that unresolved in current context.
     */
    fun getTypeReferencesInfoFromTys(
        context: RsElement,
        vararg elemTys: Ty,
        useAliases: Boolean = true,
        skipUnchangedDefaultTypeArguments: Boolean = true
    ): TypeReferencesInfo = getTypeReferencesInfo(context, elemTys.toList()) { ty, result ->
        collectImportSubjectsFromTy(ty, emptySubstitution, result, useAliases, skipUnchangedDefaultTypeArguments)
    }

    private fun <T> getTypeReferencesInfo(
        context: RsElement,
        elements: Collection<T>,
        collector: (T, MutableSet<RsQualifiedNamedElement>) -> Unit
    ): TypeReferencesInfo {
        val result = hashSetOf<RsQualifiedNamedElement>()
        elements.forEach { collector(it, result) }
        return processRawImportSubjects(context, result)
    }

    private fun collectImportSubjectsFromTypeReferences(
        context: RsElement,
        result: MutableSet<RsQualifiedNamedElement>,
    ) {
        context.accept(object : RsVisitor() {
            override fun visitPath(path: RsPath) {
                val qualifier = path.path
                if (qualifier == null) {
                    val item = path.reference?.resolve() as? RsQualifiedNamedElement
                    if (item != null) {
                        result += item
                    }
                }
                super.visitPath(path)
            }

            override fun visitElement(element: RsElement) =
                element.acceptChildren(this)
        })
    }

    private fun collectImportSubjectsFromTy(
        ty: Ty,
        subst: Substitution,
        result: MutableSet<RsQualifiedNamedElement>,
        useAliases: Boolean,
        skipUnchangedDefaultTypeArguments: Boolean
    ) {
        ty.substitute(subst).visitWith(object : TypeVisitor {
            override fun visitTy(ty: Ty): Boolean {
                val alias = ty.aliasedBy?.element.takeIf { useAliases } as? RsQualifiedNamedElement
                if (alias != null) {
                    result += alias
                    return true
                }

                when (ty) {
                    is TyAdt -> {
                        result += ty.item

                        if (skipUnchangedDefaultTypeArguments) {
                            val filteredTypeArguments = ty.typeArguments
                                .zip(ty.item.typeParameters)
                                .dropLastWhile { (argumentTy, param) -> argumentTy.isEquivalentTo(param.typeReference?.normType) }
                                .map { (argumentTy, _) -> argumentTy }
                            return ty.copy(typeArguments = filteredTypeArguments).superVisitWith(this)
                        }
                    }
                    is TyAnon -> result += ty.traits.map { it.element }
                    is TyTraitObject -> result += ty.traits.map { it.element }
                    is TyProjection -> {
                        result += ty.trait.element
                        result += ty.target
                    }
                }
                return ty.superVisitWith(this)
            }
        })
    }

    /**
     * Takes `rawImportSubjects` and filters items that are unresolved in the current `context`.
     * Then splits the items into two sets:
     * - Items that should be imported
     * - Items that can't be imported
     */
    private fun processRawImportSubjects(
        context: RsElement,
        rawImportSubjects: Set<RsQualifiedNamedElement>
    ): TypeReferencesInfo {
        val subjectsWithName = rawImportSubjects.associateWithTo(hashMapOf()) { it.name }
        val processor = createProcessor { entry ->
            val element = entry.element ?: return@createProcessor false
            if (subjectsWithName[element] == entry.name) {
                subjectsWithName.remove(element)
            }
            subjectsWithName.isEmpty()
        }
        val itemsInScope = hashMapOf<String, Set<Namespace>>()
        processWithShadowingAndUpdateScope(itemsInScope, TYPES_N_VALUES, processor) {
            processNestedScopesUpwards(context, TYPES_N_VALUES, it)
        }

        val toImport = hashSetOf<RsQualifiedNamedElement>()
        val toQualify = hashSetOf<RsQualifiedNamedElement>()
        for ((item, name) in subjectsWithName) {
            val existingNs = itemsInScope[name ?: continue]
            if (existingNs != null && existingNs.intersects(item.namespaces)) {
                toQualify += item
            } else {
                toImport += item
            }
        }
        return TypeReferencesInfo(toImport, toQualify)
    }
}

/**
 * @param toImport  Set of unresolved items that should be imported
 * @param toQualify Set of unresolved items that can't be imported
 */
data class TypeReferencesInfo(
    val toImport: Set<RsQualifiedNamedElement>,
    val toQualify: Set<RsQualifiedNamedElement>
)
