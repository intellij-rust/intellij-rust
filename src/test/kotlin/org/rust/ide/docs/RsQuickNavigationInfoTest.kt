package org.rust.ide.docs

import com.intellij.codeInsight.documentation.DocumentationManager

class RsQuickNavigationInfoTest : RsDocumentationProviderTest() {
    override val dataPath = ""

    fun `test nested function`() = doTest("""
        mod a {
            mod b {
                mod c {
                    pub fn double(x: i16) -> i32 {
                        x * 2
                    }
                }
            }

            fn main() {
                b::c::do<caret>uble(2);
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
            <caret>foo()
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
            <caret>foo()
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
            s.<caret>consume();
        }
    """, """
        pub fn <b>consume</b>(self) -&gt; i32 [main.rs]
    """)

    fun `test trait method`() = doTest("""
        trait MyTrait {
            /// Documented
            fn <caret>my_func();
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
            <caret>foo()
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
            <caret>foo()
        }
    """, """
        pub const unsafe extern &quot;C&quot; fn <b>foo</b>&lt;T, U, V&gt;(x: T, y: u32, z: u64) -&gt; (u32, u64, u64) where T: Clone, U: Debug, V: Display [main.rs]
    """)

    fun `test variable 1`() = doTest("""
        fn main() {
            let xy<caret>z = 42;
            do_something(xyz);
        }
    """, """
        let <b>xyz</b> [main.rs]
    """)

    fun `test variable 2`() = doTest("""
        fn main() {
            let mut xyz = 42;
            do_something(x<caret>yz);
        }
    """, """
        let mut <b>xyz</b> [main.rs]
    """)

    fun `test mod`() = doTest("""
        pub mod foo { /* foo code */ }
        use self::fo<caret>o;
    """, """
        pub mod <b>foo</b> [main.rs]
    """)

    fun `test struct`() = doTest("""
        pub struct Foo { a: bool }
        use self::Fo<caret>o;
    """, """
        pub struct <b>Foo</b> [main.rs]
    """)

    fun `test struct with tuple fields`() = doTest("""
        pub struct Foo(a: bool);
        use self::Fo<caret>o;
    """, """
        pub struct <b>Foo</b>(a: bool) [main.rs]
    """)

    fun `test enum`() = doTest("""
        enum Foo { BAR, BAZ };
        use self::Fo<caret>o;
    """, """
        enum <b>Foo</b> [main.rs]
    """)

    fun `test enum variant`() = doTest("""
        enum Foo { BAR(bool, u32) };
        use self::Foo::BA<caret>R;
    """, """
        <b>BAR</b>(bool, u32) [main.rs]
    """)

    fun `test trait`() = doTest("""
        pub trait Foo { /* no methods */ }
        impl Fo<caret>o for u32 {}
    """, """
        pub trait <b>Foo</b> [main.rs]
    """)

    fun `test type alias`() = doTest("""
        type Str = &'static str;
        fn foo(s: St<caret>r) {}
    """, """
        type <b>Str</b> = &amp;&#39;static str [main.rs]
    """)

    fun `test const`() = doTest("""
        pub const FOO: u32 = 42;
        fn foo() { let _ = FO<caret>O; }
    """, """
        pub const <b>FOO</b>: u32 = 42 [main.rs]
    """)

    fun `test static`() = doTest("""
        pub static mut FOO: bool = true;
        fn foo() { let _ = FO<caret>O; }
    """, """
        pub static mut <b>FOO</b>: bool = true [main.rs]
    """)

    fun `test lifetime`() = doTest("""
        type Str<'foo> = &'fo<caret>o str;
    """, """
        <b>'foo</b> [main.rs]
    """)

    fun `test loop label`() = doTest("""
        fn foo() {
            'foo: loop { break 'fo<caret>o }
        }
    """, """
        <b>'foo</b> [main.rs]
    """)

    fun `test self parameter 1`() = doTest("""
        struct Foo;
        impl Foo {
            fn foo(&mut self) { let _ = sel<caret>f; }
        }
    """, """
        &amp;mut <b>self</b> [main.rs]
    """)

    fun `test self parameter 2`() = doTest("""
        struct Foo;
        impl Foo {
            fn foo(self: &mut Foo) { let _ = sel<caret>f; }
        }
    """, """
        <b>self</b>: &amp;mut Foo [main.rs]
    """)

    fun `test value parameter`() = doTest("""
        fn foo(val: &mut u32) { let _ = v<caret>al; }
    """, """
        <b>val</b> [main.rs]
    """)

    private fun doTest(code: String, expected: String) {
        myFixture.configureByText("main.rs", code)
        val originalElement = myFixture.elementAtCaret
        val element = DocumentationManager.getInstance(project).findTargetElement(myFixture.editor, myFixture.file)
        val actual = RsDocumentationProvider().getQuickNavigateInfo(element, originalElement)
        assertSameLines(expected, actual)
    }
}
