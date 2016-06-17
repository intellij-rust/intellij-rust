package org.rust.lang.core.stubs.index

import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndexKey
import org.rust.lang.core.RustFileElementType
import org.rust.lang.core.psi.RustStructOrEnum

class RustStructOrEnumIndex : StringStubIndexExtension<RustStructOrEnum>() {
    companion object {
        val KEY: StubIndexKey<String, RustStructOrEnum> = StubIndexKey.createIndexKey("rust.structOrEnum")
    }

    override fun getVersion(): Int = RustFileElementType.stubVersion

    override fun getKey(): StubIndexKey<String, RustStructOrEnum> = KEY
}
