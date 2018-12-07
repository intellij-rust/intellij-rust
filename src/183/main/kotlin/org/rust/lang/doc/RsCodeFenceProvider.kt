/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.doc

import com.intellij.codeEditor.printing.HTMLTextPainter
import com.intellij.psi.PsiElement
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.html.GeneratingProvider
import org.intellij.markdown.html.HtmlGenerator
import java.lang.StringBuilder

fun createCodeFenceProvider(element: PsiElement): GeneratingProvider = RsCodeFenceProvider(element)

// Inspired by org.intellij.markdown.html.CodeFenceGeneratingProvider
private class RsCodeFenceProvider(private val context: PsiElement) : GeneratingProvider {

    override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
        val indentBefore = node.getTextInNode(text).commonPrefixWith(" ".repeat(10)).length

        val codeText = StringBuilder()

        var childrenToConsider = node.children
        if (childrenToConsider.last().type == MarkdownTokenTypes.CODE_FENCE_END) {
            childrenToConsider = childrenToConsider.subList(0, childrenToConsider.size - 1)
        }

        var isContentStarted = false
        var skipNextEOL = false
        var lastChildWasContent = false

        loop@for (child in childrenToConsider) {
            if (isContentStarted && child.type in listOf(MarkdownTokenTypes.CODE_FENCE_CONTENT, MarkdownTokenTypes.EOL)) {
                if (skipNextEOL && child.type == MarkdownTokenTypes.EOL) {
                    skipNextEOL = false
                    continue
                }
                val rawLine = HtmlGenerator.trimIndents(child.getTextInNode(text), indentBefore)
                // `cargo doc` has special rules for code lines which start with `#`:
                //   * `# ` prefix is used to mark lines that should be skipped in rendered documentation.
                //   * `##` prefix is converted to `#`
                // See https://github.com/rust-lang/rust/blob/5182cc1ca65d05f16ee5e1529707ac6f63233ca9/src/librustdoc/html/markdown.rs#L114 for more details
                val codeLine = when {
                    rawLine.startsWith("# ") -> {
                        skipNextEOL = true
                        continue@loop
                    }
                    rawLine.startsWith("##") -> rawLine.removePrefix("#")
                    else -> rawLine
                }

                codeText.append(codeLine)
                lastChildWasContent = child.type == MarkdownTokenTypes.CODE_FENCE_CONTENT
            }
            if (!isContentStarted && child.type == MarkdownTokenTypes.EOL) {
                isContentStarted = true
            }
        }
        if (lastChildWasContent) {
            codeText.appendln()
        }
        val htmlCodeText = HTMLTextPainter.convertCodeFragmentToHTMLFragmentWithInlineStyles(context, codeText.toString())
        visitor.consumeHtml(htmlCodeText)
    }
}
