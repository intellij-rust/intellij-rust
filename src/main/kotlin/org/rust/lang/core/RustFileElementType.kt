package org.rust.lang.core

import com.intellij.psi.tree.IStubFileElementType
import org.rust.lang.RustLanguage
import org.rust.lang.core.stubs.RustFileStub

object RustFileElementType : IStubFileElementType<RustFileStub>(RustLanguage) {
    override fun getStubVersion(): Int = 14
}
