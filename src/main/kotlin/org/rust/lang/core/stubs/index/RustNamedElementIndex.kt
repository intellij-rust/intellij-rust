package org.rust.lang.core.stubs.index

import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndexKey
import org.rust.lang.core.psi.RsNamedElement
import org.rust.lang.core.stubs.RustFileStub

class RustNamedElementIndex : StringStubIndexExtension<RsNamedElement>() {
    override fun getVersion(): Int = RustFileStub.Type.stubVersion
    override fun getKey(): StubIndexKey<String, RsNamedElement> = KEY

    companion object {
        val KEY: StubIndexKey<String, RsNamedElement> =
            StubIndexKey.createIndexKey("org.rust.lang.core.stubs.index.RustNamedElementIndex")
    }
}
