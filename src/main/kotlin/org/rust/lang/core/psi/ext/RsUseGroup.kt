/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.psi.PsiComment
import com.intellij.psi.SyntaxTraverser
import org.rust.lang.core.psi.RsUseGroup
import org.rust.lang.core.psi.RsUseSpeck


val RsUseGroup.parentUseSpeck: RsUseSpeck get() = parent as RsUseSpeck

val RsUseGroup.asTrivial: RsUseSpeck?
    get() {
        val speck = useSpeckList.singleOrNull() ?: return null
        if (speck.alias == null && !speck.isIdentifier) return null
        // Do not change use-groups with comments
        if (SyntaxTraverser.psiTraverser(this).traverse().any { it is PsiComment }) return null
        return speck
    }
