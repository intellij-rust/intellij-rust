/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

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
        test_package::a::b::c
        pub fn <b>double</b>(x: i16) -&gt; i32
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
        test_package
        fn <b>foo</b>()
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
        test_package
        pub const unsafe extern "C" fn <b>foo</b>&lt;T&gt;(x: T) -&gt; u32<br>where<br>&nbsp;&nbsp;&nbsp;&nbsp;T: <a href="psi_element://Clone">Clone</a>,
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
        test_package
        impl <a href="psi_element://S">S</a>
        pub fn <b>consume</b>(self) -&gt; i32
    """)

    fun `test generic struct method`() = doTest("""
        struct Foo<T>(T);

        impl<T> Foo<T> {
            pub fn foo(&self) {}
                  //^
        }
    """, """
        test_package
        impl&lt;T&gt; <a href="psi_element://Foo">Foo</a>&lt;T&gt;
        pub fn <b>foo</b>(&amp;self)
    """)

    fun `test generic struct method with where clause`() = doTest("""
        struct Foo<T, F>(T, F);

        impl<T, F> Foo<T, F> where T: Ord, F: Into<String> {
            pub fn foo(&self) {}
                  //^
        }
    """, """
        test_package
        impl&lt;T, F&gt; <a href="psi_element://Foo">Foo</a>&lt;T, F&gt;<br>where<br>&nbsp;&nbsp;&nbsp;&nbsp;T: <a href="psi_element://Ord">Ord</a>,<br>&nbsp;&nbsp;&nbsp;&nbsp;F: <a href="psi_element://Into">Into</a>&lt;<a href="psi_element://String">String</a>&gt;,
        pub fn <b>foo</b>(&amp;self)
    """)

    fun `test trait method`() = doTest("""
        trait MyTrait {
            /// Documented
            fn my_func();
             //^
        }
    """, """
        test_package::MyTrait
        fn <b>my_func</b>()
    """)

    fun `test generic trait method`() = doTest("""
        trait MyTrait<T> {
            /// Documented
            fn my_func();
             //^
        }
    """, """
        test_package::MyTrait&lt;T&gt;
        fn <b>my_func</b>()
    """)

    fun `test generic trait method with where clause`() = doTest("""
        trait MyTrait<T> where T: Into<String> {
            /// Documented
            fn my_func();
             //^
        }
    """, """
        test_package::MyTrait&lt;T&gt;
        where<br>&nbsp;&nbsp;&nbsp;&nbsp;T: <a href="psi_element://Into">Into</a>&lt;<a href="psi_element://String">String</a>&gt;,
        fn <b>my_func</b>()
    """)

    fun `test multiple where`() = doTest("""
        use std::fmt::{Debug, Display};

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
        test_package
        pub const unsafe extern "C" fn <b>foo</b>&lt;T, U, V&gt;(x: T) -&gt; u32<br>where<br>&nbsp;&nbsp;&nbsp;&nbsp;T: <a href="psi_element://Clone">Clone</a>,<br>&nbsp;&nbsp;&nbsp;&nbsp;U: <a href="psi_element://Debug">Debug</a>,<br>&nbsp;&nbsp;&nbsp;&nbsp;V: <a href="psi_element://Display">Display</a>,
    """)

    fun `test expanded signature`() = doTest("""
        use std::fmt::{Debug, Display};

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
        test_package
        pub const unsafe extern "C" fn <b>foo</b>&lt;T, U, V&gt;(x: T, y: u32, z: u64) -&gt; (u32, u64, u64)<br>where<br>&nbsp;&nbsp;&nbsp;&nbsp;T: <a href="psi_element://Clone">Clone</a>,<br>&nbsp;&nbsp;&nbsp;&nbsp;U: <a href="psi_element://Debug">Debug</a>,<br>&nbsp;&nbsp;&nbsp;&nbsp;V: <a href="psi_element://Display">Display</a>,
    """)

    fun `test trait method impl`() = doTest("""
        trait Trait {
            fn foo();
        }
        struct Foo;
        impl Trait for Foo {
            fn foo() {}
              //^
        }
    """, """
        test_package
        impl <a href="psi_element://Trait">Trait</a> for <a href="psi_element://Foo">Foo</a>
        fn <b>foo</b>()
    """)

    fun `test generic trait method impl`() = doTest("""
        trait Trait<T> {
            fn foo();
        }
        struct Foo<T>(T);
        impl<T, F> Trait<T> for Foo<F> {
            fn foo() {}
              //^
        }
    """, """
        test_package
        impl&lt;T, F&gt; <a href="psi_element://Trait">Trait</a>&lt;T&gt; for <a href="psi_element://Foo">Foo</a>&lt;F&gt;
        fn <b>foo</b>()
    """)

    fun `test generic trait method impl with where clause`() = doTest("""
        trait Trait<T> {
            fn foo();
        }
        struct Foo<T>(T);
        impl<T, F> Trait<T> for Foo<F> where T: Ord, F: Into<String> {
            fn foo() {}
              //^
        }
    """, """
        test_package
        impl&lt;T, F&gt; <a href="psi_element://Trait">Trait</a>&lt;T&gt; for <a href="psi_element://Foo">Foo</a>&lt;F&gt;<br>where<br>&nbsp;&nbsp;&nbsp;&nbsp;T: <a href="psi_element://Ord">Ord</a>,<br>&nbsp;&nbsp;&nbsp;&nbsp;F: <a href="psi_element://Into">Into</a>&lt;<a href="psi_element://String">String</a>&gt;,
        fn <b>foo</b>()
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
        test_package
        pub struct <b>Foo</b>
    """)

    fun `test pub struct`() = doTest("""
        pub struct Foo(i32);
                  //^
    """, """
        test_package
        pub struct <b>Foo</b>
    """)

    fun `test generic struct`() = doTest("""
        struct Foo<T>(T);
              //^
    """, """
        test_package
        struct <b>Foo</b>&lt;T&gt;
    """)

    fun `test generic struct with where clause`() = doTest("""
        struct Foo<T>(T) where T: Into<String>;
              //^
    """, """
        test_package
        struct <b>Foo</b>&lt;T&gt;<br>where<br>&nbsp;&nbsp;&nbsp;&nbsp;T: <a href="psi_element://Into">Into</a>&lt;<a href="psi_element://String">String</a>&gt;,
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
        test_package
        enum <b>Foo</b>
    """)

    fun `test pub enum`() = doTest("""
        pub enum Foo {
               //^
            Bar
        }
    """, """
        test_package
        pub enum <b>Foo</b>
    """)

    fun `test generic enum`() = doTest("""
        enum Foo<T> {
            //^
            Bar(T)
        }
    """, """
        test_package
        enum <b>Foo</b>&lt;T&gt;
    """)

    fun `test generic enum with where clause`() = doTest("""
        enum Foo<T> where T: Into<String> {
             //^
            Bar(T)
        }
    """, """
        test_package
        enum <b>Foo</b>&lt;T&gt;<br>where<br>&nbsp;&nbsp;&nbsp;&nbsp;T: <a href="psi_element://Into">Into</a>&lt;<a href="psi_element://String">String</a>&gt;,
    """)

    fun `test enum variant`() = doTest("""
        enum Foo { BAR(bool, u32) };
        use self::Foo::BAR;
                       //^
    """, """
        <b>BAR</b>(bool, u32) [main.rs]
    """)

    fun `test trait`() = doTest("""
        trait Foo { /* no methods */ }
        impl Foo for u32 {}
             //^
    """, """
        test_package
        trait <b>Foo</b>
    """)

    fun `test pub trait`() = doTest("""
        pub trait Foo {
                 //^
        }
    """, """
        test_package
        pub trait <b>Foo</b>
    """)

    fun `test unsafe trait`() = doTest("""
        unsafe trait Foo {
                    //^
        }
    """, """
        test_package
        unsafe trait <b>Foo</b>
    """)

    fun `test generic trait`() = doTest("""
        trait Foo<T> {
             //^
        }
    """, """
        test_package
        trait <b>Foo</b>&lt;T&gt;
    """)

    fun `test generic trait with where clause`() = doTest("""
        trait Foo<T> where T: Into<String> {
             //^
        }
    """, """
        test_package
        trait <b>Foo</b>&lt;T&gt;<br>where<br>&nbsp;&nbsp;&nbsp;&nbsp;T: <a href="psi_element://Into">Into</a>&lt;<a href="psi_element://String">String</a>&gt;,
    """)

    fun `test type alias`() = doTest("""
        type Str = &'static str;
        fn foo(s: Str) {}
                  //^
    """, """
        test_package
        type <b>Str</b> = &amp;&#39;static str
    """)

    fun `test pub type alias`() = doTest("""
        pub type Foo = Result<(), i32>;
                //^
    """, """
        test_package
        pub type <b>Foo</b> = <a href="psi_element://Result">Result</a>&lt;(), i32&gt;
    """)

    fun `test generic type alias`() = doTest("""
        type Foo<T> = Result<T, i32>;
            //^
    """, """
        test_package
        type <b>Foo</b>&lt;T&gt; = <a href="psi_element://Result">Result</a>&lt;T, i32&gt;
    """)

    fun `test generic type alias with where clause`() = doTest("""
        type Foo<T> where T: Into<String> = Result<T, i32>;
             //^
    """, """
        test_package
        type <b>Foo</b>&lt;T&gt; = <a href="psi_element://Result">Result</a>&lt;T, i32&gt;<br>where<br>&nbsp;&nbsp;&nbsp;&nbsp;T: <a href="psi_element://Into">Into</a>&lt;<a href="psi_element://String">String</a>&gt;,
    """)

    fun `test impl assoc type`() = doTest("""
        trait Trait {
            type AssocType;
        }
        struct Foo(i32);
        impl Trait for Foo {
            type AssocType = Option<i32>;
                //^
        }
    """, """
        test_package
        impl <a href="psi_element://Trait">Trait</a> for <a href="psi_element://Foo">Foo</a>
        type <b>AssocType</b> = <a href="psi_element://Option">Option</a>&lt;i32&gt;
    """)

    fun `test generic impl assoc type`() = doTest("""
        trait Trait {
            type AssocType;
        }
        struct Foo<T>(T);
        impl<T> Trait for Foo<T> {
            type AssocType = Option<T>;
                //^
        }
    """, """
        test_package
        impl&lt;T&gt; <a href="psi_element://Trait">Trait</a> for <a href="psi_element://Foo">Foo</a>&lt;T&gt;
        type <b>AssocType</b> = <a href="psi_element://Option">Option</a>&lt;T&gt;
    """)

    fun `test generic impl assoc type with where clause`() = doTest("""
        trait Trait {
            type AssocType;
        }
        struct Foo<T>(T);
        impl<T> Trait for Foo<T> where T: Into<String> {
            type AssocType = Option<T>;
                //^
        }
    """, """
        test_package
        impl&lt;T&gt; <a href="psi_element://Trait">Trait</a> for <a href="psi_element://Foo">Foo</a>&lt;T&gt;<br>where<br>&nbsp;&nbsp;&nbsp;&nbsp;T: <a href="psi_element://Into">Into</a>&lt;<a href="psi_element://String">String</a>&gt;,
        type <b>AssocType</b> = <a href="psi_element://Option">Option</a>&lt;T&gt;
    """)

    fun `test trait assoc type`() = doTest("""
        trait MyTrait {
            /// Documented
            type Awesome;
               //^
        }
    """, """
        test_package::MyTrait
        type <b>Awesome</b>
    """)

    fun `test generic trait assoc type`() = doTest("""
        trait MyTrait<T> {
            /// Documented
            type Awesome;
               //^
        }
    """, """
        test_package::MyTrait&lt;T&gt;
        type <b>Awesome</b>
    """)

    fun `test generic trait assoc type with where clause`() = doTest("""
        trait MyTrait<T> where T: Into<String> {
            /// Documented
            type Awesome;
               //^
        }
    """, """
        test_package::MyTrait&lt;T&gt;
        where<br>&nbsp;&nbsp;&nbsp;&nbsp;T: <a href="psi_element://Into">Into</a>&lt;<a href="psi_element://String">String</a>&gt;,
        type <b>Awesome</b>
    """)

    fun `test macro`() = doTest("""
        /// Documented
        macro_rules! makro {
                   //^
            () => { };
        }

    """, """
        macro_rules! <b>makro</b> [main.rs]
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

    fun `test simple type parameter`() = doTest("""
        fn foo<T>(t: T) {}
                   //^
    """, """
        type parameter <b>T</b>
    """)

    fun `test complex type parameter`() = doTest("""
        use std::borrow::Borrow;
        use std::hash::Hash;

        fn foo<'a, Q, T: 'a + Eq>(t: T) where T: Hash + Borrow<Q> { }
                                   //^
    """, """
        type parameter <b>T</b>: &#39;a + <a href="psi_element://Eq">Eq</a> + <a href="psi_element://Hash">Hash</a> + <a href="psi_element://Borrow">Borrow</a>&lt;Q&gt;
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
        condition binding <b>foo</b>: &amp;T
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
