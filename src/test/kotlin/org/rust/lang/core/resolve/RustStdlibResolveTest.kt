package org.rust.lang.core.resolve

import com.intellij.testFramework.LightProjectDescriptor
import org.rust.cargo.project.workspace.cargoProject

class RustStdlibResolveTest : RustMultiFileResolveTestBase() {

    override val dataPath = "org/rust/lang/core/resolve/fixtures/stdlib"

    override fun getProjectDescriptor(): LightProjectDescriptor = WithStdlibRustProjectDescriptor

    fun testHasStdlibSources() {
        val cargoProject = myModule.cargoProject
        cargoProject?.findExternCrateRootByName("std")
            ?: error("No Rust SDK sources found during test.\nTry running `rustup component add rust-src`")
    }

    fun testResolveFs() = stubOnlyResolve("""
    //- main.rs
        use std::fs::File;
                    //^ ...libstd/fs.rs

        fn main() {}
    """)

    fun testResolveCollections() = stubOnlyResolve("""
    //- main.rs
        use std::collections::Bound;
                             //^ ...libcollections/lib.rs

        fn main() {}
    """)

    fun testResolveCore() = stubOnlyResolve("""
    //- main.rs
        // FromStr is defined in `core` and reexported in `std`
        use std::str::FromStr;
                        //^ ...libcore/str/mod.rs

        fn main() { }
    """)

    fun testResolvePrelude() = stubOnlyResolve("""
    //- main.rs
        fn main() {
            let _ = String::new();
                    //^  ...libcollections/string.rs
        }
    """)

    fun testResolvePreludeInModule() = stubOnlyResolve("""
    //- main.rs
        mod tests {
            fn test() {
                let _ = String::new();
                        //^  ...libcollections/string.rs
            }
        }
    """)

    fun testResolveBox() = stubOnlyResolve("""
    //- main.rs
        fn main() {
            let _ = Box::new(92);
                   //^ ...liballoc/boxed.rs
        }
    """)

    fun testDontPutStdInStd() = stubOnlyResolve("""
    //- main.rs
        use std::std;
                //^ unresolved
    """)

    fun testNoCoreExcludesCore() = stubOnlyResolve("""
    //- main.rs
        #![no_std]
        use core::core;
                  //^ unresolved
    """)

    fun testNoCoreExcludesStd() = stubOnlyResolve("""
    //- main.rs
        #![no_std]
        use core::std;
                  //^ unresolved
    """)

    fun testResolveOption() = doTestResolved("option/main.rs")

    fun testPreludeVisibility1() = doTestUnresolved("prelude_visibility1/main.rs")
    fun testPreludeVisibility2() = doTestUnresolved("prelude_visibility2/main.rs")
}
