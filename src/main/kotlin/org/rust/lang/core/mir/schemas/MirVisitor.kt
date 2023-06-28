/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.schemas

import org.rust.lang.core.types.consts.Const
import org.rust.lang.core.types.ty.Ty

// https://github.com/rust-lang/rust/blob/f7b831ac8a897273f78b9f47165cf8e54066ce4b/compiler/rustc_middle/src/mir/visit.rs#L1206
interface MirVisitor {
    fun returnPlace(): MirLocal

    fun visitBody(body: MirBody) {
        // TODO: process body.generator

        for (block in body.basicBlocks) {
            visitBasicBlock(block)
        }

        for (scope in body.sourceScopes) {
            visitSourceScope(scope)
        }

        // TODO: process body.returnTy

        for (local in body.localDecls) {
            visitLocalDecl(local)
        }

        // TODO: process body.userTypeAnnotations

        for (varDebugInfo in body.varDebugInfo) {
            visitVarDebugInfo(body, varDebugInfo)
        }

        visitSpan(body.span)

        // TODO: process body.requiredConsts
    }

    fun visitBasicBlock(block: MirBasicBlock) {
        for ((index, statement) in block.statements.withIndex()) {
            val location = MirLocation(block, index)
            visitStatement(statement, location)
        }

        visitTerminator(block.terminator, block.terminatorLocation)
    }

    fun visitSourceScope(scope: MirSourceScope) {
        visitSpan(scope.span)
        scope.parentScope?.let { visitSourceScope(it) }
        // TODO: process scope.inlined
        // TODO: process scope.inlinedParentScope
    }

    fun visitLocalDecl(local: MirLocal) {
        visitTy(local.ty, TyContext.LocalDecl(local, local.source))
        // TODO: process local.userTy
        visitSourceInfo(local.source)
    }

    fun visitVarDebugInfo(body: MirBody, varDebugInfo: MirVarDebugInfo) {
        visitSourceInfo(varDebugInfo.source)

        val firstBlock = body.basicBlocks.find { it.index == 0 } ?: return
        val location = MirLocation(firstBlock, 0)
        when (val value = varDebugInfo.contents) {
            is MirVarDebugInfo.Contents.Constant -> {
                visitConstant(value.constant, location)
            }

            is MirVarDebugInfo.Contents.Place -> {
                visitPlace(value.place, MirPlaceContext.NonUse.VarDebugInfo, location)
            }

            is MirVarDebugInfo.Contents.Composite -> {
                visitTy(value.ty, TyContext.Location(location))
                for (fragment in value.fragments) {
                    visitPlace(fragment.contents, MirPlaceContext.NonUse.VarDebugInfo, location)
                }
            }
        }
    }

    fun visitPlace(place: MirPlace, context: MirPlaceContext, location: MirLocation) {
        var currentContext = context
        if (place.projections.isNotEmpty()) {
            if (currentContext.isUse) {
                currentContext = if (currentContext.isMutatingUse) {
                    MirPlaceContext.MutatingUse.Projection
                } else {
                    MirPlaceContext.NonMutatingUse.Projection
                }
            }
        }
        visitLocal(place.local, currentContext, location)
        visitProjection(place, currentContext, location)
    }

    fun visitProjection(place: MirPlace, context: MirPlaceContext, location: MirLocation) {
        for (elem in place.projections.reversed()) {
            visitProjectionElem(elem, location)
        }
    }

    fun visitProjectionElem(elem: PlaceElem, location: MirLocation) {
        when (elem) {
            is MirProjectionElem.Field -> {
                visitTy(elem.elem, TyContext.Location(location))
            }

            is MirProjectionElem.Index -> {
                visitLocal(elem.index, MirPlaceContext.NonMutatingUse.Copy, location)
            }

            is MirProjectionElem.Deref,
            is MirProjectionElem.ConstantIndex,
            is MirProjectionElem.Downcast -> {}
        }
    }

    fun visitLocal(local: MirLocal, context: MirPlaceContext, location: MirLocation) {
    }

    fun visitConstant(constant: MirConstant, location: MirLocation) {
        visitSpan(constant.span)
        // TODO: process constant.literal
    }

    fun visitSpan(span: MirSpan) {
    }

    fun visitStatement(statement: MirStatement, location: MirLocation) {
        visitSourceInfo(statement.source)
        when (statement) {
            is MirStatement.Assign -> {
                visitAssign(statement.place, statement.rvalue, location)
            }

            is MirStatement.FakeRead -> {
                visitPlace(statement.place, MirPlaceContext.NonMutatingUse.Inspect, location)
            }

            is MirStatement.StorageDead -> {
                visitLocal(statement.local, MirPlaceContext.NonUse.StorageLive, location)
            }

            is MirStatement.StorageLive -> {
                visitLocal(statement.local, MirPlaceContext.NonUse.StorageDead, location)
            }
        }
    }

    fun visitAssign(place: MirPlace, rvalue: MirRvalue, location: MirLocation) {
        visitPlace(place, MirPlaceContext.MutatingUse.Store, location)
        visitRvalue(rvalue, location)
    }

    fun visitRvalue(rvalue: MirRvalue, location: MirLocation) {
        when (rvalue) {
            is MirRvalue.Use -> {
                visitOperand(rvalue.operand, location)
            }

            is MirRvalue.Repeat -> {
                visitOperand(rvalue.operand, location)
                visitTyConst(rvalue.count, location)
            }

            is MirRvalue.ThreadLocalRef -> Unit

            is MirRvalue.Ref -> {
                // TODO: visitRegion(rvalue.region, location)
                val context = when (rvalue.borrowKind) {
                    MirBorrowKind.Shared -> MirPlaceContext.NonMutatingUse.SharedBorrow
                    MirBorrowKind.Shallow -> MirPlaceContext.NonMutatingUse.ShallowBorrow
                    MirBorrowKind.Unique -> MirPlaceContext.NonMutatingUse.UniqueBorrow
                    is MirBorrowKind.Mut -> MirPlaceContext.MutatingUse.Borrow
                }
                visitPlace(rvalue.place, context, location)
            }

            is MirRvalue.CopyForDeref -> TODO()

            is MirRvalue.AddressOf -> TODO()

            is MirRvalue.BinaryOpUse -> {
                visitOperand(rvalue.left, location)
                visitOperand(rvalue.right, location)
            }

            is MirRvalue.CheckedBinaryOpUse -> {
                visitOperand(rvalue.left, location)
                visitOperand(rvalue.right, location)
            }

            is MirRvalue.UnaryOpUse -> {
                visitOperand(rvalue.operand, location)
            }

            is MirRvalue.Discriminant -> {
                visitPlace(rvalue.place, MirPlaceContext.NonMutatingUse.Inspect, location)
            }

            is MirRvalue.NullaryOpUse -> TODO()

            is MirRvalue.Aggregate -> {
                when (rvalue) {
                    is MirRvalue.Aggregate.Adt -> {
                        // TODO: visitSubsts(rvalue.substs, location)
                    }

                    is MirRvalue.Aggregate.Array -> {
                        visitTy(rvalue.ty, TyContext.Location(location))
                    }

                    is MirRvalue.Aggregate.Tuple -> {}
                }

                for (operand in rvalue.operands) {
                    visitOperand(operand, location)
                }
            }

            is MirRvalue.Len -> {
                visitPlace(
                    rvalue.place,
                    MirPlaceContext.NonMutatingUse.Inspect,
                    location,
                )
            }
            else -> TODO()
        }
    }

    fun visitTerminator(terminator: MirTerminator<MirBasicBlock>, location: MirLocation) {
        visitSourceInfo(terminator.source)
        when (terminator) {
            is MirTerminator.Assert -> {
                visitOperand(terminator.cond, location)
                visitAssertMessage(terminator.msg, location)
            }

            is MirTerminator.Return -> {
                visitLocal(returnPlace(), MirPlaceContext.NonMutatingUse.Move, location)
            }

            is MirTerminator.SwitchInt -> {
                visitOperand(terminator.discriminant, location)
            }

            is MirTerminator.Drop -> {
                visitPlace(terminator.place, MirPlaceContext.MutatingUse.Drop, location)
            }

            is MirTerminator.Goto,
            is MirTerminator.Resume,
            is MirTerminator.Unreachable,
            is MirTerminator.FalseEdge,
            is MirTerminator.FalseUnwind -> {
            }

            is MirTerminator.Call -> {
                visitOperand(terminator.callee, location)
                for (arg in terminator.args) {
                    visitOperand(arg, location)
                }
                visitPlace(
                    terminator.destination,
                    MirPlaceContext.MutatingUse.Call,
                    location,
                )
            }
        }
    }

    fun visitOperand(operand: MirOperand, location: MirLocation) {
        when (operand) {
            is MirOperand.Copy -> {
                visitPlace(operand.place, MirPlaceContext.NonMutatingUse.Copy, location)
            }

            is MirOperand.Move -> {
                visitPlace(operand.place, MirPlaceContext.NonMutatingUse.Move, location)
            }

            is MirOperand.Constant -> {
                visitConstant(operand.constant, location)
            }
        }
    }

    fun visitAssertMessage(msg: MirAssertKind, location: MirLocation) {
        when (msg) {
            is MirAssertKind.Overflow -> {
                visitOperand(msg.left, location)
                visitOperand(msg.right, location)
            }

            is MirAssertKind.OverflowNeg -> {
                visitOperand(msg.arg, location)
            }

            is MirAssertKind.DivisionByZero -> {
                visitOperand(msg.arg, location)
            }

            is MirAssertKind.ReminderByZero -> {
                visitOperand(msg.arg, location)
            }

            is MirAssertKind.BoundsCheck -> {
                visitOperand(msg.len, location)
                visitOperand(msg.index, location)
            }
        }
    }

    fun visitSourceInfo(source: MirSourceInfo) {
        visitSpan(source.span)
        visitSourceScope(source.scope)
    }

    fun visitTy(ty: Ty, context: TyContext) {
    }

    fun visitTyConst(const: Const, location: MirLocation) {
    }
}

sealed class MirPlaceContext {
    sealed class NonMutatingUse : MirPlaceContext() {
        object Projection : NonMutatingUse()
        object Inspect : NonMutatingUse()
        object Copy : NonMutatingUse()
        object Move : NonMutatingUse()
        object SharedBorrow : NonMutatingUse()
        object ShallowBorrow : NonMutatingUse()
        object UniqueBorrow : NonMutatingUse()
    }

    sealed class MutatingUse : MirPlaceContext() {
        object Projection : MutatingUse()
        object Store : MutatingUse()
        object Borrow : MutatingUse()
        object Call : MutatingUse()
        object Drop : MutatingUse()
    }

    sealed class NonUse : MirPlaceContext() {
        object VarDebugInfo : NonUse()
        object StorageLive : NonUse()
        object StorageDead : NonUse()
    }

    val isUse: Boolean get() = this !is NonUse
    val isMutatingUse: Boolean get() = this is MutatingUse
}

/** Extra information passed to `visitTy` and friends to give context about where the type etc appears. */
sealed class TyContext {
    data class LocalDecl(
        /** The index of the local variable we are visiting. */
        val local: MirLocal,
        /** The source location where this local variable was declared. */
        val sourceInfo: MirSourceInfo,
    ) : TyContext()

    /** A type found at some location. */
    data class Location(val location: MirLocation) : TyContext()
}
