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
import org.rust.lang.core.types.RustStructOrEnumTypeBase
import org.rust.lang.core.types.unresolved.RustUnresolvedPathType
import org.rust.lang.core.types.unresolved.RustUnresolvedType
import org.rust.lang.core.types.unresolved.decay
import org.rust.lang.core.types.util.type
import sun.plugin.dom.exception.InvalidStateException


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
        stub.type?.let { ty ->
            if (stub.traitRef != null) {
                sink.occurrence(RustImplIndex.ByType.KEY, RustImplIndex.ByType.Key(ty))
            } else {
                (ty as? RustUnresolvedPathType)?.let {
                    sink.occurrence(RustImplIndex.ByName.KEY, it.path.part.name)
                }
            }
        }
    }

}

class RustImplItemElementStub(
    parent: StubElement<*>?,
    elementType: IStubElementType<*, *>,
    val type: RustUnresolvedType?,
    val traitRef: RustQualifiedPath?
) : RustElementStub<RustImplItemElement>(parent, elementType)
