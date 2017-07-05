/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.psi.util.PsiTreeUtil
import org.rust.lang.core.psi.RsTypeReference


val RsTypeReference.typeElement: RsTypeElement?
    get() = PsiTreeUtil.getStubChildOfType(this, RsTypeElement::class.java)
