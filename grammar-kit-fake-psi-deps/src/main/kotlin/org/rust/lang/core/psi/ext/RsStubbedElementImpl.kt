/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.extapi.psi.StubBasedPsiElementBase
import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement

/**
 * Beware, **FAKE**!
 * This is not a real class used across the plugin. This is a fake class used as an input for Grammar-Kit.
 * Please, read `README.md` of this module for more info.
 */
class RsStubbedElementImpl<StubT : StubElement<*>> : StubBasedPsiElementBase<StubT> {
    constructor(node: ASTNode) : super(node)

    constructor(stub: StubT, nodeType: IStubElementType<*, *>) : super(stub, nodeType)
}
