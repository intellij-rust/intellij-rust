/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir

import com.intellij.psi.PsiWhiteSpace
import org.rust.lang.core.mir.building.*
import org.rust.lang.core.mir.schemas.*
import org.rust.lang.core.mir.schemas.impls.MirBasicBlockImpl
import org.rust.lang.core.mir.schemas.impls.MirBodyImpl
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.normType
import org.rust.lang.core.types.ty.*

class MirBuilder private constructor(
    private val checkOverflow: Boolean,
    private val source: MirSourceInfo,
    returnTy: Ty,
    returnSource: MirSourceInfo,
) {
    private val basicBlocks = BasicBlocksBuilder()
    private val localDecls = LocalsBuilder(returnTy, returnSource)
    private val scopes = Scopes()

    fun build(constant: RsConstant): MirBody {
        val expr = constant.expr?.mirror() ?: error("Could not get expression from constant")
        basicBlocks
            .startBlock()
            .exprIntoPlace(expr, localDecls.returnPlace())
            .block
            .terminateWithReturn(constant.asSource)
        buildDropTrees()
        return MirBodyImpl(
            basicBlocks = basicBlocks.build(),
            localDecls = localDecls.build(),
            source = constant.asSource,
        )
    }

    private fun buildDropTrees() {
        // TODO: something about generator
        buildUnwindTree()
    }

    private fun buildUnwindTree() {
        val blocks = scopes.unwindDrops.buildMir(Unwind(basicBlocks), null)
        blocks[scopes.unwindDrops.root]?.resume(source)
    }

    private fun BlockAnd<*>.exprIntoPlace(expr: ThirExpr, place: MirPlace): BlockAnd<Unit> {
        return when (expr) {
            is ThirExpr.Literal, is ThirExpr.Unary, is ThirExpr.Binary, is ThirExpr.Tuple -> {
                val (block, rvalue) = this.toLocalRvalue(expr)
                block.pushAssign(place, rvalue, expr.source).andUnit()
            }
            is ThirExpr.Scope -> inScope(expr.source) { exprIntoPlace(expr.expr, place) }
            is ThirExpr.Block -> astBlockIntoPlace(expr.block, place, expr.source)
            is ThirExpr.Logical -> {
                val shortcircuitBlock = basicBlocks.new()
                val elseBlock = basicBlocks.new()
                val joinBlock = basicBlocks.new()
                this
                    .toLocalOperand(expr.left)
                    .run {
                        when (expr.op) {
                            LogicOp.AND -> block.terminateWithIf(elem, elseBlock, shortcircuitBlock, expr.source)
                            LogicOp.OR -> block.terminateWithIf(elem, shortcircuitBlock, elseBlock, expr.source)
                        }
                        val shortcircuitValue = when (expr.op) {
                            LogicOp.AND -> toConstant(false, TyBool.INSTANCE, expr.source)
                            LogicOp.OR -> toConstant(true, TyBool.INSTANCE, expr.source)
                        }
                        shortcircuitBlock.pushAssign(
                            place = place,
                            rvalue = MirRvalue.Use(MirOperand.Constant(shortcircuitValue)),
                            source = expr.source
                        )
                        shortcircuitBlock.terminateWithGoto(joinBlock, expr.source)
                        elseBlock
                            .andUnit()
                            .toLocalOperand(expr.right)
                            .let {
                                it.block
                                    .pushAssign(place, MirRvalue.Use(it.elem), expr.source)
                                    .terminateWithGoto(joinBlock, expr.source)
                            }
                        joinBlock.andUnit()
                    }
            }
            is ThirExpr.If -> {
                val conditionScope = scopes.last()

                val (thenBlock, elseBlock) = inScope(expr.then.source) {
                    inIfThenScope(conditionScope, expr.then.source) {
                        this
                            .thenElseBreak(
                                expr.cond,
                                conditionScope,
                                conditionScope,
                                expr.then.source,
                            )
                            .exprIntoPlace(expr.then, place)
                    }
                }.map {
                    expr.`else`?.let { `else` ->
                        it.andUnit().exprIntoPlace(`else`, place).block
                    } ?: run {
                        TODO()
                    }
                }

                val joinBlock = basicBlocks.new()
                thenBlock.terminateWithGoto(joinBlock, expr.source)
                elseBlock.terminateWithGoto(joinBlock, expr.source)

                joinBlock.andUnit()
            }
        }
    }

    private fun BlockAnd<*>.thenElseBreak(
        cond: ThirExpr, // for some reasons it's called expr in compiler
        tempScopeOverride: Scope?,
        breakScope: Scope,
        variableSource: MirSourceInfo,
    ): BlockAnd<Unit> {
        return when (cond) {
            // TODO: cases for logical op w/ `and` & for let
            is ThirExpr.Scope -> inScope(cond.source) {
                thenElseBreak(cond.expr, tempScopeOverride, breakScope, variableSource)
            }
            else -> {
                val tempScope = tempScopeOverride ?: scopes.last()
                this
                    .toTemp(cond, tempScope, Mutability.MUTABLE)
                    .let {
                        val operand = MirOperand.Move(MirPlace(it.elem))
                        val thenBlock = basicBlocks.new()
                        val elseBlock = basicBlocks.new()
                        it.block.terminateWithIf(operand, thenBlock, elseBlock, cond.source)
                        elseBlock.breakForElse(breakScope, cond.source)
                        thenBlock.andUnit()
                    }
            }
        }
    }

    private fun MirBasicBlockImpl.breakForElse(breakScope: Scope, source: MirSourceInfo) {
        val ifThenScope = scopes.ifThenScope
        checkNotNull(ifThenScope)
        val scopeIndex = scopes.scopeIndex(breakScope)
        var dropNode = ifThenScope.elseDrops.root
        scopes.scopes().drop(scopeIndex + 1).forEach { scope ->
            scope.drops().forEach { drop ->
                dropNode = ifThenScope.elseDrops.addDrop(drop, dropNode)
            }
        }
        ifThenScope.elseDrops.addEntry(this, dropNode)
        this.setTerminatorSource(source)
    }

    private fun BlockAnd<*>.astBlockIntoPlace(
        block: ThirBlock,
        place: MirPlace,
        source: MirSourceInfo,
    ): BlockAnd<Unit> {
        // TODO: something about destruction scopes
        return inScope(source) {
            // TODO: add case if targeted by break
            astBlockStmtsIntoPlace(block, place, source)
        }
    }

    private fun BlockAnd<*>.astBlockStmtsIntoPlace(
        block: ThirBlock,
        place: MirPlace,
        source: MirSourceInfo,
    ): BlockAnd<Unit> {
        // TODO: handle statements
        // TODO: something about block context
        return exprIntoPlace(block.expr, place)
    }

    private fun BlockAnd<*>.toLocalRvalue(expr: ThirExpr): BlockAnd<MirRvalue> {
        return toRvalue(expr, scopes.last())
    }

    private fun BlockAnd<*>.toLocalOperand(expr: ThirExpr): BlockAnd<MirOperand> {
        return toOperand(expr, scopes.last(), NeedsTemporary.Maybe)
    }

    private fun BlockAnd<*>.toRvalue(expr: ThirExpr, scope: Scope): BlockAnd<MirRvalue> {
        return when (expr) {
            is ThirExpr.Scope -> inScope(expr.source) { toRvalue(expr.expr, scope) }
            is ThirExpr.Literal -> {
                val constant = toConstant(expr, expr.ty, expr.source)
                block and MirRvalue.Use(MirOperand.Constant(constant))
            }
            is ThirExpr.Unary -> {
                toOperand(expr.arg, scope, NeedsTemporary.No)
                    .assertNoNegOverflow(expr, expr.ty, expr.source)
                    .map { MirRvalue.UnaryOpUse(expr.op, it) }
            }
            is ThirExpr.Binary -> {
                this
                    .toOperand(expr.left, scope, NeedsTemporary.Maybe)
                    .let { blockAnd ->
                        val blockAndRight = blockAnd.toOperand(expr.right, scope, NeedsTemporary.No)
                        blockAndRight.map { blockAnd.elem to it }
                    }
                    .buildBinaryOp(expr.op, expr.ty, expr.source)
            }
            is ThirExpr.Block, is ThirExpr.If, is ThirExpr.Logical -> {
                this
                    .toOperand(expr, scope, NeedsTemporary.No)
                    .map { MirRvalue.Use(it) }
            }
            is ThirExpr.Tuple -> {
                var blockAnd: BlockAnd<*> = this
                val fields = expr
                    .fields
                    .map {
                        blockAnd = blockAnd.toOperand(it, scope, NeedsTemporary.Maybe) // TODO: needs temp maybe
                        blockAnd.elem as MirOperand
                    }
                blockAnd.block and MirRvalue.Aggregate.Tuple(fields)
            }
        }
    }

    private fun BlockAnd<Pair<MirOperand, MirOperand>>.buildBinaryOp(
        op: ArithmeticOp,
        ty: Ty,
        source: MirSourceInfo,
    ): BlockAnd<MirRvalue> {
        val left = elem.first
        val right = elem.second
        return if (checkOverflow && op.isCheckable && ty.isIntegral) {
            val resultTy = TyTuple(listOf(ty, TyBool.INSTANCE))
            val resultPlace = localDecls.tempPlace(resultTy, source, Mutability.MUTABLE)
            val value = resultPlace.makeField(0, ty)
            val overflow = resultPlace.makeField(1, TyBool.INSTANCE)
            block
                .pushAssign(resultPlace, MirRvalue.CheckedBinaryOpUse(op, left.toCopy(), right.toCopy()), source)
                .assert(
                    MirOperand.Move(overflow),
                    false,
                    source,
                    MirAssertKind.Overflow(op, left, right),
                )
                .and(MirRvalue.Use(MirOperand.Move(value)))
        } else {
            if (!(ty.isIntegral && (op == ArithmeticOp.DIV || op == ArithmeticOp.REM))) {
                return block and MirRvalue.BinaryOpUse(op, left, right)
            }

            val zeroAssert = if (op == ArithmeticOp.DIV) {
                MirAssertKind.DivisionByZero(left.toCopy())
            } else {
                MirAssertKind.ReminderByZero(left.toCopy())
            }
            val overflowAssert = MirAssertKind.Overflow(op, left.toCopy(), right.toCopy())

            val isZero = localDecls.tempPlace(TyBool.INSTANCE, source, Mutability.MUTABLE)
            val zero = MirOperand.Constant(toConstant(0, ty, source))
            block.pushAssign(
                place = isZero,
                rvalue = MirRvalue.BinaryOpUse(EqualityOp.EQ, right.toCopy(), zero),
                source = source,
            )
            block
                .assert(MirOperand.Move(isZero), false, source, zeroAssert)
                .and(elem)
                .assertDivOverflow(ty, overflowAssert, source)
                .map { MirRvalue.BinaryOpUse(op, left, right) }
        }
    }

    private fun BlockAnd<Pair<MirOperand, MirOperand>>.assertDivOverflow(
        ty: Ty,
        assert: MirAssertKind,
        source: MirSourceInfo,
    ): BlockAnd<*> {
        if (!ty.isSigned) return this
        val left = elem.first
        val right = elem.second
        check(ty is TyInteger) // TODO: add floats
        val negOne = MirOperand.Constant(toConstant(-1, ty, source))
        val min = MirOperand.Constant(toConstant(ty.minValue, ty, source))

        val isNegOne = localDecls.tempPlace(TyBool.INSTANCE, source, Mutability.MUTABLE)
        val isMin = localDecls.tempPlace(TyBool.INSTANCE, source, Mutability.MUTABLE)
        val overflow = localDecls.tempPlace(TyBool.INSTANCE, source, Mutability.MUTABLE)

        return block
            .pushAssign(
                place = isNegOne,
                rvalue = MirRvalue.BinaryOpUse(EqualityOp.EQ, right.toCopy(), negOne),
                source = source,
            )
            .pushAssign(
                place = isMin,
                rvalue = MirRvalue.BinaryOpUse(EqualityOp.EQ, left.toCopy(), min),
                source = source,
            )
            .pushAssign(
                place = overflow,
                rvalue = MirRvalue.BinaryOpUse(ArithmeticOp.BIT_AND, MirOperand.Move(isNegOne), MirOperand.Move(isMin)),
                source = source,
            )
            .assert(
                cond = MirOperand.Move(overflow),
                expected = false,
                source = source,
                msg = assert,
            )
            .andUnit()
    }

    private fun BlockAnd<MirOperand>.assertNoNegOverflow(
        kind: ThirExpr.Unary,
        type: Ty,
        source: MirSourceInfo,
    ): BlockAnd<MirOperand> {
        val needsAssertion = checkOverflow && kind.op == UnaryOperator.MINUS && type.isSigned
        if (!needsAssertion) return this
        check(type is TyInteger) // TODO: guess it can also be boolean or reference
        val isMin = localDecls.tempPlace(TyBool.INSTANCE, source, Mutability.MUTABLE)
        val eq = MirRvalue.BinaryOpUse(
            op = EqualityOp.EQ,
            left = elem.toCopy(),
            right = MirOperand.Constant(toConstant(type.minValue, type, source))
        )
        return block
            .pushAssign(isMin, eq, source)
            .assert(
                cond = MirOperand.Move(isMin),
                expected = false,
                source = source,
                msg = MirAssertKind.OverflowNeg(elem.toCopy()),
            )
            .and(elem)
    }

    private inline fun <R> inScope(
        source: MirSourceInfo,
        crossinline body: () -> BlockAnd<R>,
    ): BlockAnd<R> {
        scopes.push(Scope(source))
        return body().popScope()
    }

    private inline fun inIfThenScope(
        scope: Scope,
        source: MirSourceInfo,
        crossinline body: () -> BlockAnd<*>,
    ): BlockAnd<MirBasicBlockImpl> {
        val prevScope = scopes.ifThenScope.also { scopes.ifThenScope = IfThenScope(scope, DropTree()) }
        val thenBlock = body().block
        val ifThenScope = scopes.ifThenScope.also { scopes.ifThenScope = prevScope }
        checkNotNull(ifThenScope)
        val elseBlock = buildExitTree(ifThenScope.elseDrops, ifThenScope.scope, source, null)?.block ?: basicBlocks.new()
        return thenBlock and elseBlock
    }

    private fun buildExitTree(
        drops: DropTree,
        elseScope: Scope,
        source: MirSourceInfo,
        continueBlock: MirBasicBlockImpl?,
    ): BlockAnd<Unit>? {
        val blocks = drops.buildMir(ExitScopes(basicBlocks), continueBlock)
        // TODO: there is more in case we have DropKind::Value
        val root = drops.root
        return blocks[root]?.andUnit()
    }

    private fun <T> BlockAnd<T>.popScope(): BlockAnd<T> {
        return buildDrops(scopes.last()).also { scopes.pop() }
    }

    private fun <T> BlockAnd<T>.buildDrops(scope: Scope): BlockAnd<T> {
        scope.reversedDrops().forEach { drop ->
            when (drop.kind) {
                Drop.Kind.VALUE -> TODO()
                Drop.Kind.STORAGE -> {
                    block.pushStorageDead(drop.local, drop.source)
                }
            }
        }
        return this
    }

    private fun MirBasicBlockImpl.assert(
        cond: MirOperand,
        expected: Boolean,
        source: MirSourceInfo,
        msg: MirAssertKind,
    ): MirBasicBlockImpl {
        val successBlock = basicBlocks.new()
        this.terminateWithAssert(cond, expected, successBlock, source, msg)
        divergeFrom(this)
        return successBlock
    }

    private fun divergeFrom(block: MirBasicBlockImpl) {
        val nextDrop = divergeCleanup()
        scopes.unwindDrops.addEntry(block, nextDrop)
    }

    private fun divergeCleanup(): DropTree.DropNode {
        return divergeCleanup(scopes.last())
    }

    private fun divergeCleanup(target: Scope): DropTree.DropNode {
        val targetIndex = scopes.scopeIndex(target)
        val (drop, scopeIndex) = scopes
            .scopes()
            .take(targetIndex + 1)
            .withIndex()
            .toList()
            .asReversed()
            .firstNotNullOfOrNull { (scopeIndex, scope) -> scope.cachedUnwindDrop?.to(scopeIndex) }
            ?: (scopes.unwindDrops.root to 0)

        // If current scoped is cached than just return it as far as I understand
        if (scopeIndex > targetIndex) return drop

        scopes.scopes().take(targetIndex + 1).drop(scopeIndex).forEach { scope ->
            // TODO: cycle if generator or value drops (I don't have anything of it now)
            scope.setCachedUnwindDrop(drop)
        }

        return drop
    }

    private fun BlockAnd<*>.toOperand(
        expr: ThirExpr,
        scope: Scope,
        needsTemporary: NeedsTemporary,
    ): BlockAnd<MirOperand> {
        if (expr is ThirExpr.Scope) {
            return inScope(expr.source) { toOperand(expr.expr, scope, needsTemporary) }
        }

        return when (MirCategory.of(expr)) {
            MirCategory.Constant -> {
                // TODO: cast to literal is temporary
                if (needsTemporary == NeedsTemporary.No || !expr.ty.needsDrop) {
                    val constant = toConstant(
                        expr as? ThirExpr.Literal ?: error("Unsupported type of constant category"),
                        expr.ty,
                        expr.source,
                    )
                    block and MirOperand.Constant(constant)
                } else {
                    toTemp(expr, scope, Mutability.MUTABLE)
                        .map { MirOperand.Move(MirPlace(it)) }
                }
            }
            MirCategory.Place, is MirCategory.Rvalue -> {
                toTemp(expr, scope, Mutability.MUTABLE)
                    .map { MirOperand.Move(MirPlace(it)) }
            }
            null -> TODO()
        }
    }

    private fun BlockAnd<*>.toTemp(expr: ThirExpr, scope: Scope, mutability: Mutability): BlockAnd<MirLocal> {
        return when (expr) {
            is ThirExpr.Scope -> inScope(expr.source) { toTemp(expr.expr, scope, mutability) }
            is ThirExpr.Literal,
            is ThirExpr.Unary,
            is ThirExpr.Binary,
            is ThirExpr.If,
            is ThirExpr.Logical,
            is ThirExpr.Tuple -> {
                val localPlace = localDecls.tempPlace(expr.ty, expr.source, mutability)
                block.pushStorageLive(localPlace.local, expr.source)
                scope.scheduleDrop(localPlace.local, Drop.Kind.STORAGE)
                this
                    .exprIntoPlace(expr, localPlace)
                    .also { scope.scheduleDrop(localPlace.local, Drop.Kind.VALUE) }
                    .map { localPlace.local }
            }
            is ThirExpr.Block -> TODO()
        }
    }

    private fun toConstant(
        constant: ThirExpr.Literal,
        ty: Ty,
        source: MirSourceInfo,
    ): MirConstant {
        return when {
            constant.literal.integerValue != null && ty.isIntegral -> {
                val value = constant.literal.integerValue!!.let { if (constant.neg) -it else it }
                toConstant(value, ty, source)
            }
            constant.literal.booleanValue != null && ty is TyBool -> {
                toConstant(constant.literal.booleanValue!!, ty, source)
            }
            else -> TODO()
        }
    }

    private fun toConstant(bool: Boolean, ty: Ty, source: MirSourceInfo): MirConstant {
        val value = if (bool) 1L else 0L
        return toConstant(value, ty, source)
    }

    private fun toConstant(value: Long, ty: Ty, source: MirSourceInfo): MirConstant {
        val int = MirScalarInt(value, 0) // TODO: 0 (do I even need this)
        return MirConstant.Value(
            constValue = MirConstValue.Scalar(MirScalarValue.Int(int)),
            ty = ty,
            source = source,
        )
    }

    companion object {
        fun build(file: RsFile): List<MirBody> {
            return buildList {
                file.children.forEach { child ->
                    when (child) {
                        is RsFile -> {}
                        is RsConstant -> {
                            add(build(child))
                        }
                        is PsiWhiteSpace -> {}
                        else -> TODO("Type ${child::class} is not supported")
                    }
                }
            }
        }

        fun build(constant: RsConstant): MirBody {
            val builder = MirBuilder(
                checkOverflow = true,
                source = constant.asSource,
                returnTy = constant.typeReference?.normType ?: error("Could not get return type"),
                returnSource = constant.typeReference?.asSource ?: error("Could not find return type source")
            )
            return builder.build(constant)
        }
    }
}
