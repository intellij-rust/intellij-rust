package org.rust.lang.core.resolve

import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.indexes.RsImplIndex
import org.rust.lang.core.types.infer.remapTypeParameters
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.type

fun findDerefTarget(project: Project, ty: Ty): Ty? {
    val impls = findImplsAndTraits(project, ty).first
    for ((impl, subst) in impls) {
        val trait = impl.traitRef?.resolveToTrait ?: continue
        if (!trait.isDeref) continue
        return lookupAssociatedType(impl, "Target")
            .substitute(subst)
    }
    return null
}

fun findIteratorItemType(project: Project, ty: Ty): Ty {
    val impl = findImplsAndTraits(project, ty).first
        .find { boundImpl ->
            val traitName = boundImpl.element.traitRef?.resolveToTrait?.name
            traitName == "Iterator" || traitName == "IntoIterator"
        } ?: return TyUnknown

    val rawType = lookupAssociatedType(impl.element, "Item")
    return rawType.substitute(impl.typeArguments)
}

fun findIndexOutputType(project: Project, containerType: Ty, indexType: Ty): Ty {
    val impls = RsImplIndex.findImpls(project, containerType)
        .filter { it.traitRef?.resolveToTrait?.isIndex ?: false }

    val suitableImpl = if (impls.size < 2) {
        impls.firstOrNull()
    } else {
        impls.find { isImplSuitable(project, it, "index", 0, indexType) }
    } ?: return TyUnknown

    val rawOutputType = lookupAssociatedType(suitableImpl, "Output")
    val typeParameterMap = suitableImpl.remapTypeParameters(containerType.typeParameterValues)
    return TyReference(rawOutputType.substitute(typeParameterMap))
}

fun findArithmeticBinaryExprOutputType(project: Project, lhsType: Ty, rhsType: Ty, op: ArithmeticOp): Ty {
    val impls = RsImplIndex.findImpls(project, lhsType)
        .filter { op.itemName == it.traitRef?.resolveToTrait?.langAttribute }

    val suitableImpl = if (impls.size < 2) {
        impls.firstOrNull()
    } else {
        impls.find { isImplSuitable(project, it, op.itemName, 0, rhsType) }
    } ?: return TyUnknown

    val rawOutputType = lookupAssociatedType(suitableImpl, "Output")
    val typeParameterMap = suitableImpl.remapTypeParameters(lhsType.typeParameterValues)
    return rawOutputType.substitute(typeParameterMap)
}

private fun isImplSuitable(project: Project, impl: RsImplItem,
                           fnName: String, paramIndex: Int, paramType: Ty): Boolean {
    return impl.functionList
        .find { it.name == fnName }
        ?.valueParameterList
        ?.valueParameterList
        ?.getOrNull(paramIndex)
        ?.typeReference
        ?.type
        ?.canUnifyWith(paramType, project) ?: false
}

private val RsTraitItem.langAttribute: String? get() {
    if (this.stub != null) return this.stub.langAttribute
    return this.queryAttributes.langAttribute
}

private val RsTraitItem.isDeref: Boolean get() = langAttribute == "deref"
private val RsTraitItem.isIndex: Boolean get() = langAttribute == "index"

private fun lookupAssociatedType(impl: RsImplItem, name: String): Ty =
    impl.typeAliasList.find { it.name == name }?.typeReference?.type
        ?: TyUnknown

private fun tyFromAbsolutePath(prefixStd: String, prefixNoStd: String, name: String, elementForModule: RsCompositeElement): Ty {
    val module = elementForModule.module ?: return TyUnknown;
    val crateRoot = elementForModule.crateRoot as? RsFile ?: return TyUnknown
    val prefix = if (crateRoot.attributes == RsFile.Attributes.NONE) prefixStd else prefixNoStd;
    val fullName = prefix + "::" + name;

    val (element, _) = resolveStringPath(fullName, module) ?: return TyUnknown;
    return (element as? RsTypeBearingItemElement)?.type ?: TyUnknown;
}

fun findStdRange(rangeName: String, indexType: Ty?, elementForModule: RsCompositeElement): Ty {
    val ty = tyFromAbsolutePath("std", "core", "ops::" + rangeName, elementForModule);

    if (indexType == null)
        return ty;

    val typeParameter = ty.getTypeParameter("Idx") ?: return ty;
    return ty.substitute(mapOf(typeParameter to indexType));
}
