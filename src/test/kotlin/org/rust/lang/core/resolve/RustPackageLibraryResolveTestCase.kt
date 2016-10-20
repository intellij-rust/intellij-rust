package org.rust.lang.core.resolve

import com.intellij.openapi.module.Module
import com.intellij.testFramework.LightProjectDescriptor
import org.rust.cargo.project.CargoProjectDescription
import org.rust.cargo.toolchain.impl.CleanCargoMetadata

class RustPackageLibraryResolveTestCase : RustMultiFileResolveTestCaseBase() {

    fun testLibraryAsCrate() = doTestResolved("library_as_crate/main.rs", "library_as_crate/lib.rs")
    fun testCrateAlias() = doTestResolved("crate_alias/main.rs", "crate_alias/lib.rs")

    private object WithLibraryProjectDescriptor : RustProjectDescriptorBase() {
        override fun testCargoProject(module: Module, contentRoot: String): CargoProjectDescription {
            return CleanCargoMetadata(listOf(testCargoPackage(contentRoot, name = "my_lib")), emptyList()).let {
                CargoProjectDescription.deserialize(it)!!
            }
        }
    }

    override fun getProjectDescriptor(): LightProjectDescriptor = WithLibraryProjectDescriptor
}
