package org.rust.lang.core.psi.util

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.rust.lang.core.lexer.RustTokenElementTypes
import org.rust.lang.core.psi.RustItem
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.RustPat


//
// Extension points
//

fun PsiElement?.getNextNonPhantomSibling(): PsiElement? =
    this?.let {
        val next = it.nextSibling
        val et = next.node.elementType

        if (et == RustTokenElementTypes.BLOCK_COMMENT
        ||  et == RustTokenElementTypes.EOL_COMMENT
        ||  et == RustTokenElementTypes.INNER_DOC_COMMENT
        ||  et == RustTokenElementTypes.OUTER_DOC_COMMENT
        ||  et == com.intellij.psi.TokenType.WHITE_SPACE)
            return next.getNextNonPhantomSibling()
        else
            return next
    }

val PsiElement.parentRelativeRange: TextRange?
    get() = this.parent?.let {
        TextRange(startOffsetInParent, startOffsetInParent + textLength)
    }

fun PsiElement.isBefore(other: PsiElement): Boolean = textOffset < other.textOffset


val RustPat.boundElements: List<RustNamedElement>
    get() {
        val result = arrayListOf<RustNamedElement>()

        accept(object : RecursiveRustVisitor() {
            override fun visitElement(element: PsiElement?) {
                if (element is RustNamedElement)
                    result.add(element)
                super.visitElement(element)
            }
        })
        return result
    }


fun RustItem.isPublic() = vis != null