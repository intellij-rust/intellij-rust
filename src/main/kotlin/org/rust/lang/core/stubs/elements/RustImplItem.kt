package org.rust.lang.core.stubs.elements


import com.intellij.psi.stubs.*
import org.rust.lang.core.psi.RustImplItemElement
import org.rust.lang.core.psi.impl.RustImplItemElementImpl
import org.rust.lang.core.resolve.indexes.RustImplIndex
import org.rust.lang.core.stubs.RustElementStub
import org.rust.lang.core.stubs.RustStubElementType
import org.rust.lang.core.symbols.RustQualifiedPath
import org.rust.lang.core.types.unresolved.RustUnresolvedType
import org.rust.lang.core.types.unresolved.decay
import org.rust.lang.core.types.util.type

object RustImplItemStubElementType : RustStubElementType<RustImplItemElementStub, RustImplItemElement>("IMPL_ITEM") {

    override fun createStub(psi: RustImplItemElement, parentStub: StubElement<*>?): RustImplItemElementStub =
        RustImplItemElementStub(parentStub, this, psi.type?.type, psi.traitRef?.let { it.path.decay })

    override fun createPsi(stub: RustImplItemElementStub): RustImplItemElement =
        RustImplItemElementImpl(stub, this)

    override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): RustImplItemElementStub {
        val type        = RustUnresolvedType.deserialize(dataStream)
        val traitRef    = RustQualifiedPath.deserialize(dataStream)

        return RustImplItemElementStub(parentStub, this, type, traitRef)
    }

    override fun serialize(stub: RustImplItemElementStub, dataStream: StubOutputStream) {
        RustUnresolvedType  .serialize(stub.type,       dataStream)
        RustQualifiedPath   .serialize(stub.traitRef,   dataStream)
    }

    override fun indexStub(stub: RustImplItemElementStub, sink: IndexSink) {
        RustImplIndex.ByType.index(stub, sink)
        RustImplIndex.ByName.index(stub, sink)
    }

}

class RustImplItemElementStub(
    parent: StubElement<*>?,
    elementType: IStubElementType<*, *>,
    val type: RustUnresolvedType?,
    val traitRef: RustQualifiedPath?
) : RustElementStub<RustImplItemElement>(parent, elementType)
