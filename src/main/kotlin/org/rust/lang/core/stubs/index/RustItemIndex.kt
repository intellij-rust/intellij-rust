package org.rust.lang.core.stubs.index

import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndexKey
import org.rust.lang.core.psi.RustItem

class RustItemIndex : StringStubIndexExtension<RustItem>() {
    companion object {
        val KEY: StubIndexKey<String, RustItem> = StubIndexKey.createIndexKey("rust.items")
    }

    override fun getVersion(): Int = 0

    override fun getKey(): StubIndexKey<String, RustItem> = KEY
}
