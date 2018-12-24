/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import org.rust.ProjectDescriptor
import org.rust.WithStdlibAndDependencyRustProjectDescriptor
import org.rust.ide.colors.RsColor

class RsHighlightingAnnotatorTest : RsAnnotatorTestBase(RsHighlightingAnnotator::class.java) {

    override fun setUp() {
        super.setUp()
        registerSeverities(RsColor.values().map(RsColor::testSeverity))
    }

    fun `test attributes`() = checkHighlighting("""
        <ATTRIBUTE>#[cfg_attr(foo)]</ATTRIBUTE>
        fn <FUNCTION>main</FUNCTION>() {
            <ATTRIBUTE>#![crate_type = <STRING>"lib"</STRING>]</ATTRIBUTE>
        }
    """)

    fun `test fields and methods`() = checkHighlighting("""
        struct <STRUCT>T</STRUCT>(<PRIMITIVE_TYPE>i32</PRIMITIVE_TYPE>);
        struct <STRUCT>S</STRUCT>{ <FIELD>field</FIELD>: <STRUCT>T</STRUCT>}
        fn <FUNCTION>main</FUNCTION>() {
            let s = <STRUCT>S</STRUCT>{ <FIELD>field</FIELD>: <STRUCT>T</STRUCT>(92) };
            s.<FIELD>field</FIELD>.0;
        }
    """)

    fun `test functions`() = checkHighlighting("""
        fn <FUNCTION>main</FUNCTION>() {}
        struct <STRUCT>S</STRUCT>;
        impl <STRUCT>S</STRUCT> {
            fn <ASSOC_FUNCTION>foo</ASSOC_FUNCTION>() {}
        }
        trait <TRAIT>T</TRAIT> {
            fn <ASSOC_FUNCTION>foo</ASSOC_FUNCTION>();
            fn <ASSOC_FUNCTION>bar</ASSOC_FUNCTION>() {}
        }
        impl <TRAIT><TRAIT>T</TRAIT></TRAIT> for <STRUCT>S</STRUCT> {
            fn <ASSOC_FUNCTION>foo</ASSOC_FUNCTION>() {}
        }
    """)

    fun `test macro`() = checkHighlighting("""
        fn <FUNCTION>main</FUNCTION>() {
            <MACRO>println</MACRO><MACRO>!</MACRO>["Hello, World!"];
            <MACRO>unreachable</MACRO><MACRO>!</MACRO>();
        }
        <MACRO>macro_rules!</MACRO> foo {
            (x => $ <FUNCTION>e</FUNCTION>:expr) => (println!("mode X: {}", $ <FUNCTION>e</FUNCTION>));
            (y => $ <FUNCTION>e</FUNCTION>:expr) => (println!("mode Y: {}", $ <FUNCTION>e</FUNCTION>));
        }
        impl T {
            <MACRO>foo</MACRO><MACRO>!</MACRO>();
    """)

    fun `test type parameters`() = checkHighlighting("""
        trait <TRAIT>MyTrait</TRAIT> {
            type <TYPE_ALIAS>AssocType</TYPE_ALIAS>;
            fn <METHOD>some_fn</METHOD>(&<SELF_PARAMETER>self</SELF_PARAMETER>);
        }
        struct <STRUCT>MyStruct</STRUCT><<TYPE_PARAMETER>N</TYPE_PARAMETER>: ?<TRAIT>Sized</TRAIT>+<TRAIT>Debug</TRAIT>+<TRAIT>MyTrait</TRAIT>> {
            <FIELD>N</FIELD>: my_field
        }
    """)

    fun `test function arguments`() = checkHighlighting("""
        struct <STRUCT>Foo</STRUCT> {}
        impl <STRUCT>Foo</STRUCT> {
            fn <METHOD>bar</METHOD>(&<SELF_PARAMETER>self</SELF_PARAMETER>, (<PARAMETER>i</PARAMETER>, <PARAMETER>j</PARAMETER>): (<PRIMITIVE_TYPE>i32</PRIMITIVE_TYPE>, <PRIMITIVE_TYPE>i32</PRIMITIVE_TYPE>)) {}
        }
        fn <FUNCTION>baz</FUNCTION>(<PARAMETER>u</PARAMETER>: <PRIMITIVE_TYPE>u32</PRIMITIVE_TYPE>) {}
    """)

    fun `test contextual keywords`() = checkHighlighting("""
        trait <TRAIT>T</TRAIT> {
            fn <ASSOC_FUNCTION>foo</ASSOC_FUNCTION>();
        }
        <KEYWORD>union</KEYWORD> <STRUCT>U</STRUCT> { }
        impl <TRAIT>T</TRAIT> for <STRUCT>U</STRUCT> {
            <KEYWORD>default</KEYWORD> fn <ASSOC_FUNCTION>foo</ASSOC_FUNCTION>() {}
        }
    """)

    fun `test ? operator`() = checkHighlighting("""
        fn <FUNCTION>foo</FUNCTION>() -> Result<<PRIMITIVE_TYPE>i32</PRIMITIVE_TYPE>, ()>{
            Ok(Ok(1)<Q_OPERATOR>?</Q_OPERATOR> * 2)
        }
    """)

    fun `test type alias`() = checkHighlighting("""
        type <TYPE_ALIAS>Bar</TYPE_ALIAS> = <PRIMITIVE_TYPE>u32</PRIMITIVE_TYPE>;
        fn <FUNCTION>main</FUNCTION>() {
            let a: <TYPE_ALIAS>Bar</TYPE_ALIAS> = 10;
        }
    """)

    fun `test self is not over annotated`() = checkHighlighting("""
        pub use self::<MODULE>foo</MODULE>;

        mod <MODULE>foo</MODULE> {
            pub use self::<MODULE>bar</MODULE>;

            pub mod <MODULE>bar</MODULE> {}
        }
    """)

    fun `test primitive`() = checkHighlighting("""
        fn <FUNCTION>main</FUNCTION>() -> <PRIMITIVE_TYPE>bool</PRIMITIVE_TYPE> {
            let a: <PRIMITIVE_TYPE>u8</PRIMITIVE_TYPE> = 42;
            let b: <PRIMITIVE_TYPE>f32</PRIMITIVE_TYPE> = 10.0;
            let c: &<PRIMITIVE_TYPE>str</PRIMITIVE_TYPE> = "example";
            <PRIMITIVE_TYPE>char</PRIMITIVE_TYPE>::is_lowercase('a');
            true
        }
    """)

    @ProjectDescriptor(WithStdlibAndDependencyRustProjectDescriptor::class)
    fun `test crate`() = checkHighlighting("""
        extern crate <CRATE>dep_lib_target</CRATE>;
        
        use <CRATE>std</CRATE>::<MODULE>io</MODULE>::<TRAIT>Read</TRAIT>;
    """)

    fun `test dont touch ast in other files`() = checkDontTouchAstInOtherFiles("""
        //- main.rs
            mod <MODULE>aux</MODULE>;

            fn <FUNCTION>main</FUNCTION>() {
                let _ = <MODULE>aux</MODULE>::<STRUCT>S</STRUCT>;
            }

        //- aux.rs
            pub struct S;
        """
    )
}
