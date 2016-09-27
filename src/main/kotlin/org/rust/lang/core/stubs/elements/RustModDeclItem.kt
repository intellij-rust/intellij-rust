package org.rust.lang.core.stubs.elements


import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.stubs.*
import com.intellij.util.PathUtil
import org.rust.lang.core.psi.RustModDeclItemElement
import org.rust.lang.core.psi.impl.RustModDeclItemElementImpl
import org.rust.lang.core.psi.impl.mixin.isLocal
import org.rust.lang.core.psi.impl.mixin.pathAttribute
import org.rust.lang.core.stubs.RustNamedElementStub
import org.rust.lang.core.stubs.RustNamedStubElementType
import org.rust.lang.core.stubs.index.RustModulesIndex

object RustModDeclItemStubElementType : RustNamedStubElementType<RustModDeclElementItemStub, RustModDeclItemElement>("MOD_DECL_ITEM") {
    override fun createStub(psi: RustModDeclItemElement, parentStub: StubElement<*>?): RustModDeclElementItemStub =
        RustModDeclElementItemStub(parentStub, this, psi.name, psi.isPublic, psi.pathAttribute, psi.isLocal)

    override fun createPsi(stub: RustModDeclElementItemStub): RustModDeclItemElement =
        RustModDeclItemElementImpl(stub, this)

    override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): RustModDeclElementItemStub =
        RustModDeclElementItemStub(parentStub, this,
            dataStream.readNameAsString(),
            dataStream.readBoolean(),
            dataStream.readUTFFast().let { if (it == "") null else it },
            dataStream.readBoolean()
        )

    override fun serialize(stub: RustModDeclElementItemStub, dataStream: StubOutputStream) = with(dataStream) {
        writeName(stub.name)
        writeBoolean(stub.isPublic)
        writeUTFFast(stub.pathAttribute ?: "")
        writeBoolean(stub.isLocal)
    }

    override fun indexStub(stub: RustModDeclElementItemStub, sink: IndexSink) {
        super.indexStub(stub, sink)
        val key = stub.pathAttribute?.let { FileUtil.getNameWithoutExtension(PathUtil.getFileName(it)) }
            ?: stub.name
            ?: return

        sink.occurrence(RustModulesIndex.KEY, key)
    }
}


class RustModDeclElementItemStub(
    parent: StubElement<*>?,
    elementType: IStubElementType<*, *>,
    name: String?,
    isPublic: Boolean,
    val pathAttribute: String?,
    val isLocal: Boolean
) : RustNamedElementStub<RustModDeclItemElement>(parent, elementType, name ?: "", isPublic)
