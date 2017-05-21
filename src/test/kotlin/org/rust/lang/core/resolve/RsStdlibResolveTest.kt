package org.rust.lang.core.resolve

import org.rust.cargo.project.workspace.cargoWorkspace

class RsStdlibResolveTest : RsResolveTestBase() {

    override fun getProjectDescriptor() = WithStdlibRustProjectDescriptor

    fun testHasStdlibSources() {
        val cargoProject = myModule.cargoWorkspace
        cargoProject?.findCrateByNameApproximately("std")?.crateRoot
            ?: error("No Rust sources found during test.\nTry running `rustup component add rust-src`")
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

    fun testResolveOption() = stubOnlyResolve("""
    //- main.rs
        fn f(i: i32) -> Option<i32> {}

        fn bar() {
            if let Some(x) = f(42) {
                if let Some(y) = f(x) {
                      //^ ...libcore/option.rs
                    if let Some(z) = f(y) {}
                }
            }
        }
    """)

    fun testPreludeVisibility1() = stubOnlyResolve("""
    //- main.rs
        mod m { }

        fn main() { m::Some; }
                      //^ unresolved
    """)

    fun testPreludeVisibility2() = stubOnlyResolve("""
    //- main.rs
        mod m { }

        fn main() { use self::m::Some; }
                                //^ unresolved
    """)

    fun `test string slice resolve`() = stubOnlyResolve("""

    //- main.rs
        fn main() { "test".lines(); }
                            //^ ...libcollections/str.rs
    """)

    fun `test slice resolve`() = stubOnlyResolve("""
    //- main.rs
        fn main() {
            let x : [i32];
            x.iter()
             //^ ...libcollections/slice.rs
        }
    """)

    fun `test println macro`() = stubOnlyResolve("""
    //- main.rs
        fn main() {
            println!("Hello, World!");
        }   //^ ...libstd/macros.rs
    """)

    fun `test iterating a vec`() = stubOnlyResolve("""
    //- main.rs
        struct FooBar;
        impl FooBar { fn foo(&self) {} }

        fn foo(xs: Vec<FooBar>) {
            for x in xs {
                x.foo()
            }    //^ ...main.rs
        }
    """)

    fun `test resolve None in pattern`() = stubOnlyResolve("""
    //- main.rs
        fn foo(x: Option<i32>) -> i32 {
            match x {
                Some(v) => V,
                None => 0,
            }  //^ ...libcore/option.rs
        }
    """)

    fun `test vec indexing`() = stubOnlyResolve("""
    //- main.rs
        fn foo(xs: Vec<String>) {
            xs[0].capacity();
                 //^ ...libcollections/string.rs
        }
    """)

    fun `test resolve with defaulted type parameters`() = stubOnlyResolve("""
    //- main.rs
        use std::collections::HashSet;

        fn main() {
            let things = HashSet::new();
        }                        //^ ...hash/set.rs
    """)

    fun `test resolve with unsatisfied bounds`() = stubOnlyResolve("""
    //- main.rs
        fn main() { foo().unwrap(); }
                        //^ ...libcore/result.rs

        fn foo() -> Result<i32, i32> { Ok(42) }
    """)
}
