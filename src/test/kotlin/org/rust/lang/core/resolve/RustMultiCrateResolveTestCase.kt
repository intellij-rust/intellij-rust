package org.rust.lang.core.resolve

import com.intellij.testFramework.LightProjectDescriptor
import org.rust.cargo.project.module.persistence.ExternCrateData

class RustMultiCrateResolveTestCase : RustMultiFileResolveTestCaseBase() {
    override fun getProjectDescriptor(): LightProjectDescriptor = object : RustProjectDescriptor() {
        override val externCrates: List<ExternCrateData>
            get() = listOf(ExternCrateData(name = "my_lib", path = "lib.rs"))
    }

    fun testLibraryAsCrate() = doTestResolved("library_as_crate/main.rs", "library_as_crate/lib.rs")
    fun testCrateAlias() = doTestResolved("crate_alias/main.rs", "crate_alias/lib.rs")
}
