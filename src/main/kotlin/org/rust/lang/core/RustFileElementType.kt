package org.rust.lang.core

import com.intellij.psi.PsiFile
import com.intellij.psi.StubBuilder
import com.intellij.psi.stubs.DefaultStubBuilder
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import com.intellij.psi.tree.IStubFileElementType
import org.rust.lang.RustLanguage
import org.rust.lang.core.psi.impl.RustFile
import org.rust.lang.core.stubs.RustFileStub

object RustFileElementType : IStubFileElementType<RustFileStub>(RustLanguage) {
    override fun getStubVersion(): Int = 34

    override fun getBuilder(): StubBuilder = object : DefaultStubBuilder() {
        override fun createStubForFile(file: PsiFile): StubElement<*> = RustFileStub(file as RustFile)
    }

    override fun serialize(stub: RustFileStub, dataStream: StubOutputStream) {
        dataStream.writeBoolean(stub.hasNoStdAttr)
    }

    override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): RustFileStub {
        return RustFileStub(null, dataStream.readBoolean())
    }

    override fun getExternalId(): String = "Rust.file"
}
