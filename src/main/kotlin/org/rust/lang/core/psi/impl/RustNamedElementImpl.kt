package org.rust.lang.core.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import org.rust.lang.core.psi.*
import java.util.*

abstract class RustNamedElementImpl(node: ASTNode)   : RustCompositeElementImpl(node)
                                                     , RustNamedElement {

    protected open val nameElement: PsiElement?
        get() = findChildByType(RustTokenElementTypes.IDENTIFIER)

    override fun getName(): String? = nameElement?.text

    /**
     * NOTE: This is orphaned purposefully
     */
    override fun setName(name: String): PsiElement? {
        throw UnsupportedOperationException();
    }

    override fun getNavigationElement(): PsiElement = nameElement ?: this

    override fun getTextOffset(): Int = nameElement?.textOffset ?: super.getTextOffset()

    companion object {
        fun outerDocumentationLinesForElement(psi: RustNamedElement): List<String> {
            // rustdoc appends the contents of each doc comment and doc attribute in order
            // so we have to resolve these attributes that are edge-bound at the top of the
            // children list.
            val childOuterIterator = PsiTreeUtil.childIterator(psi, PsiElement::class.java)
            val lines: List<String> = ArrayList<String>().apply {
                for (c in childOuterIterator) {
                    if (c !is RustOuterAttr && c !is PsiComment && c !is PsiWhiteSpace) {
                        // All these outer elements have been edge bound; if we reach something that isn't one
                        // of these, we have reached the actual parse children of this item.
                        break
                    } else if (c is RustOuterAttr && c.metaItem?.identifier?.textMatches("doc") ?: false) {
                        val s = (c.metaItem?.litExpr)?.stringLiteral?.text?.removeSurrounding("\"")?.trim()
                        if (s != null) add(s)
                    } else if (c is PsiComment && c.tokenType == RustTokenElementTypes.OUTER_DOC_COMMENT) {
                        val s = c.text.substringAfter("///").trim()
                        add(s)
                    }
                }
            }

            return lines
        }

        fun innerDocumentationLinesForElement(psi: RustNamedElement): List<String> {
            // Next, we have to consider inner comments and meta. These, like the outer case, are appended in
            // lexical order, after the outer elements. This only applies to functions and modules.
            val lines: MutableList<String> = ArrayList()

            val childBlock = PsiTreeUtil.findChildOfType(psi, RustBlock::class.java)

            if (childBlock != null) {
                val childInnerIterator = PsiTreeUtil.childIterator(childBlock, PsiElement::class.java)
                childInnerIterator.next() // skip the first open bracket ...
                lines.apply {
                    for (c in childInnerIterator) {
                        // We only consider comments and attributes at the beginning.
                        // Technically, anything else is a syntax error.
                        if (c !is RustInnerAttr && c !is PsiComment && c !is PsiWhiteSpace) {
                            break
                        } else if (c is RustInnerAttr && c.metaItem?.identifier?.textMatches("doc") ?: false) {
                            val s = (c.metaItem?.litExpr)?.stringLiteral?.text?.removeSurrounding("\"")?.trim()
                            if (s != null) add(s)
                        } else if (c is PsiComment && c.tokenType == RustTokenElementTypes.INNER_DOC_COMMENT) {
                            val s = c.text.substringAfter("//!").trim()
                            add(s)
                        }
                    }
                }
            }

            return lines
        }
    }

    override val documentation: String?
        get() = (outerDocumentationLinesForElement(this) +
            innerDocumentationLinesForElement(this)).joinToString("\n")
}
