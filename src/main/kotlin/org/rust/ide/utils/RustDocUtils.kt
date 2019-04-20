/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.utils

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes.EMPH
import org.intellij.markdown.MarkdownElementTypes.STRONG
import org.intellij.markdown.MarkdownTokenTypes.Companion.EOL
import org.intellij.markdown.MarkdownTokenTypes.Companion.TEXT
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.CompositeASTNode
import org.intellij.markdown.ast.LeafASTNode
import org.intellij.markdown.parser.MarkdownParser
import org.rust.lang.core.psi.ext.startOffset
import org.rust.lang.doc.RustDocMarkdownFlavourDescriptor
import org.rust.lang.doc.psi.RsDocKind

fun getAllRegularEmphasis(docComment: PsiComment): List<TextRange> {
    return getMarkdownElementRangesOfType(docComment, EMPH)
}

fun getAllStrongEmphasis(docComment: PsiComment): List<TextRange> {
    return getMarkdownElementRangesOfType(docComment, STRONG)
}

private fun getMarkdownElementRangesOfType(docComment: PsiComment, type: IElementType): List<TextRange> {
    val root = buildMarkdownASTTree(docComment)
    return getCompositeNodesOfType(root, type)
        .flatMap { getTextNodes(it) }
        .map { TextRange(it.startOffset, it.endOffset).shiftRight(docComment.startOffset) }
}

private fun buildMarkdownASTTree(docComment: PsiComment): ASTNode {
    val text = docComment.text /*RsDocKind.of(docComment.tokenType)
        .removeDecoration(docComment.text.splitToSequence("\r\n", "\r", "\n"))
        .joinToString("\n")*/
    val flavour = RustDocMarkdownFlavourDescriptor(docComment)
    return MarkdownParser(flavour).buildMarkdownTreeFromString(text)
}

// fixme
private fun getCompositeNodesOfType(root: ASTNode, type: IElementType): List<ASTNode> {
    val result = mutableListOf<ASTNode>()
    root.children.forEach {
        if (it is CompositeASTNode) {
            if (it.type == type) result += it
            result.addAll(getCompositeNodesOfType(it, type))
        }
    }
    return result
}

// fixme
private fun getTextNodes(root: ASTNode): List<ASTNode> {
    val result = mutableListOf<ASTNode>()
    root.children.forEachIndexed { index, it ->
        (
            if (it is LeafASTNode) {
                if (it.type == TEXT && root.children[index - 1].type != EOL) result += it
            } else {
                result.addAll(getTextNodes(it))
            }
            )
    }
    return result
}
