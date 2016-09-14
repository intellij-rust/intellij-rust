package org.rust.lang.core.resolve

import com.intellij.openapi.module.Module
import com.intellij.testFramework.LightProjectDescriptor
import org.rust.cargo.toolchain.impl.CleanCargoMetadata
import org.rust.cargo.project.CargoProjectDescription
import java.util.*

class RustPackageLibraryResolveTestCase : RustMultiFileResolveTestCaseBase() {
    override fun getProjectDescriptor(): LightProjectDescriptor = object : RustProjectDescriptor() {

        override fun testCargoProject(module: Module, contentRoot: String): CargoProjectDescription =
            CleanCargoMetadata(mutableListOf(testCargoPackage(contentRoot, name = "my_lib")), ArrayList()).let {
                CargoProjectDescription.deserialize(it)!!
            }
    }

    fun testLibraryAsCrate() = doTestResolved("library_as_crate/main.rs", "library_as_crate/lib.rs")
    fun testCrateAlias() = doTestResolved("crate_alias/main.rs", "crate_alias/lib.rs")
}
