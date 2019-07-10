/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsElementTypes
import org.rust.lang.core.psi.RsTraitType

val RsTraitType.isImpl: Boolean get() = (greenStub?.isImpl) ?: (impl != null)

val RsTraitType.dyn: PsiElement? get() = node.findChildByType(RsElementTypes.DYN)?.psi
