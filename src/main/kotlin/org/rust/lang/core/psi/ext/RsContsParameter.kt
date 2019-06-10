/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.search.SearchScope
import com.intellij.psi.stubs.IStubElementType
import org.rust.lang.core.psi.RsConstParameter
import org.rust.lang.core.psi.RsPsiImplUtil
import org.rust.lang.core.stubs.RsConstParameterStub

abstract class RsConstParameterImplMixin : RsStubbedNamedElementImpl<RsConstParameterStub>, RsConstParameter {
    constructor(node: ASTNode) : super(node)
    constructor(stub: RsConstParameterStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getUseScope(): SearchScope = RsPsiImplUtil.getParameterUseScope(this) ?: super.getUseScope()
}
