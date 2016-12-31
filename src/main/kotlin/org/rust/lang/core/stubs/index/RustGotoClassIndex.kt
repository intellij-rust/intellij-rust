package org.rust.lang.core.stubs.index

import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndexKey
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.stubs.RustFileStub

class RustGotoClassIndex : StringStubIndexExtension<RustNamedElement>() {
    override fun getVersion(): Int = RustFileStub.Type.stubVersion
    override fun getKey(): StubIndexKey<String, RustNamedElement> = KEY

    companion object {
        val KEY: StubIndexKey<String, RustNamedElement> =
            StubIndexKey.createIndexKey("org.rust.lang.core.stubs.index.RustGotoClassIndex")
    }
}
