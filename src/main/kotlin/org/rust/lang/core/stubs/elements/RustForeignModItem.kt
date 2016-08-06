package org.rust.lang.core.stubs.elements

import com.intellij.psi.stubs.*
import org.rust.lang.core.psi.RustForeignModItemElement
import org.rust.lang.core.psi.impl.RustForeignModItemElementImpl
import org.rust.lang.core.stubs.RustStubElementType

object RustForeignModItemStubElementType
    : RustStubElementType<RustForeignModItemElementStub, RustForeignModItemElement>("FOREIGN_MOD_ITEM") {

    override fun serialize(stub: RustForeignModItemElementStub, dataStream: StubOutputStream) {
        // NOP
    }

    override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): RustForeignModItemElementStub {
        return RustForeignModItemElementStub(parentStub, this)
    }

    override fun createStub(psi: RustForeignModItemElement, parentStub: StubElement<*>?): RustForeignModItemElementStub =
        RustForeignModItemElementStub(parentStub, this)

    override fun createPsi(stub: RustForeignModItemElementStub): RustForeignModItemElement =
        RustForeignModItemElementImpl(stub, this)

    override fun indexStub(stub: RustForeignModItemElementStub, sink: IndexSink) {
        // NOP
    }
}

class RustForeignModItemElementStub(parent: StubElement<*>?, elementType: IStubElementType<*, *>)
    : StubBase<RustForeignModItemElement>(parent, elementType)
