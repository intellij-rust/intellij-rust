/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.clones

import com.intellij.lang.LighterAST
import com.intellij.lang.LighterASTNode
import com.intellij.lang.LighterASTTokenNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.TokenSet
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
     * @return true if given [node] should be excluded from report. Note that children still could be reported in this case.
     * To exclude children as well [isNoise] method should return true
     */
    override fun isIgnoredAsDuplicate(ast: LighterAST, node: LighterASTNode): Boolean {
        // TODO: provide nodes that we want to exclude from reports
        return false
    }

    /**
     * @return true if the supplied [node] should be completely excluded from the analysis
     */
    override fun isNoise(ast: LighterAST, node: LighterASTNode): Boolean {
        return node.tokenType in IGNORED
    }

    /**
     * @return node weight, non-negative number. Used to compute total weight of code fragment.
     * Total duplicate weight is computed as sum of all node weights.
     * A duplicate is reported if total weight exceeds the user-specified threshold.
     * A code fragment is analyzed if total weight exceeds the [DuplicateIndexConfiguration.windowSize].
     */
    override fun weightOf(ast: LighterAST, node: LighterASTNode): Int {
        // TODO: come up with more precise weights
        return 1
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

    companion object {
        private val IGNORED = TokenSet.orSet(RS_COMMENTS, tokenSetOf(TokenType.WHITE_SPACE))
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
