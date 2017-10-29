/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import com.intellij.openapi.module.Module
import com.intellij.testFramework.LightProjectDescriptor
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.CargoWorkspaceData
import java.nio.file.Paths

class RsPackageLibraryResolveTest : RsResolveTestBase() {

    fun `test library as crate`() = stubOnlyResolve("""
    //- main.rs
        extern crate my_lib;

        fn main() {
            my_lib::hello();
        }         //^ lib.rs

    //- lib.rs
        pub fn hello() {}
    """)

    fun `test crate alias`() = stubOnlyResolve("""
    //- main.rs
        extern crate my_lib as other_name;

        fn main() {
            other_name::hello();
        }                //^ lib.rs

    //- lib.rs
        pub fn hello() {}
    """)


    fun `test macro rules`() = stubOnlyResolve("""
    //- main.rs
        #[macro_use]
        extern crate my_lib;

        fn main() {
            foo_bar!();
        }  //^ lib.rs
    //- lib.rs
        #[macro_export]
        macro_rules! foo_bar { () => {} }
    """)

    override fun getProjectDescriptor(): LightProjectDescriptor = WithLibraryProjectDescriptor

    private object WithLibraryProjectDescriptor : RustProjectDescriptorBase() {
        override fun testCargoProject(module: Module, contentRoot: String): CargoWorkspace {
            return CargoWorkspaceData(listOf(testCargoPackage(contentRoot, name = "my_lib")), emptyMap()).let {
                CargoWorkspace.deserialize(Paths.get("/my-crate/Cargo.toml"), it)
            }
        }
    }
}
