package org.rust.lang.core.resolve

import com.intellij.openapi.module.Module
import com.intellij.testFramework.LightProjectDescriptor
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.toolchain.impl.CleanCargoMetadata

class RsPackageLibraryResolveTest : RsResolveTestBase() {

    fun testLibraryAsCrate() = stubOnlyResolve("""
    //- main.rs
        extern crate my_lib;

        fn main() {
            my_lib::hello();
        }         //^ lib.rs

    //- lib.rs
        pub fn hello() {}
    """)

    fun testCrateAlias() = stubOnlyResolve("""
    //- main.rs
        extern crate my_lib as other_name;

        fn main() {
            other_name::hello();
        }                //^ lib.rs

    //- lib.rs
        pub fn hello() {}
    """)

    override fun getProjectDescriptor(): LightProjectDescriptor = WithLibraryProjectDescriptor

    private object WithLibraryProjectDescriptor : RustProjectDescriptorBase() {
        override fun testCargoProject(module: Module, contentRoot: String): CargoWorkspace {
            return CleanCargoMetadata(listOf(testCargoPackage(contentRoot, name = "my_lib")), emptyList()).let {
                CargoWorkspace.deserialize(it)
            }
        }
    }
}
