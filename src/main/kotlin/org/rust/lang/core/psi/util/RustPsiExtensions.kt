package org.rust.lang.core.psi.util

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.psi.RustPat
import org.rust.lang.core.psi.RustTokenElementTypes
import org.rust.lang.core.psi.visitors.RecursiveRustVisitor


//
// Extension points
//

inline fun <reified T : PsiElement> PsiElement.parentOfType(strict: Boolean = true, minStartOffset: Int = -1): T? =
    PsiTreeUtil.getParentOfType(this, T::class.java, strict, minStartOffset)

inline fun <reified T : PsiElement> PsiElement.childOfType(strict: Boolean = true): T? =
    PsiTreeUtil.findChildOfType(this, T::class.java, strict)

fun PsiElement?.getNextNonPhantomSibling(): PsiElement? =
    this?.let {
        val next = it.nextSibling
        val et = next.node.elementType

        if (et in RustTokenElementTypes.PHANTOM_TOKEN_SET)
            return next.getNextNonPhantomSibling()
        else
            return next
    }

val PsiElement.parentRelativeRange: TextRange
    get() = TextRange.from(startOffsetInParent, textLength)

/**
 * Utility checking whether this particular element precedes the other one
 */
fun PsiElement.isBefore(other: PsiElement): Boolean = textOffset < other.textOffset

/**
 * Utility checking whether this particular element succeeds the other one
 */
fun PsiElement.isAfter(other: PsiElement): Boolean = other.textOffset < textOffset


/**
 * Utility checking whether this particular element precedes text-anchor (offset)
 */
fun PsiElement.isBefore(anchor: Int): Boolean = textOffset < anchor


/**
 * Returns module for this PsiElement.
 *
 * If the element is in a library, returns the module which depends on
 * the library.
 */
fun PsiElement.getModule(): Module? {
    val vFile = this.containingFile.originalFile.virtualFile ?: return null
    return ProjectRootManager.getInstance(project).fileIndex
        .getOrderEntriesForFile(vFile)
        .firstOrNull()?.ownerModule
}



//
// TODO(kudinkin): move
//

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
