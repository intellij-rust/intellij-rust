package org.rust.lang.core

import com.intellij.psi.stubs.IndexSink
import com.intellij.psi.tree.IStubFileElementType
import org.rust.lang.RustLanguage
import org.rust.lang.core.stubs.RustFileStub

object RustFileElementType : IStubFileElementType<RustFileStub>(RustLanguage) {
    override fun indexStub(stub: RustFileStub, sink: IndexSink) {
        super.indexStub(stub, sink)
    }
}
