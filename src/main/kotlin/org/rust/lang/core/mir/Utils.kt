/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir

import com.intellij.psi.PsiElement
import org.rust.lang.core.mir.schemas.MirCastTy
import org.rust.lang.core.mir.schemas.MirOperand
import org.rust.lang.core.mir.schemas.MirRvalue
import org.rust.lang.core.mir.schemas.MirSpan
import org.rust.lang.core.psi.RsBindingMode
import org.rust.lang.core.psi.ext.ArithmeticOp
import org.rust.lang.core.thir.LocalVar
import org.rust.lang.core.types.regions.Scope
import org.rust.lang.core.types.regions.ScopeTree
import org.rust.lang.core.types.ty.*

val Ty.isSigned: Boolean
    get() = this is TyInteger.I8
        || this is TyInteger.I16
        || this is TyInteger.I32
        || this is TyInteger.I64
        || this is TyInteger.I128
        || this is TyInteger.ISize

val TyInteger.minValue: Long
    get() {
        assert(isSigned)
        // TODO
        return Int.MIN_VALUE.toLong()
    }

val PsiElement.asSpan: MirSpan get() = MirSpan.Full(this)
val PsiElement.asStartSpan: MirSpan get() = MirSpan.Start(this)

val Ty.needsDrop: Boolean
    get() {
        // TODO: it's just a dummy impl
        return when (this) {
            is TyPrimitive -> false
            is TyTuple -> types.any { it.needsDrop }
            is TyAdt -> false // TODO: usually not false actually
            is TyReference -> false
            is TyFunctionBase -> false
            else -> true
        }
    }

val ArithmeticOp.isCheckable: Boolean
    get() = this == ArithmeticOp.ADD
        || this == ArithmeticOp.SUB
        || this == ArithmeticOp.MUL
        || this == ArithmeticOp.SHL
        || this == ArithmeticOp.SHR

val Scope.span: MirSpan
    get() { // TODO: it can be more complicated in case of remainder
        return element.asSpan
    }

/**
 * This class exists because in case of `let x = 3` there is no binding mode created in PSI
 */
@JvmInline
value class RsBindingModeWrapper(private val bindingMode: RsBindingMode?) {
    val mut: PsiElement? get() = bindingMode?.mut
    val ref: PsiElement? get() = bindingMode?.ref

    val mutability: Mutability get() = if (mut == null) Mutability.IMMUTABLE else Mutability.MUTABLE
}

val RsBindingMode?.wrapper get() = RsBindingModeWrapper(this)

fun ScopeTree.getVariableScope(variable: LocalVar): Scope? {
    return when (variable) {
        is LocalVar.FromPatBinding -> getVariableScope(variable.pat)
        is LocalVar.FromSelfParameter -> getVariableScope(variable.self)
    }
}

// compiler uses ty and calls MirCastTy.from two times, but let's not
fun MirRvalue.Cast.Companion.create(
    fromTy: MirCastTy?,
    castTy: MirCastTy?,
    operand: MirOperand,
    ty: Ty,
): MirRvalue.Cast = when {
    fromTy is MirCastTy.Int && castTy is MirCastTy.Int -> MirRvalue.Cast.IntToInt(operand, ty)
    else -> TODO()
}
