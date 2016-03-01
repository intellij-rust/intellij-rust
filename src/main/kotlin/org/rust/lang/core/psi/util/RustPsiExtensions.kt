package org.rust.lang.core.psi.util

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.rust.lang.core.psi.RustTokenElementTypes
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.visitors.RecursiveRustVisitor
import org.rust.lang.core.resolve.indexes.RustModulePath


//
// Extension points
//

inline fun <reified T : PsiElement> PsiElement.parentOfType(strict: Boolean = true, minStartOffset: Int = -1): T? =
    PsiTreeUtil.getParentOfType(this, T::class.java, strict, minStartOffset)

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


fun PsiElement.getModule(): Module? =
    ModuleUtilCore.findModuleForPsiElement(this)



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
