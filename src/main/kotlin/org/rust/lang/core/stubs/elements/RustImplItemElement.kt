package org.rust.lang.core.stubs.elements


import com.intellij.psi.stubs.*
import org.rust.lang.core.psi.RustImplItemElement
import org.rust.lang.core.psi.RustPathTypeElement
import org.rust.lang.core.psi.impl.RustImplItemElementImpl
import org.rust.lang.core.resolve.indexes.RustImplIndex
import org.rust.lang.core.stubs.RustElementStub
import org.rust.lang.core.stubs.RustStubElementType
import org.rust.lang.core.symbols.RustQualifiedPath
import org.rust.lang.core.symbols.RustQualifiedPathPart
import org.rust.lang.core.symbols.impl.RustNamedQualifiedPathPart
import org.rust.lang.core.symbols.impl.RustSelfQualifiedPathPart
import org.rust.lang.core.symbols.impl.RustSuperQualifiedPathPart
import org.rust.lang.core.symbols.unfold
import org.rust.lang.core.types.unresolved.RustUnresolvedType
import org.rust.lang.core.types.util.type
import sun.plugin.dom.exception.InvalidStateException


object RustImplItemStubElementType : RustStubElementType<RustImplItemElementStub, RustImplItemElement>("IMPL_ITEM") {

    override fun createStub(psi: RustImplItemElement, parentStub: StubElement<*>?): RustImplItemElementStub =
        RustImplItemElementStub(parentStub, this, psi.type?.type)

    override fun createPsi(stub: RustImplItemElementStub): RustImplItemElement =
        RustImplItemElementImpl(stub, this)

    override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): RustImplItemElementStub {
        return RustImplItemElementStub(parentStub, this, RustUnresolvedType.deserialize(dataStream))
    }

    override fun serialize(stub: RustImplItemElementStub, dataStream: StubOutputStream) {
        RustUnresolvedType.serialize(stub.type, dataStream)
    }

    override fun indexStub(stub: RustImplItemElementStub, sink: IndexSink) {
        stub.type?.let {
            sink.occurrence(RustImplIndex.KEY, RustImplIndex.Key(it))
        }
    }

}

class RustImplItemElementStub(
    parent: StubElement<*>?,
    elementType: IStubElementType<*, *>,
    val type: RustUnresolvedType?
) : RustElementStub<RustImplItemElement>(parent, elementType)
