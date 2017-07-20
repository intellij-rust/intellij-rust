/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.type

val RsTraitItem.langAttribute: String? get() = queryAttributes.langAttribute

val RsTraitItem.isAnyFnTrait: Boolean get() = langAttribute == "fn"
    || langAttribute == "fn_once"
    || langAttribute == "fn_mut"

val RsTraitItem.fnTypeArgsParam: TyTypeParameter? get() {
    if (!isAnyFnTrait) return null
    val param = typeParameterList?.typeParameterList?.singleOrNull() ?: return null
    return TyTypeParameter(param)
}

val RsTraitItem.fnOutputParam: TyTypeParameter? get() {
    if (!isAnyFnTrait) return null
    return TyTypeParameter(this, "Output")
}

val BoundElement<RsTraitItem>.asFunctionType: TyFunction? get() {
    val param = element.fnTypeArgsParam ?: return null
    val outputParam = element.fnOutputParam ?: return null
    val argumentTypes = ((subst[param] ?: TyUnknown) as? TyTuple)?.types.orEmpty()
    val outputType = (subst[outputParam] ?: TyUnit)
    return TyFunction(argumentTypes, outputType)
}

// This is super hackish. Need to figure out how to
// identify known ty (See also the CString inspection).
// Java uses fully qualified names for this, perhaps we
// can do this as well? Will be harder to test though :(
fun isStdResult(type: Ty): Boolean {
    return type is TyEnum && type.item.name == "Result"
}

private fun tyFromAbsolutePath(prefixStd: String, prefixNoStd: String, name: String, elementForModule: RsCompositeElement): Ty {
    val module = elementForModule.module ?: return TyUnknown
    val crateRoot = elementForModule.crateRoot as? RsFile ?: return TyUnknown
    val prefix = if (crateRoot.attributes == RsFile.Attributes.NONE) prefixStd else prefixNoStd
    val fullName = prefix + "::" + name

    val (element, _) = resolveStringPath(fullName, module) ?: return TyUnknown
    return (element as? RsTypeBearingItemElement)?.type ?: TyUnknown
}

fun findStdRange(rangeName: String, indexType: Ty?, elementForModule: RsCompositeElement): Ty {
    val ty = tyFromAbsolutePath("std", "core", "ops::" + rangeName, elementForModule)

    if (indexType == null)
        return ty

    val typeParameter = ty.getTypeParameter("Idx") ?: return ty
    return ty.substitute(mapOf(typeParameter to indexType))
}

fun findStdVec(elementType: Ty, elementForModule: RsCompositeElement): Ty {
    val ty = tyFromAbsolutePath("std", "collections", "vec::Vec", elementForModule)

    val typeParameter = ty.getTypeParameter("T") ?: return ty
    return ty.substitute(mapOf(typeParameter to elementType))
}

fun findStdString(elementForModule: RsCompositeElement): Ty {
    return tyFromAbsolutePath("std", "collections", "string::String", elementForModule)
}

fun findStdArguments(elementForModule: RsCompositeElement): Ty {
    return tyFromAbsolutePath("std", "core", "fmt::Arguments", elementForModule)
}
