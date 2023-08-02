/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveResult
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.types.Substitution
import org.rust.lang.core.types.emptySubstitution

/**
 * Used as a resolve result in [org.rust.lang.core.resolve.ref.RsPathReferenceImpl]
 */
data class RsPathResolveResult<T : RsElement>(
    val element: T,
    val resolvedSubst: Substitution = emptySubstitution,
    val isVisible: Boolean,
    val namespaces: Set<Namespace> = emptySet(),
) : ResolveResult {
    override fun getElement(): PsiElement = element

    override fun isValidResult(): Boolean = true
}
