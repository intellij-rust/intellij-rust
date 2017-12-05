/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

class RsHighlightingAnnotatorTest : RsAnnotatorTestBase() {

    fun `test attributes`() = checkInfo("""
        <info>#[cfg_attr(foo)]</info>
        fn <info>main</info>() {
            <info>#![crate_type = <info>"lib"</info>]</info>
        }
    """)

    fun `test fields and methods`() = checkInfo("""
        struct <info>T</info>(<info>i32</info>);
        struct <info>S</info>{ <info>field</info>: <info>T</info>}
        fn <info>main</info>() {
            let s = <info>S</info>{ <info>field</info>: <info>T</info>(92) };
            s.<info>field</info>.0;
        }
    """)

    fun `test functions`() = checkInfo("""
        fn <info>main</info>() {}
        struct <info>S</info>;
        impl <info>S</info> {
            fn <info>foo</info>() {}
        }
        trait <info>T</info> {
            fn <info>foo</info>();
            fn <info>bar</info>() {}
        }
        impl <info>T</info> for <info>S</info> {
            fn <info>foo</info>() {}
        }
    """)

    private val `$` = '$'

    fun `test macro`() = checkInfo("""
        fn <info>main</info>() {
            <info>println!</info>["Hello, World!"];
            <info>unreachable!</info>();
        }
        <info>macro_rules!</info> foo {
            (x => $`$`<info>e</info>:expr) => (println!("mode X: {}", $`$`<info>e</info>));
            (y => $`$`<info>e</info>:expr) => (println!("mode Y: {}", $`$`<info>e</info>));
        }
        impl T {
            <info>foo!</info>();
        }
    """)

    fun `test mut binding`() = checkInfo("""
        fn <info>main</info>() {
            let mut <info>a</info> = 1;
            let b = <info>a</info>;
            let Some(ref mut <info>c</info>) = Some(10);
            let d = <info>c</info>;
        }
    """)

    fun `test type parameters`() = checkInfo("""
        trait <info>MyTrait</info> {
            type <info>AssocType</info>;
            fn <info>some_fn</info>(&<info>self</info>);
        }
        struct <info>MyStruct</info><<info>N</info>: ?<info>Sized</info>+<info>Debug</info>+<info><info>MyTrait</info></info>> {
            <info>N</info>: my_field
        }
    """)

    fun `test function arguments`() = checkInfo("""
        struct <info>Foo</info> {}
        impl <info>Foo</info> {
            fn <info>bar</info>(&<info>self</info>, (<info>i</info>, <info>j</info>): (<info>i32</info>, <info>i32</info>)) {}
        }
        fn <info>baz</info>(<info>u</info>: <info>u32</info>) {}
    """)

    fun `test contextual keywords`() = checkInfo("""
        trait <info>T</info> {
            fn <info>foo</info>();
        }
        <info>union</info> <info>U</info> { }
        impl <info>T</info> for <info>U</info> {
            <info>default</info> fn <info>foo</info>() {}
        }
    """)

    fun `test ? operator`() = checkInfo("""
        fn <info>foo</info>() -> Result<<info>i32</info>, ()>{
            Ok(Ok(1)<info>?</info> * 2)
        }
    """)

    fun `test type alias`() = checkInfo("""
        type <info>Bar</info> = <info>u32</info>;
        fn <info>main</info>() {
            let a: <info>Bar</info> = 10;
        }
    """)

    fun `test self is not over annotated`() = checkInfo("""
        pub use self::<info>foo</info>;

        mod <info>foo</info> {
            pub use self::<info>bar</info>;

            pub mod <info>bar</info> {}
        }
    """)

    fun `test primitive`() = checkInfo("""
        fn <info>main</info>() -> <info>bool</info> {
            let a: <info>u8</info> = 42;
            let b: <info>f32</info> = 10.0;
            let c: &<info>str</info> = "example";
            <info>char</info>::is_lowercase('a');
            true
        }
    """)

    fun `test dont touch ast in other files`() = checkDontTouchAstInOtherFiles("""
        //- main.rs
            mod aux;

            fn <info>main</info>() {
                let _ = aux::<info>S</info>;
            }

        //- aux.rs
            pub struct S;
        """,
        checkInfo = true
    )
}
