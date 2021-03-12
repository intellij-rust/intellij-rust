/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.psi.util.PsiTreeUtil
import org.rust.lang.core.psi.RsTypeParameterList

val RsTypeParameterList.genericParameterList: List<RsGenericParameter>
    get() = PsiTreeUtil.getStubChildrenOfTypeAsList(this, RsGenericParameter::class.java)
