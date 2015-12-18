package org.rust.lang.core.psi.util

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.lang.core.lexer.RustTokenElementTypes
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.visitors.RecursiveRustVisitor
import org.rust.lang.core.resolve.indexes.RustModulePath


//
// Extension points
//

inline fun <reified T : PsiElement> PsiElement.parentOfType(strict: Boolean = true): T? {
    var current = if (strict) parent else this
    while (current != null) {
        when (current) {
            is T -> return current
            else -> current = current.parent
        }
    }
    return null
}


fun PsiElement?.getNextNonPhantomSibling(): PsiElement? =
    this?.let {
        val next = it.nextSibling
        val et = next.node.elementType

        if (et in RustTokenElementTypes.PHANTOM_TOKEN_SET)
            return next.getNextNonPhantomSibling()
        else
            return next
    }

val PsiElement.parentRelativeRange: TextRange?
    get() = this.parent?.let {
        TextRange(startOffsetInParent, startOffsetInParent + textLength)
    }

/**
 * Utility checking whether this particular element precedes the other one
 */
fun PsiElement.isBefore(other: PsiElement): Boolean = textOffset < other.textOffset

/**
 * Utility checking whether this particular element precedes text-anchor (offset)
 */
fun PsiElement.isBefore(anchor: Int): Boolean = textOffset < anchor


fun PsiElement.getCrate(): Module =
    ModuleUtilCore.findModuleForPsiElement(this)!!


val PsiFile.modulePath: RustModulePath?
    get() = RustModulePath.devise(this)


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


fun RustItem.isPublic() = vis != null


val RustPatBinding.isMut: Boolean
    get()  = bindingMode?.mut != null

val RustCompositeElement.containingMod: RustModItem?
    get() = parentOfType<RustModItem>()
