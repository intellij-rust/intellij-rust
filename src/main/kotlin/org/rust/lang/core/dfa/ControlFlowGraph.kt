/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.dfa

import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.regions.ScopeTree
import org.rust.lang.core.types.ty.TyNever
import org.rust.lang.core.types.type
import org.rust.lang.utils.Node
import org.rust.lang.utils.PresentableGraph
import org.rust.lang.utils.PresentableNodeData
import org.rust.stdext.buildSet

sealed class CFGNodeData(val element: RsElement? = null) : PresentableNodeData {

    /** Any execution unit (e.g. expression, statement) */
    class AST(element: RsElement) : CFGNodeData(element)

    /** Execution start */
    object Entry : CFGNodeData()

    /** Normal execution end (e.g. after `main` function block) */
    object Exit : CFGNodeData()

    /** Any real (e.g. `panic!()` call) or imaginary (e.g. after infinite loop) execution end */
    object Termination : CFGNodeData()

    /** Supplementary node to build complex control flow (e.g. loops and pattern matching) */
    object Dummy : CFGNodeData()

    /** Start of any unreachable code (e.g. after `return`) */
    object Unreachable : CFGNodeData()


    override val text: String
        get() = when (this) {
            is AST -> element!!.cfgText().trim()
            Entry -> "Entry"
            Exit -> "Exit"
            Termination -> "Termination"
            Dummy -> "Dummy"
            Unreachable -> "Unreachable"
        }

    private fun RsElement.cfgText(): String =
        when (this) {
            is RsBlock, is RsBlockExpr -> "BLOCK"
            is RsIfExpr -> "IF"
            is RsWhileExpr -> "WHILE"
            is RsLoopExpr -> "LOOP"
            is RsForExpr -> "FOR"
            is RsMatchExpr -> "MATCH"
            is RsLambdaExpr -> "CLOSURE"
            is RsExprStmt -> outerAttrList.joinToString(postfix = " ") { it.text } + expr.cfgText() + ";"
            else -> this.text
        }
}

class CFGEdgeData(val exitingScopes: List<RsElement>)

typealias CFGNode = Node<CFGNodeData, CFGEdgeData>
typealias CFGGraph = PresentableGraph<CFGNodeData, CFGEdgeData>

/**
 * The control-flow graph we use is built on the top of the PSI tree.
 * We do not operate with basic blocks, and therefore each tiny execution unit is represented in the graph as a separate node.
 *
 * For illustration, consider the following program:
 * ```
 * fn main() {
 *   let x = 42;
 * }
 * ```
 * The corresponding control-flow graph we build will consist of the following edges:
 * ```
 *   `Entry` -> `42`
 *   `42` -> `x`
 *   `x` -> `x`
 *   `x` -> `let x = 42`
 *   `let x = 42;` -> `BLOCK`
 *   `BLOCK` -> `Exit`
 *   `Exit` -> `Termination`
 * ```
 *
 * You may see that the pattern bindings are duplicated here (`x` -> `x` edge).
 * This occurs because `x` nodes correspond to both `RsPatIdent` (parent) and `RsPatBinding` (child).
 *
 * Please refer to [org.rust.lang.core.dfa.RsControlFlowGraphTest] for more examples.
 */
class ControlFlowGraph private constructor(
    val owner: RsElement,
    val graph: CFGGraph,
    val body: RsBlock,
    val regionScopeTree: ScopeTree,
    val entry: CFGNode,
    val exit: CFGNode,
    val unreachableElements: Set<RsElement>
) {
    companion object {
        fun buildFor(body: RsBlock, regionScopeTree: ScopeTree): ControlFlowGraph {
            val owner = body.parent as RsElement
            val graph = PresentableGraph<CFGNodeData, CFGEdgeData>()
            val entry = graph.addNode(CFGNodeData.Entry)
            val fnExit = graph.addNode(CFGNodeData.Exit)
            val termination = graph.addNode(CFGNodeData.Termination)

            val cfgBuilder = CFGBuilder(regionScopeTree, graph, entry, fnExit, termination)
            val bodyExit = cfgBuilder.process(body, entry)
            cfgBuilder.addContainedEdge(bodyExit, fnExit)
            cfgBuilder.addContainedEdge(fnExit, termination)

            val unreachableElements = collectUnreachableElements(graph, entry)

            return ControlFlowGraph(owner, graph, body, regionScopeTree, entry, fnExit, unreachableElements)
        }

        /**
         * Collects unreachable elements, i.e. elements cannot be reached by execution of [body].
         *
         * Only collects [RsExprStmt]s, [RsLetDecl]s and tail expressions, ignoring conditionally disabled.
         */
        private fun collectUnreachableElements(graph: CFGGraph, entry: CFGNode): Set<RsElement> {
            /**
             * In terms of our control-flow graph, a [RsElement]'s node is not reachable if it cannot be fully executed,
             * since [CFGBuilder] processes elements in post-order.
             * But partially executed elements should not be treated as unreachable because the execution
             * actually reaches them; only some parts of such elements should be treated as unreachable instead.
             *
             * So, first of all, collect all the unexecuted [RsElement]s (including partially executed)
             */
            val unexecutedElements = buildSet {
                val fullyExecutedNodeIndices = graph.depthFirstTraversal(entry).map { it.index }.toSet()
                graph.forEachNode { node ->
                    if (node.index !in fullyExecutedNodeIndices) {
                        val element = node.data.element ?: return@forEachNode
                        add(element)
                    }
                }
            }
            val unexecutedStmts = unexecutedElements
                .filterIsInstance<RsStmt>()
            val unexecutedTailExprs = unexecutedElements
                .filterIsInstance<RsBlock>()
                .mapNotNull { block ->
                    block.expandedTailExpr?.takeIf { expr ->
                        when (expr) {
                            is RsMacroExpr -> expr.macroCall in unexecutedElements
                            else -> expr in unexecutedElements
                        }
                    }
                }

            /**
             * If [unexecuted] produces termination by itself
             * (which means its inner expression type is [TyNever], see the corresponding checks below),
             * it should be treated as unreachable only if the execution does not reach the beginning of [unexecuted].
             * The latter means that previous statement is not fully executed.
             * This is needed in order not to treat the first, basically reachable, `return` as unreachable
             */
            fun isUnreachable(unexecuted: RsElement, innerExpr: RsExpr?): Boolean {
                // Ignore conditionally disabled elements
                if (!unexecuted.existsAfterExpansion) {
                    return false
                }
                if (innerExpr != null && !innerExpr.existsAfterExpansion) {
                    return false
                }

                // Unexecuted element is definitely unreachable if its inner expression does not produce termination
                if (innerExpr != null && innerExpr.type !is TyNever) {
                    return true
                }

                // Otherwise, unexecuted element's child produces termination and therefore
                // we should check if it is really unreachable or just not fully executed
                val parentBlock = unexecuted.ancestorStrict<RsBlock>() ?: return false
                val blockStmts = parentBlock.stmtList.takeIf { it.isNotEmpty() } ?: return false
                val blockTailExpr = parentBlock.expandedTailExpr
                val index = blockStmts.indexOf(unexecuted)
                return when {
                    index >= 1 -> {
                        // `{ ... <unreachable>; <element>; ... }`
                        blockStmts[index - 1] in unexecutedElements
                    }
                    unexecuted == blockTailExpr -> {
                        // `{ ... <unreachable>; <element> }`
                        blockStmts.last() in unexecutedElements
                    }
                    else -> false
                }
            }

            val unreachableElements = mutableSetOf<RsElement>()

            for (stmt in unexecutedStmts) {
                when {
                    stmt is RsExprStmt && isUnreachable(stmt, stmt.expr) -> {
                        unreachableElements.add(stmt)
                    }
                    stmt is RsLetDecl && isUnreachable(stmt, stmt.expr) -> {
                        unreachableElements.add(stmt)
                    }
                }
            }
            for (tailExpr in unexecutedTailExprs) {
                if (isUnreachable(tailExpr, tailExpr)) {
                    unreachableElements.add(tailExpr)
                }
            }

            return unreachableElements
        }
    }

    fun buildLocalIndex(): HashMap<RsElement, MutableList<CFGNode>> {
        val table = hashMapOf<RsElement, MutableList<CFGNode>>()
        val func = body.parent

        if (func is RsFunction) {
            val formals = object : RsVisitor() {
                override fun visitPatBinding(binding: RsPatBinding) {
                    table.getOrPut(binding, ::mutableListOf).add(entry)
                }

                override fun visitPatField(field: RsPatField) {
                    field.acceptChildren(this)
                }

                override fun visitPat(pat: RsPat) {
                    pat.acceptChildren(this)
                }
            }

            func.valueParameters.mapNotNull { it.pat }.forEach { formals.visitPat(it) }
        }

        graph.forEachNode { node ->
            val element = node.data.element
            if (element != null) table.getOrPut(element, ::mutableListOf).add(node)
        }

        return table
    }
}
