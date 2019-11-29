/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

// BACKOMPAT: 2019.2
@file:Suppress("DEPRECATION")

package org.rust.ide.clones

import com.intellij.lang.LighterAST
import com.intellij.lang.LighterASTNode
import com.intellij.lang.LighterASTTokenNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.TokenSet
import com.jetbrains.clones.core.LighterAstNodeHashCache
import com.jetbrains.clones.core.NodeHash
import com.jetbrains.clones.core.longHash
import com.jetbrains.clones.core.nodeListHash
import com.jetbrains.clones.languagescope.common.DuplicateScopeBase
import org.rust.lang.core.psi.RS_COMMENTS
import org.rust.lang.core.psi.RS_LITERALS
import org.rust.lang.core.psi.RsElementTypes.*
import org.rust.lang.core.psi.tokenSetOf

class RsDuplicateScope : DuplicateScopeBase() {

    /**
     * Presentable language name displayed in settings window
     *
     * Used as a DuplicateScope id in settings serialization
     */
    override val languageName: String = "Rust"

    /**
     * @return true if [file] should be analyzed
     */
    override fun acceptsFile(project: Project, file: VirtualFile): Boolean =
        ProjectFileIndex.getInstance(project).isInSourceContent(file)

    /**
     * Defines how to compute hash of [node]
     * @return true -  based on tokenType
     * @return false - based on text
     */
    override fun isAnonymized(ast: LighterAST, node: LighterASTTokenNode): Boolean {
        return when (getNodeType(ast, node)) {
            NodeType.LITERAL -> indexConfiguration.anonymizeLiterals
            NodeType.IDENTIFIER -> indexConfiguration.anonymizeIdentifiers
            NodeType.CALL,
            NodeType.METHOD_CALL,
            NodeType.FIELD -> indexConfiguration.anonymizeFunctions
            NodeType.OTHER -> false
        }
    }

    /**
     * @param children nodes and hashes of immediate children.
     * @param cache file-scoped cache of nodes hash; values must be saved explicitly.
     * @return normalized hash of supplied [node]. Equal nodes must have equal normalized hash.
     */
    override fun hashOf(
        cache: LighterAstNodeHashCache,
        ast: LighterAST,
        node: LighterASTNode,
        children: List<NodeHash>
    ): Long {
        return when (node.tokenType) {
            BINARY_EXPR -> getNormalizedHashOfBinaryExpr(ast, children)
            else -> super.hashOf(cache, ast, node, children)
        }
    }

    /**
     * @return true if given [node] should be excluded from report. Note that children still could be reported in this case.
     * To exclude children as well [isNoise] method should return true
     */
    override fun isIgnoredAsDuplicate(ast: LighterAST, node: LighterASTNode): Boolean = node.tokenType in IGNORED

    /**
     * @return true if the supplied [node] should be completely excluded from the analysis
     */
    override fun isNoise(ast: LighterAST, node: LighterASTNode): Boolean = node.tokenType in NOISE

    /**
     * @return node weight, non-negative number. Used to compute total weight of code fragment.
     * Total duplicate weight is computed as sum of all node weights.
     * A duplicate is reported if total weight exceeds the user-specified threshold.
     * A code fragment is analyzed if total weight exceeds the [DuplicateIndexConfiguration.windowSize].
     */
    override fun weightOf(ast: LighterAST, node: LighterASTNode): Int {
        return when {
            node.tokenType == BLOCK -> 3
            // Weigh only leaf nodes. Otherwise, the inspection result depends on ast complexity
            // and it will show smaller code fragments
            node is LighterASTTokenNode -> 1
            else -> 0
        }
    }

    private fun getNodeType(ast: LighterAST, node: LighterASTNode): NodeType {
        return when (node.tokenType) {
            in RS_LITERALS -> NodeType.LITERAL
            IDENTIFIER -> {
                val parent = ast.getParent(node) ?: return NodeType.OTHER
                when (parent.tokenType) {
                    // x.method()
                    //   ~~~~~~
                    METHOD_CALL -> NodeType.METHOD_CALL
                    // x.field
                    //   ~~~~~
                    FIELD_LOOKUP -> NodeType.FIELD
                    // let variable = expr;
                    //     ~~~~~~~~
                    PAT_BINDING -> NodeType.IDENTIFIER
                    PATH -> {
                        val grandParent = ast.getParent(parent) ?: return NodeType.OTHER
                        if (grandParent.tokenType != PATH_EXPR) return NodeType.OTHER
                        return if (ast.getParent(grandParent)?.tokenType == CALL_EXPR) NodeType.CALL else NodeType.IDENTIFIER
                    }
                    else -> NodeType.OTHER
                }
            }
            else -> NodeType.OTHER
        }
    }

    private fun getNormalizedHashOfBinaryExpr(ast: LighterAST, children: List<NodeHash>): Long {
        if (children.size != 3) return nodeListHash(children)
        val (left, op, right) = children
        val opType = ast.getChildren(op.node).singleOrNull()?.tokenType

        fun unorderedHash(): Long {
            return listOfNotNull(left, op, right).map { it.hash }.sorted().longHash()
        }
        fun orderedHashOf(left: NodeHash?, right: NodeHash?, sign: String): Long {
            return listOfNotNull(left?.hash, right?.hash, sign.hashCode().toLong()).longHash()
        }
        return when (opType) {
            EQEQ, EXCLEQ, ANDAND, OROR, PLUS, MUL -> unorderedHash()
            GT -> orderedHashOf(left, right, ">")
            LT -> orderedHashOf(right, left, ">")
            GTEQ -> orderedHashOf(left, right, ">=")
            LTEQ -> orderedHashOf(right, left, ">=")
            else -> nodeListHash(children)
        }
    }

    companion object {
        private val NOISE = TokenSet.orSet(RS_COMMENTS, tokenSetOf(
            TokenType.WHITE_SPACE,
            // Ignore trailing commas
            COMMA
        ))
        private val IGNORED = tokenSetOf(
            BLOCK,
            BLOCK_EXPR,
            MATCH_ARM,
            // TODO: probably we don't want to ignore members node because it can be generated by macro
            //  but it's here to avoid false positive annotations for now
            MEMBERS,
            WHERE_CLAUSE,
            VALUE_ARGUMENT_LIST, VALUE_PARAMETER_LIST,
            TYPE_ARGUMENT_LIST, TYPE_PARAMETER_LIST
        )
    }

    private enum class NodeType {
        LITERAL,
        CALL,
        METHOD_CALL,
        FIELD,
        IDENTIFIER,
        OTHER
    }
}
