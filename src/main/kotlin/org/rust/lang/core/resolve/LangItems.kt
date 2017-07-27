/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.ty.*

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
