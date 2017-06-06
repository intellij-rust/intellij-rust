package org.rust.ide.docs

import org.intellij.lang.annotations.Language

class RsQuickNavigationInfoTest : RsDocumentationProviderTest() {
    override fun getProjectDescriptor() = WithStdlibRustProjectDescriptor

    fun `test nested function`() = doTest("""
        mod a {
            mod b {
                mod c {
                    pub fn double(x: i16) -> i32 { x * 2 }
                }
            }

            fn main() {
                b::c::double(2);
                      //^
            }
        }
    """, """
        pub fn <b>double</b>(x: i16) -&gt; i32 [main.rs]
    """)

    fun `test no comments`() = doTest("""
        /// Comments should not be present in
        /// quick navigation info
        fn foo() { }

        fn main() {
            foo()
          //^
        }
    """, """
        fn <b>foo</b>() [main.rs]
    """)

    fun `test big signature`() = doTest("""
        /// Docs
        #[cfg(test)]
        /// More Docs
        pub const unsafe extern "C" fn foo<T>(x: T) -> u32 where T: Clone { 92 }

        fn main() {
            foo()
          //^
        }
    """, """
        pub const unsafe extern &quot;C&quot; fn <b>foo</b>&lt;T&gt;(x: T) -&gt; u32 where T: Clone [main.rs]
    """)

    fun `test method`() = doTest("""
        struct S;
        impl S {
            pub fn consume(self) -> i32 { 92 }
        }
        fn main() {
            let s = S;
            s.consume();
            //^
        }
    """, """
        pub fn <b>consume</b>(self) -&gt; i32 [main.rs]
    """)

    fun `test trait method`() = doTest("""
        trait MyTrait {
            /// Documented
            fn my_func();
             //^
        }
    """, """
        fn <b>my_func</b>() [main.rs]
    """)

    fun `test multiple where`() = doTest("""
        /// Docs
        #[cfg(test)]
        /// More Docs
        pub const unsafe extern "C" fn foo<T, U, V>(x: T) -> u32 where T: Clone,
                                                                       U: Debug,
                                                                       V: Display
        { 92 }

        fn main() {
            foo()
          //^
        }
    """, """
        pub const unsafe extern &quot;C&quot; fn <b>foo</b>&lt;T, U, V&gt;(x: T) -&gt; u32 where T: Clone, U: Debug, V: Display [main.rs]
    """)

    fun `test expanded signature`() = doTest("""
        /// Docs
        #[cfg(test)]
        /// More Docs
        pub const unsafe extern "C" fn foo<T, U, V>(
         x: T,
         y: u32,
         z: u64
        ) -> (
         u32,
         u64,
         u64
        ) where T: Clone,
                U: Debug,
                V: Display
        {
            92
        }

        fn main() {
            foo()
          //^
        }
    """, """
        pub const unsafe extern &quot;C&quot; fn <b>foo</b>&lt;T, U, V&gt;(x: T, y: u32, z: u64) -&gt; (u32, u64, u64) where T: Clone, U: Debug, V: Display [main.rs]
    """)

    fun `test variable 1`() = doTest("""
        fn main() {
            let xyz = 42;
                //^
            do_something(xyz);
        }
    """, """
        variable <b>xyz</b>: i32
    """)

    fun `test variable 2`() = doTest("""
        fn main() {
            let mut xyz = 42;
            do_something(xyz);
                        //^
        }
    """, """
        variable <b>xyz</b>: i32
    """)

    fun `test module 1`() = doTest("""
        pub mod foo { /* foo code */ }
        use self::foo;
                  //^
    """, """
        pub mod <b>foo</b> [main.rs]
    """)

    fun `test module 2`() = doTest("""
        mod foo { pub mod bar { pub mod baz { } } }
        use self::foo::bar::baz;
                       //^
    """, """
        pub mod <b>bar</b> [main.rs]
    """)

    fun `test struct`() = doTest("""
        pub struct Foo { a: bool }
        use self::Foo;
                  //^
    """, """
        pub struct <b>Foo</b> [main.rs]
    """)

    fun `test struct with tuple fields`() = doTest("""
        pub struct Foo(a: bool);
        use self::Foo;
                  //^
    """, """
        pub struct <b>Foo</b>(a: bool) [main.rs]
    """)

    fun `test struct field`() = doTest("""
        pub struct Foo { pub foo: bool }
        fn foo() {
            let _ = Foo { foo: true };
                          //^
        }
    """, """
        pub <b>foo</b>: bool [main.rs]
    """)

    fun `test enum`() = doTest("""
        enum Foo { BAR, BAZ };
        use self::Foo;
                  //^
    """, """
        enum <b>Foo</b> [main.rs]
    """)

    fun `test enum variant`() = doTest("""
        enum Foo { BAR(bool, u32) };
        use self::Foo::BAR;
                       //^
    """, """
        <b>BAR</b>(bool, u32) [main.rs]
    """)

    fun `test trait`() = doTest("""
        pub trait Foo { /* no methods */ }
        impl Foo for u32 {}
             //^
    """, """
        pub trait <b>Foo</b> [main.rs]
    """)

    fun `test type alias`() = doTest("""
        type Str = &'static str;
        fn foo(s: Str) {}
                  //^
    """, """
        type <b>Str</b> = &amp;&#39;static str [main.rs]
    """)

    fun `test const`() = doTest("""
        pub const FOO: u32 = 42;
        fn foo() { let _ = FOO; }
                           //^
    """, """
        pub const <b>FOO</b>: u32 = 42 [main.rs]
    """)

    fun `test static`() = doTest("""
        pub static mut FOO: bool = true;
        fn foo() { let _ = FOO; }
                           //^
    """, """
        pub static mut <b>FOO</b>: bool = true [main.rs]
    """)

    fun `test lifetime`() = doTest("""
        type Str<'foo> = &'foo str;
                           //^
    """, """
        <i>lifetime:</i> <b>'foo</b> [main.rs]
    """)

    fun `test type parameter`() = doTest("""
        fn foo<T>(t: T) {}
                   //^
    """, """
        <i>type parameter:</i> <b>T</b> [main.rs]
    """)

    fun `test loop label`() = doTest("""
        fn foo() {
            'foo: loop { break 'foo }
                                //^
        }
    """, """
        <b>'foo</b>: loop [main.rs]
    """)

    fun `test for loop label`() = doTest("""
        fn foo() {
            'foo: for x in 0..4 { continue 'foo }
                                            //^
        }
    """, """
        <b>'foo</b>: for x in 0..4 [main.rs]
    """)

    fun `test while loop label`() = doTest("""
        fn foo() {
            'foo: while true { continue 'foo }
                                         //^
        }
    """, """
        <b>'foo</b>: while true [main.rs]
    """)

    fun `test self parameter 1`() = doTest("""
        struct Foo;
        impl Foo {
            fn foo(&mut self) { let _ = self; }
                                         //^
        }
    """, """
        &amp;mut <b>self</b> [main.rs]
    """)

    fun `test self parameter 2`() = doTest("""
        struct Foo;
        impl Foo {
            fn foo(self: &mut Foo) { let _ = self; }
                                              //^
        }
    """, """
        <b>self</b>: &amp;mut Foo [main.rs]
    """)

    fun `test value parameter 1`() = doTest("""
        fn foo(val: &mut u32) { let _ = val; }
                                       //^
    """, """
        value parameter <b>val</b>: &amp;mut u32
    """)

    fun `test value parameter 2`() = doTest("""
        fn foo((val, flag): &mut (u32, bool)) { let _ = val; }
                                                       //^
    """, """
        value parameter <b>val</b>: &lt;unknown&gt;
    """)

    fun `test value parameter 3`() = doTest("""
        fn foo((val, flag): &mut (u32, bool)) { let _ = flag; }
                                                        //^
    """, """
        value parameter <b>flag</b>: &lt;unknown&gt;
    """)

    fun `test match arm 1`() = doTest("""
        fn foo() {
            match Some(12) {
                foo => foo
                       //^
            };
        }
    """, """
        match arm binding <b>foo</b>: Option&lt;i32&gt;
    """)

    fun `test match arm 2`() = doTest("""
        fn foo() {
            match Some(12) {
                Some(foo) => foo
                             //^
                None => 0
            };
        }
    """, """
        match arm binding <b>foo</b>: i32
    """)

    fun `test match arm 3`() = doTest("""
        struct Foo { foo: bool }
        fn foo() {
            let f = Foo { foo: true };
            match f {
                Foo { foo } => foo
                               //^
            };
        }
    """, """
        match arm binding <b>foo</b>: bool
    """)

    fun `test match arm with cfg attribute`() = doTest("""
        enum Foo {}
        fn foo(f: Foo) {
            match f {
                #[cfg(afsd)]
                Basdfwe => { Basdfwe },
                                //^
                _ => {},
            }
        }
    """, """
        match arm binding <b>Basdfwe</b>: Foo
    """)

    fun `test match if let`() = doTest("""
        fn foo() {
            if let Some(ref foo) = None {
                *foo
                 //^
            }
        }
    """, """
        condition binding <b>foo</b>: T
    """)

    fun `test file 1`() = doTest("""
        const PI: f64 = std::f64::consts::PI;
                            //^
    """, """
        mod <b>f64</b> [f64.rs]
    """)

    fun `test file 2`() = doTest("""
        const PI: f64 = std::f64::consts::PI;
                        //^
    """, """
        <b>crate</b> [lib.rs]
    """)

    private fun doTest(@Language("Rust") code: String, @Language("Html") expected: String)
        = doTest(code, expected, RsDocumentationProvider::getQuickNavigateInfo)
}
