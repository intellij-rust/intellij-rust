/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.stubs

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.*
import org.rust.lang.core.psi.ext.RsElement

open class RsPlaceholderStub<PsiT : RsElement>(parent: StubElement<*>?, elementType: IStubElementType<*, *>)
    : StubBase<PsiT>(parent, elementType) {

    open class Type<PsiT : RsElement>(
        debugName: String,
        private val psiCtor: (RsPlaceholderStub<*>, IStubElementType<*, *>) -> PsiT
    ) : RsStubElementType<RsPlaceholderStub<*>, PsiT>(debugName) {

        override fun shouldCreateStub(node: ASTNode): Boolean = createStubIfParentIsStub(node)

        override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): RsPlaceholderStub<PsiT>
            = RsPlaceholderStub(parentStub, this)

        override fun serialize(stub: RsPlaceholderStub<*>, dataStream: StubOutputStream) {
        }

        override fun createPsi(stub: RsPlaceholderStub<*>): PsiT = psiCtor(stub, this)

        override fun createStub(psi: PsiT, parentStub: StubElement<*>?): RsPlaceholderStub<PsiT>
            = RsPlaceholderStub(parentStub, this)
    }
}
