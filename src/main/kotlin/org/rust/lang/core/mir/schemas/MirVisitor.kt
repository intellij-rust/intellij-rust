/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.schemas

import org.rust.lang.core.types.consts.Const
import org.rust.lang.core.types.ty.Ty

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
        var index = 0
        for (statement in block.statements) {
            val location = MirLocation(block, index)
            visitStatement(statement, location)
            index += 1
        }

        val terminator = block.terminator
        if (terminator != null) {
            val location = MirLocation(block, index)
            visitTerminator(terminator, location)
        }
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
                visitPlace(value.place, MirPlaceContext.NonUse(NonUseContext.VarDebugInfo), location)
            }

            is MirVarDebugInfo.Contents.Composite -> {
                visitTy(value.ty, TyContext.Location(location))
                for (fragment in value.fragments) {
                    visitPlace(fragment.contents, MirPlaceContext.NonUse(NonUseContext.VarDebugInfo), location)
                }
            }
        }
    }

    fun visitPlace(place: MirPlace, context: MirPlaceContext, location: MirLocation) {
        var currentContext = context
        if (place.projections.isNotEmpty()) {
            if (currentContext.isUse) {
                currentContext = if (currentContext.isMutatingUse) {
                    MirPlaceContext.MutatingUse(MutatingUseContext.Projection)
                } else {
                    MirPlaceContext.NonMutatingUse(NonMutatingUseContext.Projection)
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

            is MirProjectionElem.Deref -> {}
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
                visitPlace(statement.place, MirPlaceContext.NonMutatingUse(NonMutatingUseContext.Inspect), location)
            }

            is MirStatement.StorageDead -> {
                visitLocal(statement.local, MirPlaceContext.NonUse(NonUseContext.StorageLive), location)
            }

            is MirStatement.StorageLive -> {
                visitLocal(statement.local, MirPlaceContext.NonUse(NonUseContext.StorageDead), location)
            }
        }
    }

    fun visitAssign(place: MirPlace, rvalue: MirRvalue, location: MirLocation) {
        visitPlace(place, MirPlaceContext.MutatingUse(MutatingUseContext.Store), location)
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

            is MirRvalue.Ref -> {
                // TODO: visitRegion(rvalue.region, location)
                val context = when (rvalue.borrowKind) {
                    MirBorrowKind.Shared -> MirPlaceContext.NonMutatingUse(NonMutatingUseContext.SharedBorrow)
                    MirBorrowKind.Shallow -> MirPlaceContext.NonMutatingUse(NonMutatingUseContext.ShallowBorrow)
                    MirBorrowKind.Unique -> MirPlaceContext.NonMutatingUse(NonMutatingUseContext.UniqueBorrow)
                    is MirBorrowKind.Mut -> MirPlaceContext.MutatingUse(MutatingUseContext.Borrow)
                }
                visitPlace(rvalue.place, context, location)
            }

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
                visitLocal(returnPlace(), MirPlaceContext.NonMutatingUse(NonMutatingUseContext.Move), location)
            }

            is MirTerminator.SwitchInt -> {
                visitOperand(terminator.discriminant, location)
            }

            is MirTerminator.Goto,
            is MirTerminator.Resume,
            is MirTerminator.Unreachable,
            is MirTerminator.FalseUnwind -> {
            }

            is MirTerminator.Call -> TODO()
        }
    }

    fun visitOperand(operand: MirOperand, location: MirLocation) {
        when (operand) {
            is MirOperand.Copy -> {
                visitPlace(operand.place, MirPlaceContext.NonMutatingUse(NonMutatingUseContext.Copy), location)
            }

            is MirOperand.Move -> {
                visitPlace(operand.place, MirPlaceContext.NonMutatingUse(NonMutatingUseContext.Move), location)
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
    data class NonMutatingUse(val context: NonMutatingUseContext) : MirPlaceContext()
    data class MutatingUse(val context: MutatingUseContext) : MirPlaceContext()
    data class NonUse(val context: NonUseContext) : MirPlaceContext()

    val isUse: Boolean get() = this !is NonUse
    val isMutatingUse: Boolean get() = this is MutatingUse
}

enum class NonMutatingUseContext {
    Projection,
    Inspect,
    Copy,
    Move,
    SharedBorrow,
    ShallowBorrow,
    UniqueBorrow
}

enum class MutatingUseContext {
    Projection,
    Store,
    Borrow
}

enum class NonUseContext {
    VarDebugInfo,
    StorageLive,
    StorageDead
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
