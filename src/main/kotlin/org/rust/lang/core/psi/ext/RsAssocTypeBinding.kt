/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import org.rust.lang.core.psi.RsAssocTypeBinding
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.resolve.ref.RsAssocTypeBindingReferenceImpl
import org.rust.lang.core.resolve.ref.RsReference
import org.rust.lang.core.stubs.RsAssocTypeBindingStub

// Current grammar allows to write assoc type bindings in method calls, e.g.
// `a.foo::<Item = i32>()`, so it's nullable
val RsAssocTypeBinding.parentPath: RsPath?
    get() = ancestorStrict()

abstract class RsAssocTypeBindingMixin : RsStubbedNamedElementImpl<RsAssocTypeBindingStub>,
                                         RsAssocTypeBinding {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RsAssocTypeBindingStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getReference(): RsReference = RsAssocTypeBindingReferenceImpl(this)

    override val referenceNameElement: PsiElement get() = identifier

    override val referenceName: String get() = stub?.name ?: super.referenceName
}
