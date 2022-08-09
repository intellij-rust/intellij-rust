/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.utils.import

import org.rust.ide.settings.RsCodeInsightSettings
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.psi.RsTypeReference
import org.rust.lang.core.psi.RsVisitor
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.RsQualifiedNamedElement
import org.rust.lang.core.psi.ext.typeParameters
import org.rust.lang.core.resolve.TYPES
import org.rust.lang.core.resolve.createProcessor
import org.rust.lang.core.resolve.processNestedScopesUpwards
import org.rust.lang.core.types.Substitution
import org.rust.lang.core.types.emptySubstitution
import org.rust.lang.core.types.infer.TypeVisitor
import org.rust.lang.core.types.infer.substitute
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.type

object RsImportHelper {
    fun importTypeReferencesFromElements(
        context: RsElement,
        elements: Collection<RsElement>,
        subst: Substitution = emptySubstitution,
        useAliases: Boolean = true,
        skipUnchangedDefaultTypeArguments: Boolean = true
    ) {
        val (toImport, _) = getTypeReferencesInfoFromElements(context, elements, subst, useAliases, skipUnchangedDefaultTypeArguments)
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
        val importContext = ImportContext2.from(context, ImportContext2.Type.OTHER) ?: return
        for (element in elements) {
            val candidate = ImportCandidatesCollector2.findImportCandidate(importContext, element)
            candidate?.import(context)
        }
    }

    // finds path to `element` from `context.containingMod`, taking into account reexports and glob imports
    fun findPath(context: RsElement, element: RsQualifiedNamedElement): String? {
        val importContext = ImportContext2.from(context, ImportContext2.Type.OTHER) ?: return null
        val candidate = ImportCandidatesCollector2.findImportCandidate(importContext, element)
        return candidate?.info?.usePath
    }

    /**
     * Traverses type references in `elements` and collects all items that unresolved in current context.
     */
    private fun getTypeReferencesInfoFromElements(
        context: RsElement,
        elements: Collection<RsElement>,
        subst: Substitution,
        useAliases: Boolean,
        skipUnchangedDefaultTypeArguments: Boolean
    ): TypeReferencesInfo = getTypeReferencesInfo(context, elements) { ty, result ->
        collectImportSubjectsFromTypeReferences(ty, subst, result, useAliases, skipUnchangedDefaultTypeArguments)
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
        subst: Substitution,
        result: MutableSet<RsQualifiedNamedElement>,
        useAliases: Boolean,
        skipUnchangedDefaultTypeArguments: Boolean
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

            override fun visitTypeReference(reference: RsTypeReference) =
                collectImportSubjectsFromTy(reference.type, subst, result, useAliases, skipUnchangedDefaultTypeArguments)

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
                                .dropLastWhile { (argumentTy, param) -> argumentTy.isEquivalentTo(param.typeReference?.type) }
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
        val importSubjects = hashMapOf<String, MutableSet<RsQualifiedNamedElement>>()
        for (element in rawImportSubjects) {
            val name = element.name ?: continue
            importSubjects.getOrPut(name, ::hashSetOf) += element
        }

        val toQualifiedName = hashSetOf<RsQualifiedNamedElement>()
        val processor = createProcessor { entry ->
            val group = importSubjects.remove(entry.name) ?: return@createProcessor false
            group.remove(entry.element)
            toQualifiedName.addAll(group)
            importSubjects.isEmpty()
        }
        processNestedScopesUpwards(context, TYPES, processor)

        return TypeReferencesInfo(importSubjects.flatMap { it.value }.toSet(), toQualifiedName)
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
