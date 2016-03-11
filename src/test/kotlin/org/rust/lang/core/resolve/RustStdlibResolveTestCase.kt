package org.rust.lang.core.resolve

import com.intellij.openapi.roots.ModuleRootManager
import org.assertj.core.api.Assertions.assertThat
import org.rust.RustWithSdkTestCaseBase

class RustStdlibResolveTestCase : RustWithSdkTestCaseBase() {
    override val dataPath = "org/rust/lang/core/resolve/fixtures"

    fun testSdkHasSources() {
        assertThat(ModuleRootManager.getInstance(myModule).orderEntries().sdkOnly().classesRoots)
            .hasSize(1)
    }
}
