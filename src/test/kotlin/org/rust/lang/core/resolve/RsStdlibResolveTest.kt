/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.lang.core.types.infer.TypeInferenceMarks

@ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
class RsStdlibResolveTest : RsResolveTestBase() {

    override fun runTest() {
        for (edition in CargoWorkspace.Edition.values()) {
            project.cargoProjects.setEdition(edition)
            super.runTest()
        }
    }

    fun `test resolve fs`() = stubOnlyResolve("""
    //- main.rs
        use std::fs::File;
                    //^ ...libstd/fs.rs

        fn main() {}
    """)

    // BACKCOMPAT: Rust 1.25.0
    fun `test resolve collections`() = stubOnlyResolve("""
    //- main.rs
        use std::collections::Bound;
                             //^ ...lib.rs|...libcore/ops/range.rs

        fn main() {}
    """)

    fun `test resolve core`() = stubOnlyResolve("""
    //- main.rs
        // FromStr is defined in `core` and reexported in `std`
        use std::str::FromStr;
                        //^ ...libcore/str/mod.rs

        fn main() { }
    """)

    fun `test resolve prelude`() = stubOnlyResolve("""
    //- main.rs
        fn main() {
            let _ = String::new();
                    //^  ...string.rs
        }
    """)

    fun `test resolve prelude in module`() = stubOnlyResolve("""
    //- main.rs
        mod tests {
            fn test() {
                let _ = String::new();
                        //^  ...string.rs
            }
        }
    """)

    fun `test resolve box`() = stubOnlyResolve("""
    //- main.rs
        fn main() {
            let _ = Box::new(92);
                   //^ ...liballoc/boxed.rs
        }
    """)

    fun `test don't put std in std`() = stubOnlyResolve("""
    //- main.rs
        use std::std;
                //^ unresolved
    """)

    fun `test no core excludes core`() = stubOnlyResolve("""
    //- main.rs
        #![no_std]
        use core::core;
                  //^ unresolved
    """)

    fun `test no core excludes std`() = stubOnlyResolve("""
    //- main.rs
        #![no_std]
        use core::std;
                  //^ unresolved
    """)

    fun `test resolve option`() = stubOnlyResolve("""
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

    fun `test prelude visibility 1`() = stubOnlyResolve("""
    //- main.rs
        mod m { }

        fn main() { m::Some; }
                      //^ unresolved
    """)

    fun `test prelude visibility 2`() = stubOnlyResolve("""
    //- main.rs
        mod m { }

        fn main() { use self::m::Some; }
                                //^ unresolved
    """)

    // BACKCOMPAT: Rust 1.26.0
    fun `test string slice resolve`() = stubOnlyResolve("""

    //- main.rs
        fn main() { "test".lines(); }
                            //^ ...str.rs|...str/mod.rs
    """)

    // BACKCOMPAT: Rust 1.26.0
    fun `test slice resolve`() = stubOnlyResolve("""
    //- main.rs
        fn main() {
            let x : [i32];
            x.iter()
             //^ ...slice.rs|...slice/mod.rs
        }
    """)

    // BACKCOMPAT: Rust 1.25.0
    fun `test inherent impl char 1`() = stubOnlyResolve("""
    //- main.rs
        fn main() { 'Z'.is_lowercase(); }
                      //^ .../char.rs|...libcore/char/methods.rs
    """)

    // BACKCOMPAT: Rust 1.25.0
    fun `test inherent impl char 2`() = stubOnlyResolve("""
    //- main.rs
        fn main() { char::is_lowercase('Z'); }
                        //^ .../char.rs|...libcore/char/methods.rs
    """)

    fun `test inherent impl str 1`() = stubOnlyResolve("""
    //- main.rs
        fn main() { "Z".to_uppercase(); }
                      //^ ...str.rs
    """)

    fun `test inherent impl str 2`() = stubOnlyResolve("""
    //- main.rs
        fn main() { str::to_uppercase("Z"); }
                       //^ ...str.rs
    """)

    fun `test inherent impl f32 1`() = stubOnlyResolve("""
    //- main.rs
        fn main() { 0.0f32.sqrt(); }
                         //^ .../f32.rs
    """)

    fun `test inherent impl f32 2`() = stubOnlyResolve("""
    //- main.rs
        fn main() { f32::sqrt(0.0f32); }
                       //^ .../f32.rs
    """)

    fun `test inherent impl f32 3`() = expect<IllegalStateException> {
        stubOnlyResolve("""
    //- main.rs
        fn main() { <f32>::sqrt(0.0f32); }
                         //^ .../f32.rs
    """)
    }

    fun `test inherent impl f64 1`() = stubOnlyResolve("""
    //- main.rs
        fn main() { 0.0f64.sqrt(); }
                         //^ .../f64.rs
    """)

    fun `test inherent impl f64 2`() = stubOnlyResolve("""
    //- main.rs
        fn main() { f64::sqrt(0.0f64); }
                       //^ .../f64.rs
    """)

    fun `test inherent impl const ptr 1`() = stubOnlyResolve("""
    //- main.rs
        fn main() {
            let p: *const char;
            p.is_null();
            //^ ...libcore/ptr.rs
        }
    """)

    fun `test inherent impl const ptr 2`() = expect<IllegalStateException> {
        stubOnlyResolve("""
    //- main.rs
        fn main() {
            let p: *const char;
            <*const char>::is_null(p);
                         //^ ...libcore/ptr.rs
        }
    """)
    }

    fun `test inherent impl const ptr 3`() = expect<IllegalStateException> {
        stubOnlyResolve("""
    //- main.rs
        fn main() {
            let p: *mut char;
            <*const char>::is_null(p); //Pass a *mut pointer to a *const method
                         //^ ...libcore/ptr.rs
        }
    """)
    }

    fun `test inherent impl mut ptr 1`() = stubOnlyResolve("""
    //- main.rs
        fn main() {
            let p: *mut char;
            p.is_null();
            //^ ...libcore/ptr.rs
        }
    """)

    fun `test inherent impl mut ptr 2`() = expect<IllegalStateException> {
        stubOnlyResolve("""
    //- main.rs
        fn main() {
            let p: *mut char;
            <*mut char>::is_null(p);
                       //^ ...libcore/ptr.rs
        }
    """)
    }

    fun `test println macro`() = stubOnlyResolve("""
    //- main.rs
        fn main() {
            println!("Hello, World!");
        }   //^ ...libstd/macros.rs
    """)

    fun `test assert_eq macro`() = stubOnlyResolve("""
    //- main.rs
        fn main() {
            assert_eq!("Hello, World!", "");
        }   //^ ...libcore/macros.rs
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

    fun `test array indexing`() = stubOnlyResolve("""
    //- main.rs
        struct Foo(i32);
        impl Foo { fn foo(&self) {} }

        fn main() {
            let xs = [Foo(0), Foo(123)];
            xs[0].foo();
                 //^ ...main.rs
        }
    """)

    fun `test vec indexing`() = stubOnlyResolve("""
    //- main.rs
        fn foo(xs: Vec<String>) {
            xs[0].capacity();
                 //^ ...string.rs
        }
    """)

    // BACKCOMPAT: Rust 1.26.0
    fun `test vec slice`() = stubOnlyResolve("""
    //- main.rs
        fn foo(xs: Vec<i32>) {
            xs[0..3].len();
                     //^ ...slice.rs|...slice/mod.rs
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

    fun `test String plus &str`() = stubOnlyResolve("""
    //- main.rs
        fn main() {
            (String::new() + "foo").capacity();
                                     //^ ...string.rs
        }
    """)

    // BACKCOMPAT: Rust 1.24.1
    fun `test Instant minus Duration`() = stubOnlyResolve("""
    //- main.rs
        use std::time::{Duration, Instant};
        fn main() {
            (Instant::now() - Duration::from_secs(3)).elapsed();
                                                      //^ ...time.rs|...time/mod.rs
        }
    """)

    fun `test resolve assignment operator`() = stubOnlyResolve("""
    //- main.rs
        fn main() {
            let s = String::new();
            s += "foo";
             //^ ...string.rs
        }
    """)

    // BACKCOMPAT: Rust 1.24.1
    fun `test resolve arithmetic operator`() = stubOnlyResolve("""
    //- main.rs
        use std::time::{Duration, Instant};
        fn main() {
            let x = Instant::now() - Duration::from_secs(3);
                                 //^ ...time.rs|...time/mod.rs
        }
    """)

    fun `test autoderef Rc`() = stubOnlyResolve("""
    //- main.rs
        use std::rc::Rc;
        struct Foo;
        impl Foo { fn foo(&self) {} }

        fn main() {
            let x = Rc::new(Foo);
            x.foo()
        }    //^ ...main.rs
    """)

    fun `test generic pattern matching`() = stubOnlyResolve("""
    //- main.rs
        fn maybe() -> Option<String> { unimplemented!() }

        fn main() {
            if let Some(x) = maybe() {
                x.capacity();
                  //^ ...string.rs
            }
        }
    """)

    fun `test resolve derive traits`() {
        val traitToPath = mapOf(
            "Clone" to "clone.rs",
            "Copy" to "marker.rs",
            "Debug" to "fmt/mod.rs",
            "Default" to "default.rs",
            "Eq" to "cmp.rs",
            "Hash" to "hash/mod.rs",
            "Ord" to "cmp.rs",
            "PartialEq" to "cmp.rs",
            "PartialOrd" to "cmp.rs"
        )
        for ((trait, path) in traitToPath) {
            stubOnlyResolve("""
            //- main.rs
                #[derive($trait)]
                        //^ ...libcore/$path
                struct Foo;
            """)
        }
    }

    fun `test infer lambda expr`() = stubOnlyResolve("""
    //- main.rs
        struct S;
        impl S {
            fn foo(&self) {}
        }
        fn main() {
            let test: Vec<S> = Vec::new();
            test.into_iter().map(|a| a.foo());
        }                             //^ ...main.rs
    """)

    fun `test derivable trait method`() = stubOnlyResolve("""
    //- main.rs
        #[derive(Clone)]
        struct Foo;

        fn bar(foo: Foo) {
            let x = foo.clone();
                         //^ ...libcore/clone.rs
        }
    """)

    fun `test multiple derivable trait method`() = stubOnlyResolve("""
    //- main.rs
        #[derive(Debug)]
        #[derive(Clone)]
        #[derive(Hash)]
        struct Foo;

        fn bar(foo: Foo) {
            let x = foo.clone();
                         //^ ...libcore/clone.rs
        }
    """)

    fun `test derivable trait method call`() = stubOnlyResolve("""
    //- main.rs
        #[derive(Clone)]
        struct Foo;
        impl Foo {
            fn foo(&self) {}
        }

        fn bar(foo: Foo) {
            let x = foo.clone();
            x.foo();
              //^ ...main.rs
        }
    """)

    fun `test ? operator with result`() = checkByCode("""
        struct S { field: u32 }
                    //X
        fn foo() -> Result<S, ()> { unimplemented!() }

        fn main() {
            let s = foo()?;
            s.field;
            //^
        }
    """)

    fun `test try operator with option`() = checkByCode("""
        struct S { field: u32 }
                    //X
        fn foo() -> Option<S> { unimplemented!() }

        fn main() {
            let s = foo()?;
            s.field;
            //^
        }
    """, TypeInferenceMarks.questionOperator)



    fun `test try! macro with aliased Result`() = checkByCode("""
        mod io {
            pub struct IoError;
            pub type IoResult<T> = Result<T, IoError>;

            pub struct S { field: u32 }
                          //X

            pub fn foo() -> IoResult<S> { unimplemented!() }

        }

        fn main() {
            let s = io::foo()?;
            s.field;
              //^
        }
    """)

    fun `test method defined in out of scope trait from prelude`() = stubOnlyResolve("""
    //- a.rs
        use super::S;
        impl Into<u8> for S { fn into(self) -> u8 { unimplemented!() } }
    //- b.rs
        use super::S;
        pub trait B where Self: Sized { fn into(self) -> u8 { unimplemented!() } }
        impl B for S {}
    //- main.rs
        mod a; mod b;
        struct S;

        fn main() {
            let _: u8 = S.into();
        }               //^ a.rs
    """, TypeInferenceMarks.methodPickTraitScope)

    fun `test &str into String`() = stubOnlyResolve("""
    //- main.rs
        fn main() {
            let _: String = "".into();
        }                    //^ ...convert.rs
    """)

    fun `test resolve with no_std attribute`() = stubOnlyResolve("""
    //- main.rs
        #![no_std]

        fn foo(v: Vec) {}
                 //^ unresolved
    """)

    fun `test inherent impl have higher priority than derived`() = checkByCode("""
        #[derive(Clone)]
        struct S;
        impl S {
            fn clone(&self) {}
        }    //X
        fn main() {
            S.clone();
        }   //^
    """)
}
