/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.psi.PsiNameIdentifierOwner

interface RsAbstractable : RsNamedElement, PsiNameIdentifierOwner {
    val isAbstract: Boolean
}
