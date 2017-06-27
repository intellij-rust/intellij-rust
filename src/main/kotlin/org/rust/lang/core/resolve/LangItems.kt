/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.indexes.RsImplIndex
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.infer.remapTypeParameters
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.type

enum class StdDerivableTrait(val modName: String) {
    Clone("clone"),
    Copy("marker"),
    Debug("fmt"),
    Default("default"),
    Eq("cmp"),
    Hash("hash"),
    Ord("cmp"),
    PartialEq("cmp"),
    PartialOrd("cmp")
}

val STD_DERIVABLE_TRAITS: Map<String, StdDerivableTrait> = StdDerivableTrait.values().associate { it.name to it }

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
    val stub = stub
    if (stub != null) return stub.langAttribute
    return queryAttributes.langAttribute
}

private val RsTraitItem.isDeref: Boolean get() = langAttribute == "deref"
private val RsTraitItem.isIndex: Boolean get() = langAttribute == "index"
val RsTraitItem.isAnyFnTrait: Boolean get() = langAttribute == "fn"
    || langAttribute == "fn_once"
    || langAttribute == "fn_mut"

val RsTraitItem.fnTypeArgsParam: TyTypeParameter? get() {
    if (!isAnyFnTrait) return null
    val param = typeParameterList?.typeParameterList?.singleOrNull() ?: return null
    return TyTypeParameter(param)
}

val BoundElement<RsTraitItem>.asFunctionType: TyFunction? get() {
    val param = element.fnTypeArgsParam ?: return null
    val argumentTypes = ((typeArguments[param] ?: TyUnknown) as? TyTuple)?.types.orEmpty()
    return TyFunction(argumentTypes, TyUnknown)
}

private fun lookupAssociatedType(impl: RsImplItem, name: String): Ty =
    impl.typeAliasList.find { it.name == name }?.typeReference?.type
        ?: TyUnknown

// This is super hackish. Need to figure out how to
// identify known ty (See also the CString inspection).
// Java uses fully qualified names for this, perhaps we
// can do this as well? Will be harder to test though :(
fun isStdResult(type: Ty): Boolean {
    return type is TyEnum && type.item.name == "Result";
}

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

fun findStdVec(elementType: Ty, elementForModule: RsCompositeElement): Ty {
    val ty = tyFromAbsolutePath("std", "collections", "vec::Vec", elementForModule);

    val typeParameter = ty.getTypeParameter("T") ?: return ty
    return ty.substitute(mapOf(typeParameter to elementType));
}

fun findStdString(elementForModule: RsCompositeElement): Ty {
    return tyFromAbsolutePath("std", "collections", "string::String", elementForModule);
}

fun findStdArguments(elementForModule: RsCompositeElement): Ty {
    return tyFromAbsolutePath("std", "core", "fmt::Arguments", elementForModule);
}
