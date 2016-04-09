package org.rust.lang.core.resolve

import com.intellij.openapi.module.Module
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.util.containers.MultiMap
import org.rust.cargo.CargoProjectDescription

class RustPackageLibraryResolveTestCase : RustMultiFileResolveTestCaseBase() {
    override fun getProjectDescriptor(): LightProjectDescriptor = object : RustProjectDescriptor() {

        override fun testCargoProject(module: Module, contentRoot: String): CargoProjectDescription =
            CargoProjectDescription.create(
                listOf(testCargoPackage(contentRoot, name = "my_lib")),
                MultiMap()
            )!!
    }

    fun testLibraryAsCrate() = doTestResolved("library_as_crate/main.rs", "library_as_crate/lib.rs")
    fun testCrateAlias() = doTestResolved("crate_alias/main.rs", "crate_alias/lib.rs")
}
