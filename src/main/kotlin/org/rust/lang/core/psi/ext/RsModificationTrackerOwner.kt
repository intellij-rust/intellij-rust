/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.PsiElement
import org.rust.lang.core.macros.RsExpandedElement
import org.rust.lang.core.psi.RsMacro
import org.rust.lang.core.psi.RsMacroCall
import org.rust.lang.core.psi.RsPsiManager
import kotlin.reflect.KClass

/**
 * A PSI element that holds modification tracker for some reason.
 * This is mostly used to invalidate cached type inference results.
 */
interface RsModificationTrackerOwner : RsElement {
    val modificationTracker: ModificationTracker

    /**
     * Increments local modification counter if needed.
     *
     * If and only if false returned,
     * [RsPsiManager.rustStructureModificationTracker]
     * will be incremented.
     *
     * @param element the changed psi element
     * @see org.rust.lang.core.psi.RsPsiManagerImpl.updateModificationCount
     */
    fun incModificationCount(element: PsiElement): Boolean
}

fun PsiElement.findModificationTrackerOwner(strict: Boolean): RsModificationTrackerOwner? {
    return findContextOfTypeWithoutIndexAccess(
        strict,
        RsItemElement::class,
        RsMacroCall::class,
        RsMacro::class
    ) as? RsModificationTrackerOwner
}

// We have to process contexts without index access because accessing indices during PSI event processing is slow.
private val PsiElement.contextWithoutIndexAccess: PsiElement?
    get() = if (this is RsExpandedElement) {
        RsExpandedElement.getContextImpl(this, isIndexAccessForbidden = true)
    } else {
        stubParent
    }

@Suppress("UNCHECKED_CAST")
private fun <T : PsiElement> PsiElement.findContextOfTypeWithoutIndexAccess(strict: Boolean, vararg classes: KClass<out T>): T? {
    var element = if (strict) contextWithoutIndexAccess else this

    while (element != null && !classes.any { it.isInstance(element) }) {
        element = element.contextWithoutIndexAccess
    }

    return element as T?
}

