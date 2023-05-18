/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir

import com.intellij.psi.PsiWhiteSpace
import org.rust.lang.core.mir.building.*
import org.rust.lang.core.mir.schemas.*
import org.rust.lang.core.mir.schemas.MirBinaryOperator.Companion.toMir
import org.rust.lang.core.mir.schemas.impls.MirBasicBlockImpl
import org.rust.lang.core.mir.schemas.impls.MirBodyImpl
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.ImplLookup
import org.rust.lang.core.thir.*
import org.rust.lang.core.types.consts.asLong
import org.rust.lang.core.types.normType
import org.rust.lang.core.types.regions.Scope
import org.rust.lang.core.types.regions.ScopeTree
import org.rust.lang.core.types.regions.getRegionScopeTree
import org.rust.lang.core.types.ty.*
import org.rust.openapiext.testAssert

class MirBuilder private constructor(
    private val element: RsElement,
    private val implLookup: ImplLookup,
    private val regionScopeTree: ScopeTree,
    private val checkOverflow: Boolean,
    private val span: MirSpan,
    private val argCount: Int,
    returnTy: Ty,
    returnSpan: MirSpan,
) {
    private val basicBlocks = BasicBlocksBuilder()
    private val scopes = Scopes()
    private val sourceScopes = SourceScopesBuilder(span)
    private val localDecls = LocalsBuilder(returnTy, MirSourceInfo.outermost(returnSpan))
    private val varDebugInfo = mutableListOf<MirVarDebugInfo>()
    private val varIndices = mutableMapOf<LocalVar, MirLocalForNode>()

    private val unitTemp by lazy { localDecls.tempPlace(TyUnit.INSTANCE, span) }

    fun build(function: RsFunction): MirBody {
        val body = function.block ?: error("Could not get block of function")
        val expr = body.mirrorAsExpr(function, function.normReturnType)
        inScope(Scope.CallSite(body)) {
            val fnEndSpan = span.end
            val returnBlockAnd = inBreakableScope(null, localDecls.returnPlace(), fnEndSpan) {
                inScope(Scope.Arguments(body)) {
                    basicBlocks
                        .startBlock()
                        .argsAndBody(expr)
                }
            }
            returnBlockAnd.block.terminateWithReturn(sourceInfo(fnEndSpan))
            buildDropTrees()
            returnBlockAnd
        }
        return finish()
    }

    fun build(constant: RsConstant): MirBody {
        val expr = constant.expr?.mirror(constant) ?: error("Could not get expression from constant")
        basicBlocks
            .startBlock()
            .exprIntoPlace(expr, localDecls.returnPlace())
            .block
            .terminateWithReturn(sourceInfo(constant.asSpan))
        buildDropTrees()
        return finish()
    }

    private fun finish(): MirBody {
        return MirBodyImpl(
            sourceElement = element,
            basicBlocks = basicBlocks.build(),
            localDecls = localDecls.build(),
            span = span,
            sourceScopes = sourceScopes.build(),
            argCount = argCount,
            varDebugInfo = varDebugInfo,
        )
    }

    // TODO:
    //  1. function arguments
    //  2. captured values
    private fun BlockAnd<*>.argsAndBody(expr: ThirExpr): BlockAnd<Unit> {
        return exprIntoPlace(expr, localDecls.returnPlace())
    }

    private fun buildDropTrees() {
        // TODO: something about generator
        buildUnwindTree()
    }

    private fun buildUnwindTree() {
        val blocks = scopes.unwindDrops.buildMir(Unwind(basicBlocks), null)
        blocks[scopes.unwindDrops.root]?.terminateWithResume(MirSourceInfo.outermost(span))
    }

    private fun BlockAnd<*>.exprIntoPlace(expr: ThirExpr, place: MirPlace): BlockAnd<Unit> {
        val source = sourceInfo(expr.span)
        return when (expr) {
            is ThirExpr.Unary,
            is ThirExpr.Binary,
            is ThirExpr.Box,
            is ThirExpr.Cast,
            is ThirExpr.Pointer,
            is ThirExpr.Repeat,
            is ThirExpr.Array,
            is ThirExpr.Tuple,
            is ThirExpr.Closure,
            is ThirExpr.ConstBlock,
            is ThirExpr.Literal,
            is ThirExpr.NamedConst,
            is ThirExpr.NonHirLiteral,
            is ThirExpr.ZstLiteral,
            is ThirExpr.ConstParam,
            is ThirExpr.ThreadLocalRef,
            is ThirExpr.StaticRef,
            is ThirExpr.OffsetOf -> {
                val (block, rvalue) = this.toLocalRvalue(expr)
                block.pushAssign(place, rvalue, source).andUnit()
            }
            is ThirExpr.Scope -> inScope(expr.regionScope) { exprIntoPlace(expr.expr, place) }
            is ThirExpr.Block -> astBlockIntoPlace(expr.block, place, expr.span)
            is ThirExpr.Logical -> {
                val shortcircuitBlock = basicBlocks.new()
                val elseBlock = basicBlocks.new()
                val joinBlock = basicBlocks.new()
                this
                    .toLocalOperand(expr.left)
                    .run {
                        when (expr.op) {
                            LogicOp.AND -> block.terminateWithIf(elem, elseBlock, shortcircuitBlock, source)
                            LogicOp.OR -> block.terminateWithIf(elem, shortcircuitBlock, elseBlock, source)
                        }
                        val shortcircuitValue = when (expr.op) {
                            LogicOp.AND -> toConstant(false, TyBool.INSTANCE, expr.span)
                            LogicOp.OR -> toConstant(true, TyBool.INSTANCE, expr.span)
                        }
                        shortcircuitBlock.pushAssign(
                            place = place,
                            rvalue = MirRvalue.Use(MirOperand.Constant(shortcircuitValue)),
                            source = source
                        )
                        shortcircuitBlock.terminateWithGoto(joinBlock, source)
                        elseBlock
                            .andUnit()
                            .toLocalOperand(expr.right)
                            .let {
                                it.block
                                    .pushAssign(place, MirRvalue.Use(it.elem), source)
                                    .terminateWithGoto(joinBlock, source)
                            }
                        joinBlock.andUnit()
                    }
            }
            is ThirExpr.If -> {
                val conditionScope = scopes.topmost()

                val thenSource = sourceInfo(expr.then.span)
                val (thenBlock, elseBlock) = inScope(expr.ifThenScope) {
                    inIfThenScope(conditionScope, expr.then.span) {
                        this
                            .thenElseBreak(
                                expr.cond,
                                conditionScope,
                                conditionScope,
                                thenSource,
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
                thenBlock.terminateWithGoto(joinBlock, source)
                elseBlock.terminateWithGoto(joinBlock, source)

                joinBlock.andUnit()
            }
            is ThirExpr.Loop -> {
                val loopBlock = basicBlocks.new()
                block.terminateWithGoto(loopBlock, source)
                inBreakableScope(loopBlock, place, expr.span) {
                    val bodyBlock = basicBlocks.new()
                    loopBlock.terminateWithFalseUnwind(bodyBlock, null, source)
                    divergeFrom(loopBlock)
                    val bodyBlockEnd = bodyBlock.andUnit().exprIntoPlace(expr.body, unitTemp)
                    bodyBlockEnd.block.terminateWithGoto(loopBlock, source)
                    null
                }
            }
            // TODO: there is an optimization for calls that will change the result unoptimized mir
            is ThirExpr.NeverToAny -> {
                val block = this.toTemp(expr.spanExpr, scopes.topmost(), Mutability.MUTABLE).block
                block.terminateWithUnreachable(source)
                basicBlocks.new().andUnit()
            }
            is ThirExpr.Continue, is ThirExpr.Break, is ThirExpr.Return -> statementExpr(expr, null)
            is ThirExpr.VarRef,
            is ThirExpr.UpvarRef,
            is ThirExpr.PlaceTypeAscription,
            is ThirExpr.ValueTypeAscription,
            is ThirExpr.Index,
            is ThirExpr.Deref,
            is ThirExpr.Field -> {
                if (expr is ThirExpr.Field) {
                    // Create a "fake" temporary variable so that we check that the value is Sized.
                    // Usually, this is caught in type checking, but in the case of box expr there is no such check.
                    if (place.projections.isNotEmpty()) {
                        // TODO Do we really need it ?
                        // localDecls.newLocal(..., expr.ty, expr.span)
                    }
                }

                this
                    .toPlace(expr)
                    .map { consumeByCopyOrMove(it) }
                    .map { MirRvalue.Use(it) }
                    .also { it.block.pushAssign(place, it.elem, source) }
                    .block
                    .andUnit()
            }
            is ThirExpr.Assign, is ThirExpr.AssignOp -> {
                this
                    .statementExpr(expr, null)
                    .also { block.pushAssignUnit(place, source) }
            }
            is ThirExpr.Adt -> {
                this
                    .block
                    .pushAssign(place, MirRvalue.Aggregate.Adt(expr.definition, emptyList()), source)
                    .andUnit()
            }
            is ThirExpr.Borrow -> {
                val blockAnd = when (expr.kind) {
                    MirBorrowKind.Shared -> this.toReadOnlyPlace(expr.arg)
                    else -> this.toPlace(expr.arg)
                }
                val borrow = MirRvalue.Ref(expr.kind, blockAnd.elem)
                blockAnd.block.pushAssign(place, borrow, source)
                blockAnd.block.andUnit()
            }
            else -> TODO()
        }
    }

    private fun consumeByCopyOrMove(place: MirPlace): MirOperand {
        return if (place.local.ty.isMovesByDefault(implLookup)) {
            MirOperand.Move(place)
        } else {
            MirOperand.Copy(place)
        }
    }

    private fun BlockAnd<*>.toReadOnlyPlace(expr: ThirExpr): BlockAnd<MirPlace> {
        return toReadOnlyPlaceBuilder(expr).map { it.toPlace() }
    }

    private fun BlockAnd<*>.toPlace(expr: ThirExpr): BlockAnd<MirPlace> {
        return toPlaceBuilder(expr).map { it.toPlace() }
    }

    private fun BlockAnd<*>.toReadOnlyPlaceBuilder(expr: ThirExpr): BlockAnd<PlaceBuilder> {
        return exprToPlace(expr, Mutability.IMMUTABLE)
    }

    private fun BlockAnd<*>.toPlaceBuilder(expr: ThirExpr): BlockAnd<PlaceBuilder> {
        return exprToPlace(expr, Mutability.MUTABLE)
    }

    private fun BlockAnd<*>.exprToPlace(expr: ThirExpr, mutability: Mutability): BlockAnd<PlaceBuilder> {
        return when (expr) {
            is ThirExpr.Scope -> {
                inScope(expr.regionScope) {
                    exprToPlace(expr.expr, mutability)
                }
            }
            is ThirExpr.Field -> {
                exprToPlace(expr.expr, mutability)
                    .map { placeBuilder ->
                        val ty = expr.expr.ty
                        if (ty is TyAdt && ty.item is RsEnumItem) {
                            TODO()
                            // placeBuilder.downcast(ty.item, variantIndex)
                        } else {
                            placeBuilder
                        }
                    }
                    .map { placeBuilder ->
                        placeBuilder.field(expr.fieldIndex, expr.ty)
                    }

            }
            is ThirExpr.VarRef -> {
                // TODO: different handling in case of guards
                block and PlaceBuilder(varLocal(expr.local))
            }
            is ThirExpr.Array,
            is ThirExpr.Repeat,
            is ThirExpr.Tuple,
            is ThirExpr.Adt,
            is ThirExpr.Unary,
            is ThirExpr.Binary,
            is ThirExpr.NeverToAny,
            is ThirExpr.Borrow,
            is ThirExpr.If,
            is ThirExpr.Loop,
            is ThirExpr.Block,
            is ThirExpr.Assign,
            is ThirExpr.Break,
            is ThirExpr.Literal -> {
                toTemp(expr, expr.tempLifetime, mutability)
                    .map { PlaceBuilder(it) }
            }
            else -> TODO()
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
            is ThirExpr.Scope -> inScope(cond.regionScope) {
                thenElseBreak(cond.expr, tempScopeOverride, breakScope, variableSource)
            }
            else -> {
                val tempScope = tempScopeOverride ?: scopes.topmost()
                this
                    .toTemp(cond, tempScope, Mutability.MUTABLE)
                    .let {
                        val operand = MirOperand.Move(MirPlace(it.elem))
                        val thenBlock = basicBlocks.new()
                        val elseBlock = basicBlocks.new()
                        val source = sourceInfo(cond.span)
                        it.block.terminateWithIf(operand, thenBlock, elseBlock, source)
                        elseBlock.breakForElse(breakScope, source)
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
        span: MirSpan,
    ): BlockAnd<Unit> {
        // TODO: something about destruction scopes
        return inScope(block.scope) {
            // TODO: add case if targeted by break
            astBlockStmtsIntoPlace(block, place, span)
        }
    }

    private fun BlockAnd<*>.astBlockStmtsIntoPlace(
        block: ThirBlock,
        place: MirPlace,
        span: MirSpan,
    ): BlockAnd<Unit> {
        var blockAnd = this
        val outerSourceScope = sourceScopes.sourceScope
        val letScopeStack = mutableListOf<Scope>()
        val source = sourceInfo(span)
        block.statements.forEach { statement ->
            when {
                statement is ThirStatement.Let && statement.elseBlock == null -> {
                    scopes.push(MirScope(statement.remainderScope))
                    letScopeStack.add(statement.remainderScope)
                    val remainderSourceInfo = statement.remainderScope.span
                    val visibilityScope = sourceScopes.newSourceScope(remainderSourceInfo)
                    if (statement.initializer != null) {
                        blockAnd = inScope(statement.initScope) {
                            declareBindings(
                                visibilityScope,
                                remainderSourceInfo,
                                statement.pattern,
                                null to statement.initializer.span
                            )
                            blockAnd.exprIntoPattern(statement.pattern, statement.initializer)
                        }
                    } else {
                        blockAnd = inScope(statement.initScope) {
                            declareBindings(
                                visibilityScope,
                                remainderSourceInfo,
                                statement.pattern,
                                null,
                            )
                            blockAnd
                        }
                        visitPrimaryBindings(statement.pattern) { _, _, _, node, span, _ ->
                            blockAnd.block.storageLiveBinding(node, span, true)
                            scheduleDropForBinding(node, span)
                        }
                    }
                    sourceScopes.sourceScope = visibilityScope
                }
                statement is ThirStatement.Expr -> {
                    blockAnd = inScope(statement.scope) {
                        blockAnd.statementExpr(statement.expr, statement.scope)
                    }
                }
                else -> TODO()
            }
        }

        // TODO: something about block context
        blockAnd = if (block.expr != null) {
            blockAnd.exprIntoPlace(block.expr, place)
        } else {
            if (place.local.ty is TyUnit) {
                blockAnd.block.pushAssignUnit(place, source)
            }
            blockAnd
        }

        repeat(letScopeStack.size) {
            blockAnd = blockAnd.popScope()
        }
        sourceScopes.sourceScope = outerSourceScope

        return blockAnd.block.andUnit()
    }

    private fun BlockAnd<*>.exprIntoPattern(
        /* irrefutable */ pattern: ThirPat,
        initializer: ThirExpr
    ): BlockAnd<Unit> {
        return when {
            pattern is ThirPat.Binding && pattern.mode is ThirBindingMode.ByValue && pattern.subpattern == null -> {
                val place = block.storageLiveBinding(pattern.variable, pattern.source, true)
                val blockAnd = this.exprIntoPlace(initializer, place)
                val source = sourceInfo(pattern.source)
                blockAnd.block.pushFakeRead(MirStatement.FakeRead.Cause.ForLet(null), place, source)
                scheduleDropForBinding(pattern.variable, pattern.source)
                blockAnd.block.andUnit()
            }
            else -> TODO()
        }
    }

    private fun scheduleDropForBinding(
        variable: LocalVar,
        span: MirSpan,
    ) {
        val local = varLocal(variable)
        regionScopeTree.getVariableScope(variable.value)?.let { scope ->
            scheduleDrop(scope, local, Drop.Kind.VALUE)
        }
    }

    private fun MirBasicBlockImpl.storageLiveBinding(
        variable: LocalVar,
        source: MirSpan,
        scheduleDrop: Boolean,
    ): MirPlace {
        val local = varLocal(variable)
        pushStorageLive(local, sourceInfo(source))
        if (scheduleDrop) {
            regionScopeTree.getVariableScope(variable.value)?.let { scope ->
                scheduleDrop(scope, local, Drop.Kind.STORAGE)
            }
        }
        return MirPlace(local)
    }

    private fun varLocal(variable: LocalVar): MirLocal {
        return when (val localForNode = varIndices[variable]) {
            is MirLocalForNode.ForGuard -> TODO()
            is MirLocalForNode.One -> localForNode.local
            null -> error("Could not find variable")
        }
    }

    private fun declareBindings(
        visibilityScope: MirSourceScope?,
        scopeSource: MirSpan,
        pattern: ThirPat,
        matchPlace: Pair<MirPlace?, MirSpan>?,
    ): MirSourceScope? {
        var actualVisibilityScope = visibilityScope
        visitPrimaryBindings(pattern) { mutability, name, mode, variable, span, ty ->
            if (actualVisibilityScope == null) {
                actualVisibilityScope = sourceScopes.newSourceScope(scopeSource)
            }
            declareBinding(
                source = sourceInfo(span),
                visibilityScope = actualVisibilityScope!!,
                mutability = mutability,
                name = name,
                mode = mode,
                variable = variable,
                variableTy = ty,
                matchPlace = matchPlace,
                patternSource = pattern.source,
            )
        }
        // TODO: guard handling
        return actualVisibilityScope
    }

    private fun declareBinding(
        source: MirSourceInfo,
        visibilityScope: MirSourceScope,
        mutability: Mutability,
        name: String,
        mode: ThirBindingMode,
        variable: LocalVar,
        variableTy: Ty,
        matchPlace: Pair<MirPlace?, MirSpan>?,
        patternSource: MirSpan,
    ) {
        // this assert is needed because I use original mode from ThirBindingMode and
        // only hope that it will be the same
        testAssert {
            val refCorrect = when (mode) {
                is ThirBindingMode.ByValue -> mode.rs.ref == null
            }
            val mutCorrect = @Suppress("USELESS_IS_CHECK") when (mode) {
                is ThirBindingMode.ByValue -> true
            }
            refCorrect && mutCorrect
        }
        val debugSource = MirSourceInfo(source.span, visibilityScope)
        val localForArmBody = localDecls.newLocal(
            mutability = mutability,
            ty = variableTy,
            source = source,
            internal = false,
            blockTail = null,
            localInfo = MirLocalInfo.User(
                MirClearCrossCrate.Set(
                    MirBindingForm.Var(
                        MirVarBindingForm(
                            bindingMode = mode.rs,
                            tyInfo = null,
                            matchPlace = matchPlace,
                            patternSource = patternSource,
                        )
                    )
                )
            )
        )
        varDebugInfo.add(MirVarDebugInfo(name, debugSource, MirVarDebugInfo.Contents.Place(MirPlace(localForArmBody))))
        varIndices[variable] = MirLocalForNode.One(localForArmBody)
    }

    private fun visitPrimaryBindings(
        pattern: ThirPat,
        action: (Mutability, name: String, ThirBindingMode, LocalVar, MirSpan, Ty) -> Unit,
    ) {
        when (pattern) {
            is ThirPat.Binding -> {
                if (pattern.isPrimary) {
                    action(pattern.mutability, pattern.name, pattern.mode, pattern.variable, pattern.source, pattern.ty)
                }
                if (pattern.subpattern != null) {
                    visitPrimaryBindings(pattern.subpattern, action)
                }
            }
        }
    }

    private fun BlockAnd<*>.toLocalRvalue(expr: ThirExpr): BlockAnd<MirRvalue> {
        return toRvalue(expr, scopes.topmost())
    }

    private fun BlockAnd<*>.toLocalOperand(expr: ThirExpr): BlockAnd<MirOperand> {
        return toOperand(expr, scopes.topmost(), NeedsTemporary.Maybe)
    }

    private fun BlockAnd<*>.toRvalue(expr: ThirExpr, scope: Scope): BlockAnd<MirRvalue> {
        return when (expr) {
            is ThirExpr.Scope -> inScope(expr.regionScope) { toRvalue(expr.expr, scope) }
            is ThirExpr.Literal -> {
                val constant = toConstant(expr, expr.ty, expr.span)
                block and MirRvalue.Use(MirOperand.Constant(constant))
            }
            is ThirExpr.NamedConst,
            is ThirExpr.NonHirLiteral,
            is ThirExpr.ZstLiteral,
            is ThirExpr.ConstParam,
            is ThirExpr.ConstBlock,
            is ThirExpr.StaticRef -> TODO()
            is ThirExpr.Unary -> {
                toOperand(expr.arg, scope, NeedsTemporary.No)
                    .assertNoNegOverflow(expr, expr.ty, sourceInfo(expr.span))
                    .map { MirRvalue.UnaryOpUse(expr.op, it) }
            }
            is ThirExpr.Binary -> {
                this
                    .toOperand(expr.left, scope, NeedsTemporary.Maybe)
                    .let { blockAnd ->
                        val blockAndRight = blockAnd.toOperand(expr.right, scope, NeedsTemporary.No)
                        blockAndRight.map { blockAnd.elem to it }
                    }
                    .buildBinaryOp(expr.op, expr.ty, expr.span)
            }
            is ThirExpr.Array -> {
                var blockAnd: BlockAnd<*> = this
                val elementType = (expr.ty as? TyArray)?.base ?: TyUnknown
                val fields = expr
                    .fields
                    .map {
                        blockAnd = blockAnd.toOperand(it, scope, NeedsTemporary.Maybe)
                        blockAnd.elem as MirOperand
                    }
                blockAnd.block and MirRvalue.Aggregate.Array(elementType, fields)
            }
            is ThirExpr.Repeat -> {
                var blockAnd: BlockAnd<*> = this
                if (expr.count.asLong() == 0L) {
                    // TODO: For a non-const, we may need to generate an appropriate `Drop`
                    val elementType = expr.value.ty
                    blockAnd.block and MirRvalue.Aggregate.Array(elementType, emptyList())
                } else {
                    blockAnd = blockAnd.toOperand(expr.value, scope, NeedsTemporary.No)
                    blockAnd.block and MirRvalue.Repeat(blockAnd.elem as MirOperand, expr.count)
                }
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
            is ThirExpr.Assign, is ThirExpr.AssignOp -> {
                this
                    .statementExpr(expr, null)
                    .map {
                        MirRvalue.Use(
                            MirOperand.Constant(
                                MirConstant.zeroSized(TyUnit.INSTANCE, expr.span)
                            )
                        )
                    }
            }
            is ThirExpr.Yield,
            is ThirExpr.Block,
            is ThirExpr.Match,
            is ThirExpr.If,
            is ThirExpr.NeverToAny,
            is ThirExpr.Use,
            is ThirExpr.Borrow,
            is ThirExpr.AddressOf,
            is ThirExpr.Adt,
            is ThirExpr.Loop,
            is ThirExpr.Logical,
            is ThirExpr.Call,
            is ThirExpr.Field,
            is ThirExpr.Let,
            is ThirExpr.Deref,
            is ThirExpr.Index,
            is ThirExpr.VarRef,
            is ThirExpr.UpvarRef,
            is ThirExpr.Break,
            is ThirExpr.Continue,
            is ThirExpr.Return,
            is ThirExpr.InlineAsm,
            is ThirExpr.PlaceTypeAscription,
            is ThirExpr.ValueTypeAscription -> {
                this
                    .toOperand(expr, scope, NeedsTemporary.No)
                    .map { MirRvalue.Use(it) }
            }
            else -> TODO()
        }
    }

    private fun BlockAnd<*>.statementExpr(expr: ThirExpr, statementScope: Scope?): BlockAnd<Unit> {
        val source = sourceInfo(expr.span)
        return when (expr) {
            is ThirExpr.Scope -> inScope(expr.regionScope) { statementExpr(expr.expr, statementScope) }
            is ThirExpr.Continue -> TODO()
            is ThirExpr.Break -> breakScope(expr.expr, BreakableTarget.Break(expr.label), source)
            is ThirExpr.Return -> TODO()
            is ThirExpr.Assign -> if (expr.left.ty.needsDrop) {
                TODO()
            } else {
                val blockAndRight = this.toLocalRvalue(expr.right)
                val blockAndLeft = blockAndRight.toPlace(expr.left)
                blockAndLeft.block.pushAssign(blockAndLeft.elem, blockAndRight.elem, source)
                blockAndLeft.block.andUnit()
            }
            is ThirExpr.AssignOp -> {
                val blockAndRight = toLocalOperand(expr.right)
                val blockAndLeft = blockAndRight.toPlace(expr.left)
                val operands: Pair<MirOperand, MirOperand> = MirOperand.Copy(blockAndLeft.elem) to blockAndRight.elem
                val blockAndOperands = blockAndLeft.block and operands
                val result = blockAndOperands.buildBinaryOp(expr.op, expr.left.ty, expr.span)
                result.block.pushAssign(blockAndLeft.elem, result.elem, source)
                result.block.andUnit()
            }
            else -> TODO()
        }
    }

    private fun BlockAnd<*>.breakScope(
        value: ThirExpr?,
        target: BreakableTarget,
        source: MirSourceInfo,
    ): BlockAnd<Unit> {
        fun getBreakableScope(scope: Scope): BreakableScope {
            return scopes.reversedBreakableScopes().find { it.scope == scope }
                ?: error("No enclosing breakable scope found")
        }
        val breakableScope: BreakableScope
        val destination: MirPlace
        when (target) {
            is BreakableTarget.Break -> {
                breakableScope = getBreakableScope(target.scope)
                destination = breakableScope.breakDestination
            }
        }
        val blockAnd: BlockAnd<*>
        if (value != null) {
            blockAnd = this.exprIntoPlace(value, destination)
        } else {
            blockAnd = this
            blockAnd.block.pushAssignUnit(destination, source)
        }
        val scopeIndex = scopes.scopeIndex(breakableScope.scope)
        val drops = if (destination != null) {
            breakableScope.breakDrops
        } else {
            breakableScope.continueDrops ?: error("DropTree for continue drops must exist if we compile continue")
        }
        var dropNode = drops.root
        scopes.scopes().drop(scopeIndex + 1).forEach { scope ->
            scope.drops().forEach { drop ->
                dropNode = drops.addDrop(drop, dropNode)
            }
        }
        drops.addEntry(blockAnd.block, dropNode)
        blockAnd.block.setTerminatorSource(source)
        return basicBlocks.new().andUnit()
    }

    private fun MirBasicBlockImpl.pushAssignUnit(place: MirPlace, source: MirSourceInfo) {
        pushAssign(
            place = place,
            rvalue = MirRvalue.Use(
                MirOperand.Constant(
                    MirConstant.zeroSized(TyUnit.INSTANCE, source.span)
                )
            ),
            source = source,
        )
    }

    private fun BlockAnd<Pair<MirOperand, MirOperand>>.buildBinaryOp(
        op: BinaryOperator,
        ty: Ty,
        span: MirSpan,
    ): BlockAnd<MirRvalue> {
        val left = elem.first
        val right = elem.second
        val source = sourceInfo(span)
        return if (checkOverflow && op is ArithmeticOp && op.isCheckable && ty.isIntegral) {
            val resultTy = TyTuple(listOf(ty, TyBool.INSTANCE))
            val resultPlace = localDecls.tempPlace(resultTy, span)
            val value = resultPlace.makeField(0, ty)
            val overflow = resultPlace.makeField(1, TyBool.INSTANCE)
            block
                .pushAssign(
                    resultPlace,
                    MirRvalue.CheckedBinaryOpUse(op.toMir(), left.toCopy(), right.toCopy()),
                    source,
                )
                .assert(
                    MirOperand.Move(overflow),
                    false,
                    span,
                    MirAssertKind.Overflow(op, left, right),
                )
                .and(MirRvalue.Use(MirOperand.Move(value)))
        } else {
            if (!(ty.isIntegral && (op == ArithmeticOp.DIV || op == ArithmeticOp.REM))) {
                return block and MirRvalue.BinaryOpUse(op.toMir(), left, right)
            }
            op as ArithmeticOp  // `op` is either `ArithmeticOp.DIV` or `ArithmeticOp.REM`

            val zeroAssert = if (op == ArithmeticOp.DIV) {
                MirAssertKind.DivisionByZero(left.toCopy())
            } else {
                MirAssertKind.ReminderByZero(left.toCopy())
            }
            val overflowAssert = MirAssertKind.Overflow(op, left.toCopy(), right.toCopy())

            val isZero = localDecls.tempPlace(TyBool.INSTANCE, span)
            val zero = MirOperand.Constant(toConstant(0, ty, span))
            block.pushAssign(
                place = isZero,
                rvalue = MirRvalue.BinaryOpUse(EqualityOp.EQ.toMir(), right.toCopy(), zero),
                source = source,
            )
            block
                .assert(MirOperand.Move(isZero), false, span, zeroAssert)
                .and(elem)
                .assertDivOverflow(ty, overflowAssert, source)
                .map { MirRvalue.BinaryOpUse(op.toMir(), left, right) }
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
        val negOne = MirOperand.Constant(toConstant(-1, ty, source.span))
        val min = MirOperand.Constant(toConstant(ty.minValue, ty, source.span))

        val isNegOne = localDecls.newTempPlace(TyBool.INSTANCE, source)
        val isMin = localDecls.newTempPlace(TyBool.INSTANCE, source)
        val overflow = localDecls.newTempPlace(TyBool.INSTANCE, source)

        return block
            .pushAssign(
                place = isNegOne,
                rvalue = MirRvalue.BinaryOpUse(EqualityOp.EQ.toMir(), right.toCopy(), negOne),
                source = source,
            )
            .pushAssign(
                place = isMin,
                rvalue = MirRvalue.BinaryOpUse(EqualityOp.EQ.toMir(), left.toCopy(), min),
                source = source,
            )
            .pushAssign(
                place = overflow,
                rvalue = MirRvalue.BinaryOpUse(ArithmeticOp.BIT_AND.toMir(), MirOperand.Move(isNegOne), MirOperand.Move(isMin)),
                source = source,
            )
            .assert(
                cond = MirOperand.Move(overflow),
                expected = false,
                span = source.span,
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
        val isMin = localDecls.newTempPlace(TyBool.INSTANCE, source)
        val eq = MirRvalue.BinaryOpUse(
            op = EqualityOp.EQ.toMir(),
            left = elem.toCopy(),
            right = MirOperand.Constant(toConstant(type.minValue, type, source.span))
        )
        return block
            .pushAssign(isMin, eq, source)
            .assert(
                cond = MirOperand.Move(isMin),
                expected = false,
                span = source.span,
                msg = MirAssertKind.OverflowNeg(elem.toCopy()),
            )
            .and(elem)
    }

    private inline fun <R> inScope(
        scope: Scope,
        crossinline body: () -> BlockAnd<R>,
    ): BlockAnd<R> {
        scopes.push(MirScope(scope))
        return body().popScope()
    }

    private inline fun inIfThenScope(
        scope: Scope,
        source: MirSpan,
        crossinline body: () -> BlockAnd<*>,
    ): BlockAnd<MirBasicBlockImpl> {
        val prevScope = scopes.ifThenScope.also { scopes.ifThenScope = IfThenScope(scope, DropTree()) }
        val thenBlock = body().block
        val ifThenScope = scopes.ifThenScope.also { scopes.ifThenScope = prevScope }
        checkNotNull(ifThenScope)
        val elseBlock = buildExitTree(ifThenScope.elseDrops, ifThenScope.scope, source, null)?.block ?: basicBlocks.new()
        return thenBlock and elseBlock
    }

    private inline fun inBreakableScope(
        loopBlock: MirBasicBlockImpl?,
        breakDestination: MirPlace,
        span: MirSpan,
        action: () -> BlockAnd<Unit>?,
    ): BlockAnd<Unit> {
        val regionScope = scopes.topmost()
        val scope = BreakableScope(
            regionScope,
            breakDestination,
            DropTree(),
            loopBlock?.let { DropTree() }
        )
        scopes.pushBreakable(scope)
        val normalExitBlock = action()
        scopes.popBreakable()
        val breakBlock = buildExitTree(scope.breakDrops, regionScope, span, null)
        scope.continueDrops?.let {
            buildExitTree(it, regionScope, span, loopBlock)
        }
        return when {
            normalExitBlock != null && breakBlock == null -> normalExitBlock
            normalExitBlock == null && breakBlock != null -> breakBlock
            normalExitBlock != null && breakBlock != null -> {
                val target = basicBlocks.new()
                val source = sourceInfo(span)
                normalExitBlock.block.terminateWithGoto(target, source)
                breakBlock.block.terminateWithGoto(target, source)
                target.andUnit()
            }
            else -> basicBlocks.new().andUnit()
        }
    }

    private fun buildExitTree(
        drops: DropTree,
        elseScope: Scope,
        source: MirSpan,
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

    private fun <T> BlockAnd<T>.buildDrops(scope: MirScope): BlockAnd<T> {
        scope.reversedDrops().forEach { drop ->
            when (drop.kind) {
                Drop.Kind.VALUE -> {
                    // TODO
                }
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
        span: MirSpan,
        msg: MirAssertKind,
    ): MirBasicBlockImpl {
        val successBlock = basicBlocks.new()
        this.terminateWithAssert(cond, expected, successBlock, sourceInfo(span), msg)
        divergeFrom(this)
        return successBlock
    }

    private fun divergeFrom(block: MirBasicBlockImpl) {
        val nextDrop = divergeCleanup()
        scopes.unwindDrops.addEntry(block, nextDrop)
    }

    private fun divergeCleanup(): DropTree.DropNode {
        return divergeCleanup(scopes.topmost())
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
            return inScope(expr.regionScope) { toOperand(expr.expr, scope, needsTemporary) }
        }

        return when (MirCategory.of(expr)) {
            MirCategory.Constant -> {
                // TODO: cast to literal is temporary
                if (needsTemporary == NeedsTemporary.No || !expr.ty.needsDrop) {
                    val constant = toConstant(
                        expr as? ThirExpr.Literal ?: error("Unsupported type of constant category"),
                        expr.ty,
                        expr.span,
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

    private fun BlockAnd<*>.toTemp(expr: ThirExpr, scope: Scope?, mutability: Mutability): BlockAnd<MirLocal> {
        val localPlace = localDecls.tempPlace(expr.ty, expr.span, mutability = mutability)
        val source = sourceInfo(expr.span)
        when (expr) {
            is ThirExpr.Scope -> return inScope(expr.regionScope) { toTemp(expr.expr, scope, mutability) }
            is ThirExpr.Break, is ThirExpr.Continue, is ThirExpr.Return -> Unit
            is ThirExpr.Block -> TODO()
            else -> {
                block.pushStorageLive(localPlace.local, source)
                if (scope != null) {
                    scheduleDrop(scope, localPlace.local, Drop.Kind.STORAGE)
                }
            }
        }

        return this
            .exprIntoPlace(expr, localPlace)
            .also {
                if (scope != null) {
                    scheduleDrop(scope, localPlace.local, Drop.Kind.VALUE)
                }
            }
            .map { localPlace.local }
    }

    private fun scheduleDrop(regionScope: Scope, local: MirLocal, dropKind: Drop.Kind) {
        val needsDrop = when (dropKind) {
            Drop.Kind.VALUE -> if (!local.ty.needsDrop) return else true
            Drop.Kind.STORAGE -> false
        }
        scopes.scopes(reversed = true).forEach { scope ->
            if (needsDrop) scope.invalidateCaches()
            if (scope.scope == regionScope) {
                val regionScopeSource = regionScope.span
                scope.addDrop(Drop(local, dropKind, sourceInfo(regionScopeSource.endPoint)))
                return
            }
        }
        error("Corresponding scope is not found")
    }

    private fun toConstant(
        constant: ThirExpr.Literal,
        ty: Ty,
        source: MirSpan,
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

    private fun toConstant(bool: Boolean, ty: Ty, source: MirSpan): MirConstant {
        val value = if (bool) 1L else 0L
        return toConstant(value, ty, source)
    }

    private fun toConstant(value: Long, ty: Ty, source: MirSpan): MirConstant {
        val int = MirScalarInt(value, 0) // TODO: 0 (do I even need this)
        return MirConstant.Value(
            constValue = MirConstValue.Scalar(MirScalar.Int(int)),
            ty = ty,
            span = source,
        )
    }

    private fun MirSourceInfo.Companion.outermost(span: MirSpan): MirSourceInfo {
        return MirSourceInfo(span, sourceScopes.outermost)
    }

    private fun sourceInfo(span: MirSpan): MirSourceInfo {
        return MirSourceInfo(span, sourceScopes.sourceScope)
    }

    private fun LocalsBuilder.tempPlace(
        ty: Ty,
        span: MirSpan,
        internal: Boolean = false,
        mutability: Mutability = Mutability.MUTABLE,
    ): MirPlace {
        return newTempPlace(ty, MirSourceInfo.outermost(span), internal, mutability)
    }

    companion object {
        fun build(file: RsFile): List<MirBody> {
            return buildList {
                file.children.forEach { child ->
                    when (child) {
                        is RsFile -> {}
                        is RsConstant -> add(build(child))
                        is RsFunction -> add(build(child))
                        is RsStructItem -> {}
                        is PsiWhiteSpace -> {}
                        else -> TODO("Type ${child::class} is not supported")
                    }
                }
            }
        }

        fun build(constant: RsConstant): MirBody {
            val builder = MirBuilder(
                element = constant,
                implLookup = ImplLookup.relativeTo(constant),
                checkOverflow = true,
                span = constant.asSpan,
                argCount = 0,
                returnTy = constant.typeReference?.normType ?: error("Could not get return type"),
                returnSpan = constant.typeReference?.asSpan ?: error("Could not find return type source"),
                regionScopeTree = getRegionScopeTree(constant),
            )
            return builder.build(constant)
        }

        fun build(function: RsFunction): MirBody {
            val returnSpan = function.retType
                ?.let { it.typeReference?.asSpan ?: error("Could not get type reference from function return type") }
                ?: function.block?.asStartSpan
                ?: error("Could not get block of function")

            val builder = MirBuilder(
                element = function,
                implLookup = ImplLookup.relativeTo(function),
                checkOverflow = true,
                span = function.asSpan,
                argCount = function.valueParameterList?.valueParameterList?.size ?: error("Could not get parameters"),
                returnTy = function.normReturnType,
                returnSpan = returnSpan,
                regionScopeTree = getRegionScopeTree(function)
            )

            return builder.build(function)
        }
    }
}
