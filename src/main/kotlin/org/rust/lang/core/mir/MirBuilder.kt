/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir

import com.intellij.openapi.util.Ref
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
import org.rust.lang.core.types.ty.*
import org.rust.openapiext.testAssert
import org.rust.lang.core.types.regions.Scope as RegionScope

class MirBuilder private constructor(
    private val element: RsElement,
    private val implLookup: ImplLookup,
    private val mirrorContext: MirrorContext,
    private val checkOverflow: Boolean,
    private val span: MirSpan,
    private val argCount: Int,
    returnTy: Ty,
    returnSpan: MirSpan,
) {
    private val regionScopeTree: ScopeTree get() = mirrorContext.regionScopeTree
    private val basicBlocks = BasicBlocksBuilder()
    private val scopes = Scopes()
    private val sourceScopes = SourceScopesBuilder(span)
    private val localDecls = LocalsBuilder(returnTy, MirSourceInfo.outermost(returnSpan))
    private val varDebugInfo = mutableListOf<MirVarDebugInfo>()
    private val varIndices = mutableMapOf<LocalVar, MirLocalForNode>()

    private val unitTemp by lazy {
        localDecls
            .newLocal(
                ty = TyUnit.INSTANCE,
                source = MirSourceInfo.outermost(span),
            )
            .intoPlace()
    }

    fun buildFunction(thir: Thir, body: RsElement): MirBody {
        inScope(Scope.CallSite(body)) {
            val fnEndSpan = span.end
            val returnBlockAnd = inBreakableScope(null, localDecls.returnPlace(), fnEndSpan) {
                inScope(Scope.Arguments(body)) {
                    basicBlocks
                        .startBlock()
                        .argsAndBody(thir.expr, thir.params, Scope.Arguments(body))
                }
            }
            returnBlockAnd.block.terminateWithReturn(sourceInfo(fnEndSpan))
            buildDropTrees()
            returnBlockAnd
        }
        return finish()
    }

    fun buildConstant(thir: Thir): MirBody {
        basicBlocks
            .startBlock()
            .exprIntoPlace(thir.expr, localDecls.returnPlace())
            .block
            .terminateWithReturn(sourceInfo(element.asSpan))
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

    // TODO: captured values
    private fun BlockAnd<*>.argsAndBody(
        expr: ThirExpr,
        arguments: List<ThirParam>,
        argumentScope: Scope,
    ): BlockAnd<Unit> {
        arguments.forEachIndexed { index, param ->
            val sourceInfo = MirSourceInfo.outermost(param.pat?.source ?: span)
            val argLocal = localDecls.newLocal(ty = param.ty, source = sourceInfo).intoPlace()
            param.pat?.simpleIdent?.let { name ->
                val info = MirVarDebugInfo(
                    name = name,
                    source = sourceInfo,
                    contents = MirVarDebugInfo.Contents.Place(argLocal),
                    argumentIndex = index + 1,
                )
                varDebugInfo.add(info)
            }
        }
        // TODO: insert upvars
        var scope: MirSourceScope? = null
        for ((index, param) in arguments.withIndex()) {
            val local = localDecls[index + 1]
            scheduleDrop(argumentScope, local, Drop.Kind.VALUE)
            if (param.pat == null) continue
            val originalSourceScope = sourceScopes.sourceScope
            // TODO: set_correct_source_scope_for_arg
            when {
                param.pat is ThirPat.Binding
                    && param.pat.mode is ThirBindingMode.ByValue
                    && param.pat.subpattern == null -> {
                    localDecls.update(
                        index = index + 1,
                        mutability = param.pat.mutability,
                        source = MirSourceInfo(local.source.span, sourceScopes.sourceScope),
                        localInfo = if (param.selfKind != null) {
                            MirLocalInfo.User(MirClearCrossCrate.Set(MirBindingForm.ImplicitSelf(param.selfKind)))
                        } else {
                            MirLocalInfo.User(
                                MirClearCrossCrate.Set(
                                    MirBindingForm.Var(
                                        MirVarBindingForm(
                                            bindingMode = MirBindingMode.BindByValue(param.pat.mutability),
                                            tyInfo = param.tySpan,
                                            matchPlace = Pair(null, param.pat.source),
                                            patternSource = param.pat.source
                                        )
                                    )
                                )
                            )
                        }
                    )
                    varIndices[param.pat.variable] = MirLocalForNode.One(localDecls[index + 1])
                }
                else -> TODO()
            }
            sourceScopes.sourceScope = originalSourceScope
        }
        scope?.let { sourceScopes.sourceScope = it }
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

    // https://github.com/rust-lang/rust/blob/f7b831ac8a897273f78b9f47165cf8e54066ce4b/compiler/rustc_mir_build/src/build/expr/into.rs#L18
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
                val conditionScope = localScope()

                val (thenBlock, elseBlock) = inScope(expr.ifThenScope) {
                    val sourceInfo = if (isLet(expr.cond)) {
                        val variableScope = sourceScopes.newSourceScope(expr.then.span)
                        sourceScopes.sourceScope = variableScope
                        MirSourceInfo(expr.then.span, variableScope)
                    } else {
                        sourceInfo(expr.then.span)
                    }

                    inIfThenScope(conditionScope, expr.then.span) {
                        this
                            .thenElseBreak(
                                expr.cond,
                                conditionScope,
                                conditionScope,
                                sourceInfo,
                            )
                            .exprIntoPlace(expr.then, place)
                    }
                }.map {
                    expr.`else`?.let { `else` ->
                        it.andUnit().exprIntoPlace(`else`, place).block
                    } ?: run {
                        it.pushAssignUnit(place, sourceInfo(expr.span.end))
                        it
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
            is ThirExpr.Call -> {
                var blockAnd = this
                blockAnd = blockAnd.toLocalOperand(expr.callee)
                val callee = blockAnd.elem
                val args = expr
                    .args
                    .map {
                        blockAnd = blockAnd.toLocalCallOperand(it)
                        blockAnd.elem as MirOperand
                    }
                val success = basicBlocks.new()
                // TODO record_operands_moved(args)
                blockAnd.block.terminateWithCall(
                    callee,
                    args,
                    destination = place,
                    target = success,
                    unwind = null,
                    expr.fromCall,
                    source
                )
                divergeFrom(blockAnd.block)
                success.andUnit()
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
                // first process the set of fields that were provided (evaluating them in order given by user)
                val fieldsMap = run {
                    var block = block
                    expr.fields.associateBy({ it.name }, {
                        val blockAndOperand = block.andUnit().toOperand(it.expr, scopes.topmost(), NeedsTemporary.Maybe)
                        block = blockAndOperand.block
                        blockAndOperand.elem
                    })
                }

                val fieldsNames = expr.definition.variant(expr.variantIndex).fields.indices

                val fields = if (expr.base != null) {
                    TODO()
                } else {
                    fieldsNames.map {
                        fieldsMap.getOrElse(it) {
                            throw IllegalStateException("Mismatched fields in struct literal and definition")
                        }
                    }
                }

                val rvalue = MirRvalue.Aggregate.Adt(expr.definition, expr.variantIndex, expr.ty, fields)
                this
                    .block
                    .pushAssign(place, rvalue, source)
                    .andUnit()
            }
            is ThirExpr.Use -> exprIntoPlace(expr.source, place)
            is ThirExpr.Borrow -> {
                val blockAnd = when (expr.kind) {
                    MirBorrowKind.Shared -> this.toReadOnlyPlace(expr.arg)
                    else -> this.toPlace(expr.arg)
                }
                val borrow = MirRvalue.Ref(expr.kind, blockAnd.elem)
                blockAnd.block.pushAssign(place, borrow, source)
                blockAnd.block.andUnit()
            }
            is ThirExpr.Match -> matchExpr(place, expr.span, expr.expr, expr.arms)
            is ThirExpr.Let -> {
                val scope = localScope()
                val (trueBlock, falseBlock) = inIfThenScope(scope, expr.span) {
                    lowerLetExpr(expr.expr, expr.pat, scope, null, expr.span, true)
                }

                val trueConstValue = MirConstValue.Scalar(MirScalar.from(true))
                val trueConstant = MirConstant.Value(trueConstValue, TyBool.INSTANCE, expr.span)
                trueBlock.pushAssignConstant(place, trueConstant, source)

                val falseConstValue = MirConstValue.Scalar(MirScalar.from(false))
                val falseConstant = MirConstant.Value(falseConstValue, TyBool.INSTANCE, expr.span)
                falseBlock.pushAssignConstant(place, falseConstant, source)

                val joinBlock = basicBlocks.new()
                trueBlock.terminateWithGoto(joinBlock, source)
                falseBlock.terminateWithGoto(joinBlock, source)
                joinBlock.andUnit()
            }
            else -> TODO()
        }
    }

    /**
     * Adds a new temporary value of type `ty` storing the result of evaluating `expr`.
     * N.B., **No cleanup is scheduled for this temporary.** You should call `scheduleDrop` once the temporary is initialized.
     */
    fun temp(ty: Ty, span: MirSpan): MirPlace {
        val temp = localDecls.newLocal(internal = true, ty = ty, source = MirSourceInfo.outermost(span))
        return MirPlace(temp)
    }

    private fun consumeByCopyOrMove(place: MirPlace): MirOperand {
        return if (place.ty().ty.isMovesByDefault(implLookup)) {
            MirOperand.Move(place)
        } else {
            MirOperand.Copy(place)
        }
    }

    private fun BlockAnd<*>.toReadOnlyPlace(expr: ThirExpr): BlockAnd<MirPlace> {
        return toReadOnlyPlaceBuilder(expr).map { it.toPlace() }
    }

    // https://github.com/rust-lang/rust/blob/f7b831ac8a897273f78b9f47165cf8e54066ce4b/compiler/rustc_mir_build/src/build/expr/as_place.rs#L357
    private fun BlockAnd<*>.toPlace(expr: ThirExpr): BlockAnd<MirPlace> {
        return toPlaceBuilder(expr).map { it.toPlace() }
    }

    private fun BlockAnd<*>.toReadOnlyPlaceBuilder(expr: ThirExpr): BlockAnd<PlaceBuilder> {
        return exprToPlace(expr, Mutability.IMMUTABLE)
    }

    // https://github.com/rust-lang/rust/blob/f7b831ac8a897273f78b9f47165cf8e54066ce4b/compiler/rustc_mir_build/src/build/expr/as_place.rs#L368
    private fun BlockAnd<*>.toPlaceBuilder(expr: ThirExpr): BlockAnd<PlaceBuilder> {
        return exprToPlace(expr, Mutability.MUTABLE)
    }

    // https://github.com/rust-lang/rust/blob/f7b831ac8a897273f78b9f47165cf8e54066ce4b/compiler/rustc_mir_build/src/build/expr/as_place.rs#L404
    private fun BlockAnd<*>.exprToPlace(
        expr: ThirExpr,
        mutability: Mutability,
        fakeBorrowTemps: MutableList<MirLocal>? = null
    ): BlockAnd<PlaceBuilder> {
        val exprSpan = expr.span
        val sourceInfo = sourceInfo(exprSpan)
        return when (expr) {
            is ThirExpr.Scope -> {
                inScope(expr.regionScope) {
                    exprToPlace(expr.expr, mutability, fakeBorrowTemps)
                }
            }

            is ThirExpr.Field -> {
                exprToPlace(expr.expr, mutability, fakeBorrowTemps)
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

            is ThirExpr.Deref -> {
                exprToPlace(expr.arg, mutability, fakeBorrowTemps).map { it.deref() }
            }

            is ThirExpr.Index -> {
                lowerIndexExpression(
                    expr.lhs,
                    expr.index,
                    mutability,
                    fakeBorrowTemps,
                    expr.tempLifetime,
                    exprSpan,
                    sourceInfo
                )
            }

            is ThirExpr.UpvarRef -> TODO()

            is ThirExpr.VarRef -> {
                // TODO: different handling in case of guards
                block and PlaceBuilder(varLocal(expr.local))
            }

            is ThirExpr.PlaceTypeAscription -> TODO()

            is ThirExpr.ValueTypeAscription -> TODO()

            is ThirExpr.Array,
            is ThirExpr.Tuple,
            is ThirExpr.Cast,
            is ThirExpr.Adt,
            is ThirExpr.Closure,
            is ThirExpr.Unary,
            is ThirExpr.Binary,
            is ThirExpr.Logical,
            is ThirExpr.Box,
            is ThirExpr.Cast,
            is ThirExpr.Use,
            is ThirExpr.NeverToAny,
            is ThirExpr.Pointer,
            is ThirExpr.Repeat,
            is ThirExpr.Borrow,
            is ThirExpr.AddressOf,
            is ThirExpr.Match,
            is ThirExpr.If,
            is ThirExpr.Loop,
            is ThirExpr.Block,
            is ThirExpr.Let,
            is ThirExpr.Assign,
            is ThirExpr.AssignOp,
            is ThirExpr.Break,
            is ThirExpr.Continue,
            is ThirExpr.Return,
            is ThirExpr.Literal,
            is ThirExpr.NamedConst,
            is ThirExpr.NonHirLiteral,
            is ThirExpr.ZstLiteral,
            is ThirExpr.ConstParam,
            is ThirExpr.ConstBlock,
            is ThirExpr.StaticRef,
            is ThirExpr.InlineAsm,
            is ThirExpr.OffsetOf,
            is ThirExpr.Yield,
            is ThirExpr.ThreadLocalRef,
            is ThirExpr.Call -> {
                toTemp(expr, expr.tempLifetime, mutability)
                    .map { PlaceBuilder(it) }
            }
        }
    }

    private fun BlockAnd<*>.lowerIndexExpression(
        base: ThirExpr,
        index: ThirExpr,
        mutability: Mutability,
        fakeBorrowTemps: MutableList<MirLocal>?,
        tempLifetime: RegionScope?,
        span: MirSpan,
        sourceInfo: MirSourceInfo
    ): BlockAnd<PlaceBuilder> {
        var blockAnd = this
        val newFakeBorrowTemps = fakeBorrowTemps ?: mutableListOf()

        blockAnd = blockAnd.exprToPlace(base, mutability, newFakeBorrowTemps)
        val basePlace = blockAnd.elem

        // Making this a *fresh* temporary means we do not have to worry about the index changing later: Nothing will
        // ever change this temporary. The "retagging" transformation (for Stacked Borrows) relies on this.
        blockAnd = blockAnd.toTemp(index, tempLifetime, Mutability.IMMUTABLE)
        val idx = blockAnd.elem

        var block = boundsCheck(blockAnd.block, basePlace, idx, span, sourceInfo)

        val isOutermostIndex = fakeBorrowTemps == null
        if (isOutermostIndex) {
            block = readFakeBorrows(block, newFakeBorrowTemps, sourceInfo)
        } else {
            addFakeBorrowsOfBase(basePlace.toPlace())
        }

        return block and basePlace.index(idx)
    }

    private fun BlockAnd<*>.lowerLetExpr(
        expr: ThirExpr,
        pat: ThirPat,
        elseTarget: RegionScope,
        sourceScope: MirSourceScope?,
        span: MirSpan,
        declareBindings: Boolean
    ): BlockAnd<Unit> {
        var blockAnd = this
        blockAnd = blockAnd.lowerScrutinee(expr)
        val exprPlaceBuilder = blockAnd.elem
        val wildcard = ThirPat.Wild(pat.ty, MirSpan.Fake)
        val guardCandidate = MirCandidate(exprPlaceBuilder.clone(), pat, false)
        val otherwiseCandidate = MirCandidate(exprPlaceBuilder.clone(), wildcard, false)
        val fakeBorrowTemps = lowerMatchTree(
            blockAnd.block,
            pat.source,
            pat.source,
            false,
            listOf(guardCandidate, otherwiseCandidate)
        )
        val exprPlace = exprPlaceBuilder.tryToPlace()
        val otherwisePostGuardBlock = otherwiseCandidate.preBindingBlock!!
        otherwisePostGuardBlock.breakForElse(elseTarget, sourceInfo(expr.span))

        if (declareBindings) {
            declareBindings(sourceScope, pat.source, pat, null, exprPlace to expr.span)
        }

        val postGuardBlock = bindPattern(sourceInfo(pat.source), guardCandidate, expr.span, null, false)
        return postGuardBlock.andUnit()
    }

    private fun boundsCheck(
        block: MirBasicBlockImpl,
        slice: PlaceBuilder,
        index: MirLocal,
        span: MirSpan,
        sourceInfo: MirSourceInfo
    ): MirBasicBlockImpl {
        val usizeTy = TyInteger.USize.INSTANCE
        val boolTy = TyBool.INSTANCE
        // bounds check:
        val len = localDecls
            .newLocal(
                ty = usizeTy,
                source = MirSourceInfo.outermost(span),
            )
            .intoPlace()
        val lt = localDecls
            .newLocal(
                ty = boolTy,
                source = MirSourceInfo.outermost(span),
            )
            .intoPlace()

        // len = len(slice)
        var blockTemp = block.pushAssign(len, MirRvalue.Len(slice.toPlace()), sourceInfo)
        // lt = idx < len
        blockTemp = blockTemp.pushAssign(
            lt,
            MirRvalue.BinaryOpUse(ComparisonOp.LT.toMir(), MirOperand.Copy(MirPlace(index)), MirOperand.Copy(len)),
            sourceInfo
        )

        val msg = MirAssertKind.BoundsCheck(MirOperand.Move(len), MirOperand.Copy(MirPlace(index)))
        blockTemp = blockTemp.assert(MirOperand.Move(lt), true, span, msg)

        return blockTemp
    }

    private fun addFakeBorrowsOfBase(basePlace: MirPlace) {
        val placeTy = basePlace.ty()
        if (placeTy.ty is TySlice) {
            // We need to create fake borrows to ensure that the bounds check that we just did stays valid. Since we
            // can't assign to unsized values, we only need to ensure that none of the pointers in the base place are
            // modified.
            for ((idx, elem) in basePlace.projections.withIndex().reversed()) {
                when (elem) {
                    is MirProjectionElem.Deref -> {
                        // TODO
                    }

                    is MirProjectionElem.Index -> {
                        val indexTy = MirPlace.tyFrom(basePlace.local, basePlace.projections.subList(0, idx))
                        when (indexTy.ty) {
                            // The previous index expression has already done any index expressions needed here.
                            is TySlice -> break
                            is TyArray -> Unit
                            else -> error("unexpected index base")
                        }
                    }

                    is MirProjectionElem.Field,
                    is MirProjectionElem.Downcast,
                    is MirProjectionElem.ConstantIndex -> Unit
                }
            }
        }
    }

    private fun readFakeBorrows(
        block: MirBasicBlockImpl,
        fakeBorrowTemps: MutableList<MirLocal>,
        sourceInfo: MirSourceInfo
    ): MirBasicBlockImpl {
        var blockTemp = block
        // All indexes have been evaluated now, read all of the fake borrows so that they are live across those index
        // expressions.
        for (temp in fakeBorrowTemps) {
            blockTemp = blockTemp.pushFakeRead(MirStatement.FakeRead.Cause.ForIndex, MirPlace(temp), sourceInfo)
        }
        return blockTemp
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
            is ThirExpr.Let -> lowerLetExpr(
                cond.expr,
                cond.pat,
                breakScope,
                variableSource.scope,
                variableSource.span,
                declareBindings = true
            )
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

    private fun BlockAnd<*>.matchExpr(
        destination: MirPlace,
        span: MirSpan,
        scrutinee: ThirExpr,
        arms: List<MirArm>
    ): BlockAnd<Unit> {
        val (block, scrutineePlace) = lowerScrutinee(scrutinee)

        val candidates = arms.map {
            MirCandidate(scrutineePlace.clone(), it.pattern, it.guard != null)
        }
        val matchHasGuard = candidates.any { it.hasGuard }

        val matchStartSpan = (span.reference as RsMatchExpr).match.asSpan  // TODO
        lowerMatchTree(block, scrutinee.span, matchStartSpan, matchHasGuard, candidates)

        return lowerMatchArms(
            destination,
            scrutineePlace,
            scrutinee.span,
            arms zip candidates,
            sourceInfo(span)
        )
    }

    private fun BlockAnd<*>.lowerScrutinee(scrutinee: ThirExpr): BlockAnd<PlaceBuilder> {
        val result = toPlaceBuilder(scrutinee)
        result.elem.tryToPlace()?.let { scrutineePlace ->
            val cause = MirStatement.FakeRead.Cause.ForMatchedPlace(null)
            val sourceInfo = sourceInfo(scrutinee.span)
            result.block.pushFakeRead(cause, scrutineePlace, sourceInfo)
        }
        return result
    }

    private fun lowerMatchTree(
        block: MirBasicBlockImpl,
        scrutineeSpan: MirSpan,
        matchStartSpan: MirSpan,
        matchHasGuard: Boolean,
        candidates: List<MirCandidate>
    ) {
        val otherwise = Ref<MirBasicBlockImpl>(null)
        matchCandidates(matchStartSpan, scrutineeSpan, block, otherwise, candidates)

        if (!otherwise.isNull) {
            val sourceInfo = sourceInfo(scrutineeSpan)
            otherwise.get().terminateWithUnreachable(sourceInfo)
        }

        // Link each leaf candidate to the `preBindingBlock` of the next one.
        var previousCandidate: MirCandidate? = null
        for (candidate in candidates) {
            candidate.visitLeaves { leafCandidate ->
                previousCandidate?.nextCandidatePreBindingBlock = leafCandidate.preBindingBlock
                previousCandidate = leafCandidate
            }
        }

        // TODO fake borrows
    }

    /**
     * Lower the bindings, guards and arm bodies of a `match` expression.
     * The decision tree should have already been created by [lowerMatchTree].
     * [outerSourceInfo] is the [MirSourceInfo] for the whole match.
     */
    private fun lowerMatchArms(
        destination: MirPlace,
        scrutineePlaceBuilder: PlaceBuilder,
        scrutineeSpan: MirSpan,
        armCandidates: List<Pair<MirArm, MirCandidate>>,
        outerSourceInfo: MirSourceInfo
    ): BlockAnd<Unit> {
        val armEndBlocks = armCandidates.map { (arm, candidate) ->
            val matchScope = localScope()
            inScope(arm.scope) {
                val scrutineePlace = scrutineePlaceBuilder.tryToPlace()
                val optScrutineePlace = scrutineePlace?.let { it to scrutineeSpan }
                val scope = declareBindings(null, arm.span, arm.pattern, arm.guard, optScrutineePlace)

                val armBlock = bindPattern(
                    outerSourceInfo,
                    candidate,
                    scrutineeSpan,
                    arm to matchScope,
                    storagesAlive = false
                )

                scope?.let {
                    sourceScopes.sourceScope = it
                }

                armBlock.andUnit().exprIntoPlace(arm.body, destination)
            }
        }

        // all the arm blocks will rejoin here
        val endBlock = basicBlocks.new()
        val endBrace = sourceInfo(outerSourceInfo.span.endPoint)
        for (armBlock in armEndBlocks) {
            val block = armBlock.block
            val lastLocation = block.statements.lastOrNull()?.source
            block.terminateWithGoto(endBlock, lastLocation ?: endBrace)
        }

        sourceScopes.sourceScope = outerSourceInfo.scope
        return endBlock.andUnit()
    }

    /**
     * Binds the variables and ascribes types for a given `match` arm or `let` binding.
     *
     * Also check if the guard matches, if it's provided.
     * [armMatchScope] should be not null if and only if this is called for a `match` arm.
     */
    private fun bindPattern(
        outerSourceInfo: MirSourceInfo,
        candidate: MirCandidate,
        scrutineeSpan: MirSpan,
        armMatchScope: Pair<MirArm, Scope>?,
        storagesAlive: Boolean
    ): MirBasicBlockImpl {
        return if (candidate.subcandidates.isEmpty()) {
            // Avoid generating another `BasicBlock` when we only have one candidate.
            bindAndGuardMatchedCandidate(
                candidate,
                parentBindings = emptyList(),
                scrutineeSpan,
                armMatchScope,
                scheduleDrops = true,
                storagesAlive,
            )
        } else {
            TODO()
        }
    }

    private fun bindAndGuardMatchedCandidate(
        candidate: MirCandidate,
        parentBindings: List<MirBinding>,
        scrutineeSpan: MirSpan,
        armMatchScope: Pair<MirArm, Scope>?,
        scheduleDrops: Boolean,
        storagesAlive: Boolean
    ): MirBasicBlockImpl {
        testAssert { candidate.matchPairs.isEmpty() }
        val candidateSourceInfo = sourceInfo(candidate.span)
        var block = candidate.preBindingBlock!!
        if (candidate.nextCandidatePreBindingBlock != null) {
            val freshBlock = basicBlocks.new()
            block.terminateWithFalseEdges(freshBlock, candidate.nextCandidatePreBindingBlock, candidateSourceInfo)
            block = freshBlock
        }

        // TODO ascribe_types

        return if (armMatchScope != null && armMatchScope.first.guard != null) {
            TODO()
        } else {
            bindMatchedCandidateForArmBody(block, scheduleDrops, parentBindings + candidate.bindings, storagesAlive)
            block
        }
    }

    private fun bindMatchedCandidateForArmBody(
        block: MirBasicBlockImpl,
        scheduleDrops: Boolean,
        bindings: List<MirBinding>,
        storagesAlive: Boolean
    ) {
        for (binding in bindings) {
            val sourceInfo = sourceInfo(binding.span)
            val local = if (storagesAlive) {
                // Here storages are already alive, probably because this is a binding from let-else.
                // We just need to schedule drop for the value.
                varLocal(binding.variable).intoPlace()
            } else {
                block.storageLiveBinding(binding.variable, binding.span, scheduleDrops)
            }
            if (scheduleDrops) {
                scheduleDropForBinding(binding.variable, binding.span)
            }
            val rvalue = when (val bindingMode = binding.bindingMode) {
                ThirBindingMode.ByValue -> MirRvalue.Use(consumeByCopyOrMove(binding.source))
                is ThirBindingMode.ByRef -> MirRvalue.Ref(bindingMode.kind, binding.source)
            }
            block.pushAssign(local, rvalue, sourceInfo)
        }
    }

    private fun matchCandidates(
        span: MirSpan,
        scrutineeSpan: MirSpan,
        startBlock: MirBasicBlockImpl,
        otherwiseBlock: Ref<MirBasicBlockImpl>,
        candidates: List<MirCandidate>
    ) {
        var splitOrCandidate = false
        for (candidate in candidates) {
            if (simplifyCandidate(candidate)) {
                splitOrCandidate = true
            }
        }

        if (splitOrCandidate) {
            val newCandidates = mutableListOf<MirCandidate>()
            for (candidate in candidates) {
                candidate.visitLeaves {
                    newCandidates += it
                }
            }
            matchSimplifiedCandidates(span, scrutineeSpan, startBlock, otherwiseBlock, newCandidates)
        } else {
            matchSimplifiedCandidates(span, scrutineeSpan, startBlock, otherwiseBlock, candidates)
        }
    }

    private fun simplifyCandidate(candidate: MirCandidate): Boolean {
        val existingBindings = candidate.bindings
        candidate.bindings = mutableListOf()
        var newBindings = mutableListOf<MirBinding>()
        while (true) {
            val matchPairs = candidate.matchPairs
            candidate.matchPairs = mutableListOf()

            if (matchPairs.singleOrNull()?.pattern is ThirPat.Or) {
                TODO()
            }

            var changed = false
            for (matchPair in matchPairs) {
                if (simplifyMatchPair(matchPair, candidate)) {
                    changed = true
                } else {
                    candidate.matchPairs += matchPair
                }
            }

            // order is important - https://github.com/rust-lang/rust/issues/69971
            newBindings = (candidate.bindings + newBindings).toMutableList()
            candidate.bindings.clear()

            if (!changed) {
                candidate.bindings = (existingBindings + newBindings).toMutableList()
                existingBindings.clear()
                // Move or-patterns to the end, because they can result in us creating additional candidates,
                // so we want to test them as late as possible.
                candidate.matchPairs.sortBy { it.pattern is ThirPat.Or }
                return false // if we were not able to simplify any, done.
            }
        }
    }

    private fun simplifyMatchPair(matchPair: MirMatchPair, candidate: MirCandidate): Boolean {
        return when (val pattern = matchPair.pattern) {
            is ThirPat.AscribeUserType -> true
            is ThirPat.Wild -> true
            is ThirPat.Binding -> {
                matchPair.place.tryToPlace()?.let { source ->
                    candidate.bindings += MirBinding(
                        matchPair.pattern.source,
                        source,
                        pattern.variable,
                        pattern.mode,
                    )
                }
                if (pattern.subpattern != null) {
                    TODO()
                }
                true
            }
            is ThirPat.Const -> TODO()
            is ThirPat.Range -> TODO()
            is ThirPat.Slice -> TODO()
            is ThirPat.Variant -> {
                val irrefutable = pattern.item.variants.size == 1  // TODO exhaustive_patterns
                if (irrefutable) {
                    val placeBuilder = matchPair.place.downcast(pattern.item, pattern.variantIndex)
                    candidate.matchPairs += fieldMatchPairs(placeBuilder, pattern.subpatterns)
                    return true
                } else {
                    return false
                }
            }
            is ThirPat.Array -> TODO()
            is ThirPat.Leaf -> {
                candidate.matchPairs += fieldMatchPairs(matchPair.place, pattern.subpatterns)
                return true
            }
            is ThirPat.Deref -> {
                val placeBuilder = matchPair.place.deref()
                candidate.matchPairs += MirMatchPair.new(placeBuilder, pattern.subpattern)
                true
            }
            is ThirPat.Or -> TODO()
        }
    }

    private fun fieldMatchPairs(place: PlaceBuilder, subpatterns: List<ThirFieldPat>): List<MirMatchPair> {
        return subpatterns.map {
            val place2 = place.cloneProject(MirProjectionElem.Field(it.field, it.pattern.ty))
            MirMatchPair.new(place2, it.pattern)
        }
    }

    private fun matchSimplifiedCandidates(
        span: MirSpan,
        scrutineeSpan: MirSpan,
        startBlock: MirBasicBlockImpl,
        otherwiseBlock: Ref<MirBasicBlockImpl>,
        candidates: List<MirCandidate>
    ) {
        val matchedCandidates = candidates.takeWhile { it.matchPairs.isEmpty() }
        val unmatchedCandidates = candidates.subList(matchedCandidates.size, candidates.size)

        val block = if (matchedCandidates.isNotEmpty()) {
            selectMatchedCandidates(matchedCandidates, startBlock)
                ?: run {
                    if (unmatchedCandidates.isEmpty()) {
                        // Any remaining candidates are unreachable.
                        return
                    }
                    basicBlocks.new()
                }
        } else {
            startBlock
        }

        if (unmatchedCandidates.isEmpty()) {
            if (!otherwiseBlock.isNull) {
                val sourceInfo = sourceInfo(span)
                block.terminateWithGoto(otherwiseBlock.get(), sourceInfo)
            } else {
                otherwiseBlock.set(block)
            }
            return
        }

        testCandidatesWithOr(span, scrutineeSpan, unmatchedCandidates, block, otherwiseBlock)
    }

    private fun selectMatchedCandidates(
        matchedCandidates: List<MirCandidate>,
        startBlock: MirBasicBlockImpl
    ): MirBasicBlockImpl? {
        testAssert { matchedCandidates.isNotEmpty() }
        testAssert { matchedCandidates.all { it.subcandidates.isEmpty() } }

        // TODO fake borrows

        val fullyMatchedWithGuard = matchedCandidates.indexOfFirst { !it.hasGuard }
        val reachableCandidates = matchedCandidates.subList(0, fullyMatchedWithGuard + 1)
        val unreachableCandidates = matchedCandidates.subList(fullyMatchedWithGuard + 1, matchedCandidates.size)

        var nextPrebinding = startBlock
        for (candidate in reachableCandidates) {
            testAssert { candidate.otherwiseBlock == null }
            testAssert { candidate.preBindingBlock == null }
            candidate.preBindingBlock = nextPrebinding
            if (candidate.hasGuard) {
                // Create the otherwise block for this candidate, which is the pre-binding block for the next candidate.
                nextPrebinding = basicBlocks.new()
                candidate.otherwiseBlock = nextPrebinding
            }
        }

        for (candidate in unreachableCandidates) {
            testAssert { candidate.preBindingBlock == null }
            candidate.preBindingBlock = basicBlocks.new()
        }

        return reachableCandidates.last().otherwiseBlock
    }

    private fun testCandidatesWithOr(
        span: MirSpan,
        scrutineeSpan: MirSpan,
        candidates: List<MirCandidate>,
        block: MirBasicBlockImpl,
        otherwiseBlock: Ref<MirBasicBlockImpl>
    ) {
        val firstCandidate = candidates.first()
        if (firstCandidate.matchPairs.first().pattern !is ThirPat.Or) {
            testCandidates(span, scrutineeSpan, candidates, block, otherwiseBlock)
            return
        }

        TODO()
    }

    private fun testCandidates(
        span: MirSpan,
        scrutineeSpan: MirSpan,
        candidates1: List<MirCandidate>,
        block: MirBasicBlockImpl,
        otherwiseBlock: Ref<MirBasicBlockImpl>
    ) {
        // Extract the match-pair from the highest priority candidate
        val matchPair = candidates1.first().matchPairs.first()
        val test = MirTest.test(matchPair)
        val matchPlace = matchPair.place.clone()

        // Most of the time, the test to perform is simply a function of the main candidate;
        // But for a test like SwitchInt, we may want to add cases based on the candidates that are available
        when (test) {
            is MirTest.SwitchInt -> TODO()
            is MirTest.Switch -> {
                for (candidate in candidates1) {
                    if (!addVariantsToSwitch(matchPlace, candidate, test.variants)) {
                        break
                    }
                }
            }
            else -> Unit
        }

        // TODO Insert a Shallow borrow of any places that is switched on.

        // Perform the test, branching to one of N blocks.
        // For each of those N possible outcomes, create a (initially empty) vector of candidates.
        // Those are the candidates that still apply if the test has that particular outcome.
        val targetCandidates = Array(test.targets()) { mutableListOf<MirCandidate>() }

        // Sort the candidates into the appropriate vector in [targetCandidates].
        // Note that at some point we may encounter a candidate where the test is not relevant;
        // at that point, we stop sorting.
        val candidates2 = candidates1.dropWhile {
            val index = sortCandidate(matchPlace, test, it) ?: return@dropWhile false
            targetCandidates[index] += it
            true
        }
        // at least the first candidate ought to be tested
        testAssert { candidates1.size > candidates2.size }

        val makeTargetBlocks = {
            val remainderStart = if (candidates2.isEmpty()) {
                otherwiseBlock
            } else {
                Ref<MirBasicBlockImpl>()
            }
            val targetBlocks = targetCandidates.map { candidates ->
                if (candidates.isNotEmpty()) {
                    val candidateStart = basicBlocks.new()
                    matchCandidates(span, scrutineeSpan, candidateStart, remainderStart, candidates)
                    candidateStart
                } else {
                    remainderStart.get() ?: run {
                        basicBlocks.new().also { remainderStart.set(it) }
                    }
                }
            }

            if (candidates2.isNotEmpty()) {
                matchCandidates(
                    span,
                    scrutineeSpan,
                    remainderStart.get() ?: basicBlocks.new(),
                    otherwiseBlock,
                    candidates2
                )
            }

            targetBlocks
        }

        performTest(span, scrutineeSpan, block, matchPlace, test, makeTargetBlocks)
    }

    // https://github.com/rust-lang/rust/blob/f7b831ac8a897273f78b9f47165cf8e54066ce4b/compiler/rustc_mir_build/src/build/block.rs#L9
    private fun BlockAnd<*>.astBlockIntoPlace(
        block: ThirBlock,
        place: MirPlace,
        span: MirSpan,
    ): BlockAnd<Unit> {
        return inScope(block.destructionScope) {
            inScope(block.regionScope) {
                // TODO: add case if targeted by break
                astBlockStmtsIntoPlace(block, place, span)
            }
        }
    }

    // https://github.com/rust-lang/rust/blob/f7b831ac8a897273f78b9f47165cf8e54066ce4b/compiler/rustc_mir_build/src/build/block.rs#L55
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
                    pushScope(statement.remainderScope)
                    letScopeStack.add(statement.remainderScope)
                    val remainderSourceInfo = statement.remainderScope.span
                    val visibilityScope = sourceScopes.newSourceScope(remainderSourceInfo)
                    if (statement.initializer != null) {
                        blockAnd = inScope(statement.destructionScope) {
                            inScope(statement.initScope) {
                                declareBindings(
                                    visibilityScope,
                                    remainderSourceInfo,
                                    statement.pattern,
                                    guard = null,
                                    null to statement.initializer.span
                                )
                                blockAnd.exprIntoPattern(statement.pattern, statement.initializer)
                            }
                        }
                    } else {
                        blockAnd = inScope(statement.destructionScope) {
                            inScope(statement.initScope) {
                                declareBindings(
                                    visibilityScope,
                                    remainderSourceInfo,
                                    statement.pattern,
                                    guard = null,
                                    null,
                                )
                                blockAnd
                            }
                        }
                        visitPrimaryBindings(statement.pattern) { _, _, _, node, span, _ ->
                            blockAnd.block.storageLiveBinding(node, span, true)
                            scheduleDropForBinding(node, span)
                        }
                    }
                    sourceScopes.sourceScope = visibilityScope
                }
                statement is ThirStatement.Expr -> {
                    blockAnd = inScope(statement.destructionScope) {
                        inScope(statement.scope) {
                            blockAnd.statementExpr(statement.expr, statement.scope)
                        }
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
        regionScopeTree.getVariableScope(variable)?.let { scope ->
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
            regionScopeTree.getVariableScope(variable)?.let { scope ->
                scheduleDrop(scope, local, Drop.Kind.STORAGE)
            }
        }
        return MirPlace(local)
    }

    private fun varLocal(variable: LocalVar): MirLocal {
        return when (val localForNode = varIndices[variable]) {
            is MirLocalForNode.ForGuard -> TODO()
            is MirLocalForNode.One -> localForNode.local
            null -> error("Could not find variable: ${variable.name}")
        }
    }

    private fun declareBindings(
        visibilityScope: MirSourceScope?,
        scopeSource: MirSpan,
        pattern: ThirPat,
        guard: Any?,
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
        if (guard != null) {
            TODO()
        }
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
        val debugSource = MirSourceInfo(source.span, visibilityScope)
        val bindingMode = when (mode) {
            ThirBindingMode.ByValue -> MirBindingMode.BindByValue(mutability)
            is ThirBindingMode.ByRef -> MirBindingMode.BindByReference(mutability)
        }
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
                            bindingMode = bindingMode,
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

    /**
     * Visit all the primary bindings in a patterns, that is,
     * visit the leftmost occurrence of each variable bound in a pattern.
     * A variable will occur more than once in an or-pattern.
     */
    private fun visitPrimaryBindings(
        pattern: ThirPat,
        action: (Mutability, name: String, ThirBindingMode, LocalVar, MirSpan, Ty) -> Unit,
    ) {
        when (pattern) {
            is ThirPat.AscribeUserType -> TODO()
            is ThirPat.Binding -> {
                if (pattern.isPrimary) {
                    action(pattern.mutability, pattern.name, pattern.mode, pattern.variable, pattern.source, pattern.ty)
                }
                if (pattern.subpattern != null) {
                    visitPrimaryBindings(pattern.subpattern, action)
                }
            }
            is ThirPat.Const, is ThirPat.Range, is ThirPat.Wild -> Unit
            is ThirPat.Deref -> {
                visitPrimaryBindings(pattern.subpattern, action)
            }
            is ThirPat.Slice -> TODO()
            is ThirPat.Leaf -> {
                for (subpattern in pattern.subpatterns) {
                    visitPrimaryBindings(subpattern.pattern, action)
                }
            }
            is ThirPat.Variant -> {
                for (subpattern in pattern.subpatterns) {
                    visitPrimaryBindings(subpattern.pattern, action)
                }
            }
            is ThirPat.Array -> TODO()
            is ThirPat.Or -> TODO()
        }
    }

    // https://github.com/rust-lang/rust/blob/f7b831ac8a897273f78b9f47165cf8e54066ce4b/compiler/rustc_mir_build/src/build/expr/as_rvalue.rs#L28
    private fun BlockAnd<*>.toLocalRvalue(expr: ThirExpr): BlockAnd<MirRvalue> {
        return toRvalue(expr, scopes.topmost())
    }

    private fun BlockAnd<*>.toLocalOperand(expr: ThirExpr): BlockAnd<MirOperand> {
        return toOperand(expr, scopes.topmost(), NeedsTemporary.Maybe)
    }

    private fun BlockAnd<*>.toLocalCallOperand(expr: ThirExpr): BlockAnd<MirOperand> {
        return toCallOperand(expr, scopes.topmost())
    }

    private fun BlockAnd<*>.toCallOperand(expr: ThirExpr, scope: Scope): BlockAnd<MirOperand> {
        if (expr is ThirExpr.Scope) {
            return inScope(expr.regionScope) { toCallOperand(expr.expr, scope) }
        }
        // TODO unsized_fn_params
        return toOperand(expr, scope, NeedsTemporary.Maybe)
    }

    // https://github.com/rust-lang/rust/blob/f7b831ac8a897273f78b9f47165cf8e54066ce4b/compiler/rustc_mir_build/src/build/expr/as_rvalue.rs#L38
    private fun BlockAnd<*>.toRvalue(expr: ThirExpr, scope: Scope): BlockAnd<MirRvalue> {
        return when (expr) {
            is ThirExpr.Scope -> inScope(expr.regionScope) { toRvalue(expr.expr, scope) }
            is ThirExpr.Literal,
            is ThirExpr.NamedConst,
            is ThirExpr.NonHirLiteral,
            is ThirExpr.ZstLiteral,
            is ThirExpr.ConstParam,
            is ThirExpr.ConstBlock,
            is ThirExpr.StaticRef -> {
                val constant = toConstant(expr)
                block and MirRvalue.Use(MirOperand.Constant(constant))
            }
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
                if (expr.count.asLong() == 0L) {
                    buildZeroRepeat(expr.value, scope, sourceInfo(expr.span))
                } else {
                    val (block, elem) = this.toOperand(expr.value, scope, NeedsTemporary.No)
                    block and MirRvalue.Repeat(elem, expr.count)
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
            is ThirExpr.Cast -> {
                this
                    .toOperand(expr.source, scope, NeedsTemporary.No)
                    .map { source ->
                        val fromTy = MirCastTy.from(expr.source.ty)
                        val castTy = MirCastTy.from(expr.ty)
                        MirRvalue.Cast.create(fromTy, castTy, source, expr.ty)
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

    private fun BlockAnd<*>.buildZeroRepeat(
        value: ThirExpr,
        scope: RegionScope?,
        outerSourceInfo: MirSourceInfo
    ): BlockAnd<MirRvalue> {
        var block = block
        when (value) {
            is ThirExpr.ConstBlock,
            is ThirExpr.Literal,
            is ThirExpr.NonHirLiteral,
            is ThirExpr.ZstLiteral,
            is ThirExpr.ConstParam,
            is ThirExpr.StaticRef,
            is ThirExpr.NamedConst -> {
                // Repeating a const does nothing
            }
            else -> {
                // For a non-const, we may need to generate an appropriate `Drop`
                val blockAnd  = this.toOperand(value, scope, NeedsTemporary.No)
                block = blockAnd.block
                val valueOperand = blockAnd.elem
                if (valueOperand is MirOperand.Move) {
                    val toDrop = valueOperand.place
                    val success = basicBlocks.new()
                    block.terminateWithDrop(toDrop, success, null, outerSourceInfo)
                    divergeFrom(block)
                    block = success
                }
                // TODO: record_operands_moved(&[value_operand])
            }
        }
        return block and MirRvalue.Aggregate.Array(value.ty, emptyList())
    }

    // https://github.com/rust-lang/rust/blob/f7b831ac8a897273f78b9f47165cf8e54066ce4b/compiler/rustc_mir_build/src/build/expr/stmt.rs#L11
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
            else -> {
                check(statementScope != null) {
                    "Should not call `statementExpr` on a general expression without a statement scope"
                }
                if (expr is ThirExpr.Block && expr.block.expr != null) {
                    TODO()  // adjustedSpan
                }

                val result = toTemp(expr, statementScope, Mutability.IMMUTABLE)
                result.block.andUnit()
            }
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
            val resultPlace = localDecls
                .newLocal(
                    ty = resultTy,
                    source = MirSourceInfo.outermost(span),
                )
                .intoPlace()
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

            val isZero = localDecls
                .newLocal(
                    ty = TyBool.INSTANCE,
                    source = MirSourceInfo.outermost(span),
                )
                .intoPlace()
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

        val isNegOne = localDecls.newLocal(ty = TyBool.INSTANCE, source = source).intoPlace()
        val isMin = localDecls.newLocal(ty = TyBool.INSTANCE, source = source).intoPlace()
        val overflow = localDecls.newLocal(ty = TyBool.INSTANCE, source = source).intoPlace()

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
        val isMin = localDecls.newLocal(ty = TyBool.INSTANCE, source = source).intoPlace()
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

    private fun localScope(): Scope = scopes.topmost()

    private inline fun <R> inScope(
        scope: Scope?,
        crossinline body: () -> BlockAnd<R>,
    ): BlockAnd<R> {
        val sourceScope = sourceScopes.sourceScope
        if (scope != null) {
            pushScope(scope)
        }
        var res = body()
        if (scope != null) {
            res = res.popScope()
        }
        sourceScopes.sourceScope = sourceScope
        return res
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

    private fun pushScope(regionScope: RegionScope) {
        scopes.push(MirScope(sourceScopes.sourceScope, regionScope))
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

    // https://github.com/rust-lang/rust/blob/f7b831ac8a897273f78b9f47165cf8e54066ce4b/compiler/rustc_mir_build/src/build/expr/as_operand.rs#L100
    private fun BlockAnd<*>.toOperand(
        expr: ThirExpr,
        scope: Scope?,
        needsTemporary: NeedsTemporary,
    ): BlockAnd<MirOperand> {
        if (expr is ThirExpr.Scope) {
            return inScope(expr.regionScope) { toOperand(expr.expr, scope, needsTemporary) }
        }

        return when (MirCategory.of(expr)) {
            MirCategory.Constant -> {
                if (needsTemporary == NeedsTemporary.No || !expr.ty.needsDrop) {
                    val constant = toConstant(expr)
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

    // https://github.com/rust-lang/rust/blob/f7b831ac8a897273f78b9f47165cf8e54066ce4b/compiler/rustc_mir_build/src/build/expr/as_temp.rs#L13
    private fun BlockAnd<*>.toTemp(expr: ThirExpr, tempLifetime: Scope?, mutability: Mutability): BlockAnd<MirLocal> {
        if (expr is ThirExpr.Scope) {
            return inScope(expr.regionScope) { toTemp(expr.expr, tempLifetime, mutability) }
        }
        val localPlace = localDecls
            .newLocal(
                mutability = mutability,
                ty = expr.ty,
                source = MirSourceInfo.outermost(expr.span),
            )
            .intoPlace()
        val source = sourceInfo(expr.span)
        when {
            expr is ThirExpr.Break || expr is ThirExpr.Continue || expr is ThirExpr.Return -> Unit
            expr is ThirExpr.Block && expr.block.expr == null && expr.ty == TyNever -> {
                // TODO: check `targeted_by_break`
            }
            else -> {
                block.pushStorageLive(localPlace.local, source)
                if (tempLifetime != null) {
                    scheduleDrop(tempLifetime, localPlace.local, Drop.Kind.STORAGE)
                }
            }
        }

        if (tempLifetime != null) {
            scheduleDrop(tempLifetime, localPlace.local, Drop.Kind.VALUE)
        }

        return exprIntoPlace(expr, localPlace).map { localPlace.local }
    }

    private fun scheduleDrop(regionScope: Scope, local: MirLocal, dropKind: Drop.Kind) {
        val needsDrop = when (dropKind) {
            Drop.Kind.VALUE -> if (!local.ty.needsDrop) return else true
            Drop.Kind.STORAGE -> false
        }

        for (scope in scopes.scopes(reversed = true)) {
            if (needsDrop) scope.invalidateCaches()
            if (scope.regionScope == regionScope) {
                val regionScopeSource = regionScope.span
                val sourceInfo = MirSourceInfo(regionScopeSource.endPoint, scope.sourceScope)
                scope.addDrop(Drop(local, dropKind, sourceInfo))
                return
            }
        }

        error("Corresponding scope is not found")
    }

    // https://github.com/rust-lang/rust/blob/f7b831ac8a897273f78b9f47165cf8e54066ce4b/compiler/rustc_mir_build/src/build/expr/as_constant.rs#L20
    private fun toConstant(expr: ThirExpr): MirConstant {
        return when (expr) {
            is ThirExpr.Literal -> when {
                expr.literal.integerValue != null && expr.ty.isIntegral -> {
                    val value = expr.literal.integerValue!!.let { if (expr.neg) -it else it }
                    toConstant(value, expr.ty, expr.span)
                }
                expr.literal.booleanValue != null && expr.ty is TyBool -> {
                    toConstant(expr.literal.booleanValue!!, expr.ty, expr.span)
                }
                else -> TODO()
            }
            is ThirExpr.NonHirLiteral -> TODO()
            is ThirExpr.ZstLiteral -> MirConstant.zeroSized(expr.ty, expr.span)
            is ThirExpr.NamedConst -> {
                MirConstant.Unevaluated(expr.def, expr.ty, expr.span)
            }
            is ThirExpr.ConstParam -> TODO()
            is ThirExpr.ConstBlock -> TODO()
            is ThirExpr.StaticRef -> TODO()
            else -> error("expression is not a valid constant: $expr")
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

    fun sourceInfo(span: MirSpan): MirSourceInfo {
        return MirSourceInfo(span, sourceScopes.sourceScope)
    }

    private fun isLet(expr: ThirExpr): Boolean = when (expr) {
        is ThirExpr.Let -> true
        is ThirExpr.Scope -> isLet(expr.expr)
        else -> false
    }

    companion object {
        fun build(file: RsFile): List<MirBody> {
            return buildList { buildToList(file) }
        }

        private fun MutableList<MirBody>.buildToList(element: RsElement) {
            element.children.forEach { child ->
                when (child) {
                    is RsConstant -> add(build(child))
                    is RsFunction -> add(build(child))
                    is RsImplItem -> child.members?.let { buildToList(it) }
                    else -> Unit
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
                mirrorContext = MirrorContext(constant)
            )
            return builder.buildConstant(Thir.from(constant))
        }

        fun build(function: RsFunction): MirBody {
            val returnSpan = function.retType
                ?.let { it.typeReference?.asSpan ?: error("Could not get type reference from function return type") }
                ?: function.block?.asStartSpan
                ?: error("Could not get block of function")

            val body = function.block ?: error("Could not get block of function")

            val thir = Thir.from(function)

            val builder = MirBuilder(
                element = function,
                implLookup = ImplLookup.relativeTo(function),
                checkOverflow = true,
                span = function.asSpan,
                argCount = thir.params.size,
                returnTy = function.normReturnType,
                returnSpan = returnSpan,
                mirrorContext = MirrorContext(function)
            )

            return builder.buildFunction(thir, body)
        }
    }
}
