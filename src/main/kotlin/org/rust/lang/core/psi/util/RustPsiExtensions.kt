package org.rust.lang.core.psi.util

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
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
 * Returns module for this PsiElement.
 *
 * If the element is in a library, returns the module which depends on
 * the library.
 */
val PsiElement.module: Module?
    get() {
        // It's important to look the module for `containingFile` file
        // and not the element itself. Otherwise this will break for
        // elements in libraries.
        return ModuleUtilCore.findModuleForPsiElement(containingFile)
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
