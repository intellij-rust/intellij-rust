/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.dfa

import com.intellij.util.ThreeState
import org.jetbrains.coverage.gnu.trove.TIntIntHashMap
import org.jetbrains.coverage.gnu.trove.TIntObjectHashMap
import org.rust.ide.utils.skipParenExprDown
import org.rust.ide.utils.skipParenExprUp
import org.rust.lang.core.cfg.CFGNode
import org.rust.lang.core.cfg.CFGNodeData
import org.rust.lang.core.cfg.ControlFlowGraph
import org.rust.lang.core.dfa.value.*
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.type
import java.util.*

private const val LOOP_COUNT = 30
private const val MAX_BRANCH_COUNT = 128
private const val MAX_STATE_COUNT = MAX_BRANCH_COUNT * 100

class DataFlowRunner(val function: RsFunction) {
    private val valueFactory: DfaValueFactory = DfaValueFactory()
    private val instructions = hashMapOf<RsExpr, DfaReachableBranch>()
    private val myBlocks = hashMapOf<RsBlock, Collection<RsPatBinding>>()
    private var lastIdForBranch = 0
    private var countStates = 0
    private lateinit var myInstructionManager: InstructionManager

    private val overflowExpressions = hashSetOf<RsExpr>()
    private val myStates = TIntObjectHashMap<HashSet<DfaMemoryState>>()
    private val myLoopCount = TIntObjectHashMap<TIntIntHashMap>()
    private var exception: DfaException? = null

    //for debug
    private val resultState: DfaMemoryState
        get() {
            val maxIndex = myStates.keys().max() ?: return DfaMemoryState.EMPTY
            return myStates[maxIndex].reduce(DfaMemoryState::unite)
        }

    private val result
        get(): DfaResult {
            val trueSet = hashSetOf<RsExpr>()
            val falseSet = hashSetOf<RsExpr>()
            instructions.asSequence()
                .filter { element -> instructions.all { other -> element.key == other.key || element.key !in other.key } }
                .forEach {
                    val (anchor, reachable) = it
                    if ((anchor as? RsLitExpr)?.boolLiteral == null) {
                        if (reachable.isTrueReachable && !reachable.isFalseReachable) {
                            trueSet += anchor
                        } else if (reachable.isFalseReachable && !reachable.isTrueReachable) {
                            falseSet += anchor
                        }
                    }
                }

            return DfaResult(trueSet, falseSet, overflowExpressions, exception, resultState)
        }

    fun analyze(): DataFlowAnalysisResult = DataFlowAnalysisResult(try {
        val block = function.block
        if (block != null) {
            myInstructionManager = InstructionManager(block)
            val initState = initFunctionParameters()
            val instruction = DfaInstruction.fromNode(0, myInstructionManager.entryNode, initState)
            addInstruction(instruction)
            visitInstructions()
        }
        DfaRunnerResult.OK
    } catch (e: Exception) {
        if (e is DfaException) exception = e
        when (e) {
            is DfaTooComplex -> DfaRunnerResult.TOO_COMPLEX
            is DfaDivisionByZeroException -> DfaRunnerResult.OK
            else -> DfaRunnerResult.NOT_APPLICABLE
        }
    }, result)

    private fun checkLoopCount(instruction: DfaInstruction): Boolean {
        val nodeIndex = instruction.node.index
        val loopCount = myLoopCount[nodeIndex]
        val id = instruction.id
        return if (loopCount == null) {
            val map = TIntIntHashMap()
            map.put(id, 1)
            myLoopCount.put(nodeIndex, map)
            true
        } else {
            val oldValue = loopCount[id]
            when {
                oldValue == 0 -> {
                    loopCount.put(id, 1)
                    true
                }
                oldValue > LOOP_COUNT -> {
                    // case: inner loop
//                    loopCount.adjustValue(id, -LOOP_COUNT)
                    false
                }
                else -> {
                    assert(loopCount.increment(id))
                    true
                }
            }
        }
    }

    private fun createMemoryState(): DfaMemoryState = DfaMemoryState.EMPTY

    private val nextIdForBranch: Int get() = ++lastIdForBranch

    private fun allBindingsInBlock(block: RsBlock): Collection<RsPatBinding> = myBlocks.getOrPut(block) { block.descendantsOfType() }

    private fun initFunctionParameters(): DfaMemoryState {
        var state = createMemoryState()
        function.valueParameterList?.valueParameterList?.forEach {
            val element = it.pat as? RsPatIdent
            val binPat = element?.patBinding
            if (binPat != null) {
                state = state.plus(binPat, valueFactory.createTypeValue(binPat.type))
            }
        }
        return state
    }

    private fun addInstruction(instruction: DfaInstruction) {
        val nodeIndex = instruction.node.index
        val instructionState = instruction.state

        val states = myStates[nodeIndex]
        if (states == null) {
            myStates.put(nodeIndex, hashSetOf(instructionState))
        } else {
            if (instructionState in states) return
            states += instructionState
        }
        if (++countStates > MAX_STATE_COUNT) throw DfaTooComplex("Too complex data flow: too many instruction states processed")
        myInstructionManager.addInstruction(instruction)
    }

    private fun addInstructions(currentInstruction: DfaInstruction, nodes: Sequence<CFGNode>) {
        val state = currentInstruction.state
        nodes.forEachIndexed { index, node ->
            if (index == 0) addInstruction(DfaInstruction.fromPreviousInstruction(currentInstruction, node, state))
            else addInstruction(DfaInstruction.fromPreviousInstruction(currentInstruction, node, state, nextIdForBranch))
        }
    }

    private fun addNextInstruction(currentInstruction: DfaInstruction, state: DfaMemoryState = currentInstruction.state) {
        val nextNode = currentInstruction.node.nextNode ?: return
        addInstruction(DfaInstruction.fromPreviousInstruction(currentInstruction, nextNode, state))
    }

    private fun visitInstructions() {
        while (true) {
            val instruction = myInstructionManager.nextInstruction() ?: return
            val node = instruction.node
            // trace for debug
            println(instruction)
            with(node.data) {
                when {
                    this is CFGNodeData.AST && this.element != null -> visitAstNode(this.element, instruction)
                    this is CFGNodeData.Dummy -> visitDummyNode(node, instruction)
                    else -> addNextInstruction(instruction)
                }
            }
        }
    }

    private fun visitAstNode(element: RsElement, instruction: DfaInstruction): Unit = when (element) {
        //TODO visit stmt (example function args)
        is RsLetDecl -> visitLetDeclNode(element, instruction)
        is RsBinaryExpr -> visitBinExpr(element, instruction)
        is RsBlock -> visitEndBlock(element, instruction)
        is RsRetExpr -> {
        }
        is RsExpr -> tryVisitControlFlow(element, instruction)
        else -> addNextInstruction(instruction)
    }

    private fun visitEndBlock(block: RsBlock, instruction: DfaInstruction) {
        val state = instruction.state.minusAll(allBindingsInBlock(block))
        addNextInstruction(instruction, state)
    }

    private fun visitBinExpr(expr: RsBinaryExpr, instruction: DfaInstruction) {
        when (expr.operatorType) {
            is AssignmentOp -> visitAssignmentBinOp(expr, instruction)
            is BoolOp -> tryVisitControlFlow(expr, instruction)
            else -> addNextInstruction(instruction)
        }
    }

    private fun tryVisitControlFlow(expr: RsExpr, instruction: DfaInstruction) {
        val parent = expr.skipParenExprUp().parent
        when (parent) {
            is RsCondition -> visitCondition(parent, instruction)
            is RsMatchExpr -> visitMatchExpr(expr, parent, instruction)
            is RsTryExpr -> visitTryExpr(expr, parent, instruction)
            is RsForExpr -> visitForExpr(expr, parent, instruction)
            else -> addNextInstruction(instruction)
        }
    }

    private fun visitCondition(condition: RsCondition, instruction: DfaInstruction) {
        val parent = condition.parent
        when (parent) {
            is RsIfExpr -> visitIfExpr(parent, condition, instruction)
            is RsWhileExpr -> visitWhileExpr(parent, condition, instruction)
            else -> addNextInstruction(instruction)
        }
    }

    private fun visitForExpr(expr: RsExpr, forExpr: RsForExpr, instruction: DfaInstruction): Unit = TODO()

    private fun visitTryExpr(expr: RsExpr, tryExpr: RsTryExpr, instruction: DfaInstruction) = addInstructions(instruction, instruction.node.outgoingNodes)

    private fun visitWhileExpr(whileExpr: RsWhileExpr, condition: RsCondition, instruction: DfaInstruction) {
        val check = checkLoopCount(instruction)
        if (check) {
            val (falseBranch, trueBranch) = instruction.node.firstControlFlowSplit
                ?: error("Couldn't find control flow split")
            visitBranches(trueBranch, falseBranch, condition, instruction)
        } else {
            TODO("Add way for loop overflow")
        }
    }

    private fun visitIfExpr(ifExpr: RsIfExpr, condition: RsCondition, instruction: DfaInstruction) {
        val (trueBranch, falseBranch) = instruction.node.firstControlFlowSplit
            ?: error("Couldn't find control flow split")
        visitBranches(trueBranch, falseBranch, condition, instruction)
    }

    private fun visitBranches(trueBranch: CFGNode, falseBranch: CFGNode, condition: RsCondition, instruction: DfaInstruction) {
        val state = instruction.state
        //TODO add if let
        val value = if (condition.let == null) tryEvaluateExpr(condition.expr, state) else DfaCondition.UNSURE

        when (value.threeState) {
            ThreeState.YES -> addInstruction(DfaInstruction.fromPreviousInstruction(instruction, trueBranch, state))
            ThreeState.NO -> addInstruction(DfaInstruction.fromPreviousInstruction(instruction, falseBranch, state))
            ThreeState.UNSURE -> {
                addInstruction(DfaInstruction.fromPreviousInstruction(instruction, trueBranch, state.intersect(value.trueState), nextIdForBranch))
                addInstruction(DfaInstruction.fromPreviousInstruction(instruction, falseBranch, state.intersect(value.falseState)))
            }
        }
    }

    private fun visitMatchExpr(expr: RsExpr, matchExpr: RsMatchExpr, instruction: DfaInstruction) = addInstructions(instruction, instruction.node.outgoingNodes)

    private fun tryEvaluateExpr(expr: RsExpr?, state: DfaMemoryState): DfaCondition {
        val expr = expr?.skipParenExprDown() ?: return DfaCondition.UNSURE
        return when (expr) {
            is RsLitExpr -> tryEvaluateLitExpr(expr)
            is RsUnaryExpr -> tryEvaluateUnaryExpr(expr, state)
            is RsBinaryExpr -> tryEvaluateBinExpr(expr, state)
            is RsPathExpr -> tryEvaluatePathExpr(expr, state)
            else -> DfaCondition.UNSURE
        }.addBinOpIfSure(expr)
    }

    private fun DfaCondition.addBinOpIfSure(expr: RsExpr): DfaCondition {
        if (sure) instructions.merge(expr, DfaReachableBranch.fromThreeState(threeState), DfaReachableBranch::merge)
        return this
    }

    private fun tryEvaluatePathExpr(expr: RsPathExpr, state: DfaMemoryState): DfaCondition {
        val constValue = valueFromPathExpr(expr, state) as? DfaConstValue ?: return DfaCondition.UNSURE
        return DfaCondition(fromBool(constValue.value as? Boolean))
    }

    private fun tryEvaluateLitExpr(expr: RsLitExpr): DfaCondition = DfaCondition(fromBool((expr.kind as? RsLiteralKind.Boolean)?.value))

    private fun tryEvaluateUnaryExpr(expr: RsUnaryExpr, state: DfaMemoryState): DfaCondition {
        if (expr.excl == null) return DfaCondition.UNSURE
        val result = tryEvaluateExpr(expr.expr, state)
        return DfaCondition(result.threeState.not, trueState = result.falseState, falseState = result.trueState)
    }

    private fun tryEvaluateBinExpr(expr: RsBinaryExpr, state: DfaMemoryState): DfaCondition {
        val op = expr.operatorType
        return when (op) {
            is LogicOp -> tryEvaluateBinExprWithLogicOp(op, expr, state)
            // TODO add separately EqualityOp
            is BoolOp -> tryEvaluateBinExprWithRange(op, expr, state)
            else -> DfaCondition.UNSURE
        }
    }

    private fun tryEvaluateBinExprWithLogicOp(op: LogicOp, expr: RsBinaryExpr, state: DfaMemoryState): DfaCondition {
        val left = expr.left.skipParenExprDown()
        val right = expr.right?.skipParenExprDown() ?: return DfaCondition.UNSURE
        val leftResult = tryEvaluateExpr(left, state)
        return when (op) {
            LogicOp.OR -> if (leftResult.threeState == ThreeState.YES) leftResult else leftResult.or(tryEvaluateExpr(right, state))
            LogicOp.AND -> if (leftResult.threeState == ThreeState.NO) leftResult else leftResult.and(tryEvaluateExpr(right, state.intersect(leftResult.trueState)))
        }
    }

    private fun tryEvaluateConst(op: BoolOp, leftExpr: RsExpr?, leftValue: DfaValue, rightExpr: RsExpr?, rightValue: DfaValue): DfaCondition? = when {
        leftExpr == null || rightExpr == null -> DfaCondition.UNSURE
        op !is EqualityOp -> null
        leftValue is DfaUnknownValue && rightValue is DfaUnknownValue -> if (equals(leftExpr, rightExpr)) DfaCondition(ThreeState.fromBoolean(op is EqualityOp.EQ)) else DfaCondition.UNSURE
        leftValue is DfaConstValue && rightValue is DfaConstValue -> DfaCondition(ThreeState.fromBoolean(if (op is EqualityOp.EQ) leftValue == rightValue else leftValue != rightValue))
        leftValue is DfaConstValue && rightValue is DfaUnknownValue -> valueFromConstantAndUnknown(leftValue, op, rightExpr)
        leftValue is DfaUnknownValue && rightValue is DfaConstValue -> valueFromConstantAndUnknown(rightValue, op, leftExpr)
        else -> null
    }

    private fun equals(leftExpr: RsExpr?, rightExpr: RsExpr?): Boolean {
        val leftVariable = leftExpr?.toVariable() ?: return false
        val rightVariable = rightExpr?.toVariable() ?: return false
        return leftVariable == rightVariable
    }

    private fun valueFromConstantAndUnknown(constValue: DfaConstValue, op: EqualityOp, otherExpr: RsExpr): DfaCondition? {
        val boolValue = constValue.value as? Boolean ?: return null
        val otherVariable = otherExpr.toVariable()

        val constValue = valueFactory.createBoolValue(boolValue)
        val resultValue = constValue.let { if (op is EqualityOp.EQ) it else it.negated }
        val trueState = createMemoryState().uniteValue(otherVariable, resultValue)
        val falseState = createMemoryState().uniteValue(otherVariable, resultValue.negated)
        return DfaCondition(ThreeState.UNSURE, trueState, falseState)
    }

    private fun DfaMemoryState.uniteRangeIfNotEmpty(variable: Variable?, range: LongRangeSet): DfaMemoryState = if (!range.isEmpty) uniteValue(variable, valueFactory.createRange(range)) else this

    private fun tryEvaluateBinExprWithRange(op: BoolOp, expr: RsBinaryExpr, state: DfaMemoryState): DfaCondition {
        val leftValue = valueFromExpr(expr.left, state)
        val rightValue = valueFromExpr(expr.right, state)

        val value = tryEvaluateConst(op, expr.left, leftValue, expr.right, rightValue)
        if (value != null) return value
//      TODO check type?
        if (leftValue.type != rightValue.type) return DfaCondition.UNSURE
        val leftRange = LongRangeSet.fromDfaValue(leftValue) ?: return DfaCondition.UNSURE
        val rightRange = LongRangeSet.fromDfaValue(rightValue) ?: return DfaCondition.UNSURE

        val leftVariable = expr.left.toVariable()
        val rightVariable = expr.right?.toVariable()
        val (leftTrueResult, rightTrueResult) = leftRange.compare(op, rightRange)
        val trueState = createMemoryState()
            .uniteRangeIfNotEmpty(leftVariable, leftTrueResult)
            .uniteRangeIfNotEmpty(rightVariable, rightTrueResult)

        val (leftFalseResult, rightFalseResult) = leftRange.compare(op.not, rightRange)
        val falseState = createMemoryState()
            .uniteRangeIfNotEmpty(leftVariable, leftFalseResult)
            .uniteRangeIfNotEmpty(rightVariable, rightFalseResult)

        return when {
            leftTrueResult.isEmpty && rightTrueResult.isEmpty -> DfaCondition(ThreeState.NO, trueState = trueState, falseState = falseState)
            leftRange in leftTrueResult && rightRange in rightTrueResult -> DfaCondition(ThreeState.YES, trueState = trueState, falseState = falseState)
            else -> DfaCondition(ThreeState.UNSURE, trueState = trueState, falseState = falseState)
        }
    }

    private fun visitLetDeclNode(element: RsLetDecl, instruction: DfaInstruction) {
        val pat = element.pat
        val state = when (pat) {
            is RsPatIdent -> {
                val state = instruction.state
                val bin = pat.patBinding
                val expr = element.expr
                // TODO add type check
                val value = if (expr != null) valueFromExpr(expr, state) else valueFactory.createTypeValue(bin.type)
                state.plus(bin, value)
            }
            is RsPatTup -> {
                var state = instruction.state
                val values = valuesFromTuple(element.expr, state)
                pat.patList.forEachIndexed { index, it ->
                    state = state.plus((it as? RsPatIdent)?.patBinding, values.getOrElse(index) { DfaUnknownValue })
                }
                state
            }
            else -> instruction.state
        }
        addNextInstruction(instruction, state)
    }

    private fun valuesFromTuple(element: RsExpr?, state: DfaMemoryState): List<DfaValue> {
        val tuple = element as? RsTupleExpr ?: return emptyList()
        return tuple.exprList.map { valueFromExpr(it, state) }
    }

    private fun DfaValue.addExprIfOverflow(expr: RsExpr): DfaValue {
        if (this is DfaFactMapValue && this[DfaFactType.RANGE]?.isOverflow == true) overflowExpressions += expr
        return this
    }

    private fun DfaValue.throwErrorIfDivideByZero(expr: RsExpr): DfaValue {
        if (this is DfaFactMapValue && this[DfaFactType.RANGE]?.hasDivisionByZero == true) throw DfaDivisionByZeroException(expr)
        return this
    }

    private fun valueFromExpr(expr: RsExpr?, state: DfaMemoryState): DfaValue {
        val expr = expr?.skipParenExprDown() ?: return DfaUnknownValue
        return when (expr) {
            is RsPathExpr -> valueFromPathExpr(expr, state)
            is RsLitExpr -> valueFactory.createLiteralValue(expr)
            is RsBinaryExpr -> valueFromBinExpr(expr, state)
            is RsUnaryExpr -> valueFromUnaryExpr(expr, state)
            else -> valueFactory.createTypeValue(expr.type)
        }.throwErrorIfDivideByZero(expr).addExprIfOverflow(expr)
    }

    private fun valueFromUnaryExpr(expr: RsUnaryExpr, state: DfaMemoryState): DfaValue =
        when {
            expr.excl != null -> valueFromExpr(expr.expr, state).negated
            expr.minus != null -> {
                // case: 'let a = - 128i8'
                val child = expr.expr?.skipParenExprDown()
                if (child is RsLitExpr) valueFactory.createIntegerValue("-${integerFromLitExpr(child)}", child.type)
                else valueFromExpr(child, state).minus
            }
            else -> DfaUnknownValue
        }

    private fun valueFromPathExpr(expr: RsPathExpr, state: DfaMemoryState): DfaValue {
        val variable = expr.toVariable() ?: return DfaUnknownValue
        return state.getOrUnknown(variable)
    }

    private fun valueFromBinExpr(expr: RsBinaryExpr, state: DfaMemoryState): DfaValue {
        val op = expr.operatorType
        return when (op) {
            is AssignmentOp -> DfaUnknownValue
            else -> valueFromBinOp(op, expr, state)
        }
    }

    private fun valueFromBinOp(op: BinaryOperator, expr: RsBinaryExpr, state: DfaMemoryState): DfaValue {
        val leftValue = valueFromExpr(expr.left, state)
        val rightValue = valueFromExpr(expr.right, state)
        val value = valueFromConstValue(op, expr.left, leftValue, expr.right, rightValue)
        if (value != null) {
            return value
        }
//      TODO check type?
        if (op !is OverloadableBinaryOperator || leftValue.type != rightValue.type) return DfaUnknownValue
        val leftRange = LongRangeSet.fromDfaValue(leftValue) ?: return DfaUnknownValue
        val rightRange = LongRangeSet.fromDfaValue(rightValue) ?: return DfaUnknownValue
        return valueFactory.createRange(leftRange.binOpFromToken(op, rightRange))
    }

    private fun evaluateBoolExpr(op: BoolOp, left: Boolean, right: Boolean): Boolean = when (op) {
        is EqualityOp -> if (op is EqualityOp.EQ) left == right else left != right
        is LogicOp -> if (op is LogicOp.OR) left || right else left && right
        else -> error("Illegal operation `$op` for boolean")
    }

    private fun valueFromConstValue(op: BinaryOperator, leftExpr: RsExpr?, leftValue: DfaValue, rightExpr: RsExpr?, rightValue: DfaValue): DfaValue? = when {
        leftExpr == null || rightExpr == null -> DfaUnknownValue
        op !is BoolOp -> null
        leftValue is DfaConstValue && rightValue is DfaConstValue -> valueFactory.createBoolValue(evaluateBoolExpr(op, leftValue.value as Boolean, rightValue.value as Boolean))
        leftValue is DfaUnknownValue && rightValue is DfaUnknownValue -> if (equals(leftExpr, rightExpr)) valueFactory.createBoolValue(op is EqualityOp.EQ) else DfaUnknownValue
        leftValue is DfaUnknownValue || rightValue is DfaUnknownValue || leftValue is DfaConstValue || rightValue is DfaConstValue -> DfaUnknownValue
        else -> null
    }

    private fun visitAssignmentBinOp(expr: RsBinaryExpr, instruction: DfaInstruction) {
        val variable = expr.left.toVariable()
        val state = if (variable != null) {
            val state = instruction.state
            val value = valueFromExpr(expr.right, state)
            state.plus(variable, value)
        } else {
            instruction.state
        }
        addNextInstruction(instruction, state)
    }

    private fun visitDummyNode(node: CFGNode, instruction: DfaInstruction) {
        val nextNode = myInstructionManager.getNode(node.index + 1)
        val expr = nextNode.data.element
        when (expr) {
            is RsLoopExpr -> visitLoopExpr(expr, node, nextNode, instruction)
            else -> addNextInstruction(instruction)
        }
    }

    private fun visitLoopExpr(expr: RsLoopExpr, dummyNode: CFGNode, loopNode: CFGNode, instruction: DfaInstruction): Unit = TODO()
}

private class InstructionManager(block: RsBlock) {
    private val cfg = ControlFlowGraph.buildFor(block)
    private val queue = PriorityQueue<DfaInstruction>(2, compareByDescending { it.priority })
    val entryNode: CFGNode = cfg.entry

    fun nextInstruction(): DfaInstruction? = queue.poll()
    fun maxPriority(): Int? = queue.peek()?.priority
    fun getNode(index: Int): CFGNode = cfg.graph.getNode(index)
    fun addInstruction(instruction: DfaInstruction): Boolean = if (instruction.id >= MAX_BRANCH_COUNT) throw DfaTooComplex("Too complex data flow: too many branches processed")
    else queue.add(instruction)
}

val CFGNode.outgoingNodes: Sequence<CFGNode> get() = generateSequence(this.firstOutEdge) { it.nextSourceEdge }.map { it.target }
val CFGNode.nextNode: CFGNode? get() = this.outgoingNodes.lastOrNull()
val CFGNode.firstControlFlowSplit: Pair<CFGNode, CFGNode>?
    get() {
        var out = this.outgoingNodes
        while (out.count() != 2) {
            out = out.firstOrNull()?.outgoingNodes ?: emptySequence()
            if (out.none()) return null
        }
        return out.elementAt(1) to out.elementAt(0)
    }
val CFGNode.hasSingleOut: Boolean get() = this.outgoingNodes.singleOrNull() != null
val CFGNode.firstOutNode: CFGNode? get() = this.firstOutEdge?.target
val CFGNode.firstInNode: CFGNode? get() = this.firstInEdge?.source

private fun RsExpr.toVariable(): Variable? {
    val expr = skipParenExprDown()
    return if (expr is RsPathExpr) expr.path.reference.resolve() as? Variable else null
}

private operator fun RsElement.contains(other: RsElement): Boolean = other.textRange in this.textRange
