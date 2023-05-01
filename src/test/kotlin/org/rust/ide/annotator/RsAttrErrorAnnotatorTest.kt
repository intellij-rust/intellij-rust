/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

class RsAttrErrorAnnotatorTest : RsAnnotatorTestBase(RsAttrErrorAnnotator::class) {

    fun `test attributes wrong delimiter`() = checkByText("""
        #![crate_type = foo!()]

        #[allow <error descr="Wrong meta list delimiters">{ foo_lint }</error> ]
        fn delim_brace() {}

        #[allow <error descr="Wrong meta list delimiters">[ foo_lint ]</error> ]
        fn delim_bracket() {}

        #[cfg_attr(unix, allow { foo_lint })]
        fn cfg() {}

        #[cfg_attr(unix, cfg_attr(unix, foo{bar}))]
        fn nested_cfg() {}
    """)

    fun `test remove attributes wrong delimiter`() = checkFixByText("Replace brackets", """
        #[allow <error descr="Wrong meta list delimiters">{/*caret*/ foo_lint }</error> ]
        fn delim_brace() {}
    ""","""
        #[allow ( foo_lint ) ]
        fn delim_brace() {}
    """)

    fun `test any delimiter is allowed for custom attributes`() = checkByText("""
        #[attr_macro_foo{foo_meta}]
        fn delim_brace() {}

        #[attr_macro_foo[foo_meta]]
        fn delim_bracket() {}

        #[cfg_attr(unix, attr_macro_foo{foo_meta})]
        fn cfg() {}

        #[cfg_attr(unix, cfg_attr(unix, attr_macro_foo{foo_meta}))]
        fn nested_cfg() {}
    """)

    fun `test attributes template compatibility`() = checkByText("""
        #![<error descr="Malformed `recursion_limit` attribute input">recursion_limit</error>]

        #[<error descr="Malformed `rustc_lint_query_instability` attribute input">rustc_lint_query_instability(a)</error>]
        fn f1() {}

        #[<error descr="Malformed `link_name` attribute input">link_name</error>]
        extern "C" {
            fn bar() -> u32;
        }

        #[<error descr="Malformed `marker` attribute input">marker(always)</error>]
        trait Marker1 {}

        #[<error descr="Malformed `marker` attribute input">marker("never")</error>]
        trait Marker2 {}

        #[<error descr="Malformed `marker` attribute input">marker(key = "value")</error>]
        trait Marker3 {}

        trait Foo {}

        #[<error descr="Malformed `rustc_on_unimplemented` attribute input">rustc_on_unimplemented</error>]
        impl Foo for u32 {}

        #[<error descr="Malformed `track_caller` attribute input">track_caller(1)</error>]
        fn f() {}

        #[<error descr="Malformed `rustc_must_implement_one_of` attribute input">rustc_must_implement_one_of</error>]
        trait Tr3 {}
    """)

    fun `test literal with suffixes in attrs are not allowed`() = checkByText("""
        #[<error descr="Multiple 'rustc_legacy_const_generics' attributes">rustc_legacy_const_generics(<error descr="Suffixed literals are not allowed in attributes">1usize</error>)</error>]
        #[rustc_legacy_const_generics(<error descr="Suffixed literals are not allowed in attributes">1u8</error>)]
        #[rustc_legacy_const_generics(<error descr="Suffixed literals are not allowed in attributes">1u16</error>)]
        #[rustc_legacy_const_generics(<error descr="Suffixed literals are not allowed in attributes">1u32</error>)]
        #[rustc_legacy_const_generics(<error descr="Suffixed literals are not allowed in attributes">1u64</error>)]
        #[rustc_legacy_const_generics(<error descr="Suffixed literals are not allowed in attributes">1isize</error>)]
        #[rustc_legacy_const_generics(<error descr="Suffixed literals are not allowed in attributes">1i8</error>)]
        #[rustc_legacy_const_generics(<error descr="Suffixed literals are not allowed in attributes">1i16</error>)]
        #[rustc_legacy_const_generics(<error descr="Suffixed literals are not allowed in attributes">1i32</error>)]
        #[rustc_legacy_const_generics(<error descr="Suffixed literals are not allowed in attributes">1i64</error>)]
        #[rustc_legacy_const_generics(<error descr="Suffixed literals are not allowed in attributes">1.0f32</error>)]
        #[rustc_legacy_const_generics(<error descr="Suffixed literals are not allowed in attributes">1.0f64</error>)]
        fn bar<const X: usize>() {}
    """)

    fun `test remove suffix from literal in attribute macro`() = checkFixByText("Remove suffix", """
        #[rustc_legacy_const_generics(<error descr="Suffixed literals are not allowed in attributes">1.0f64/*caret*/</error>)]
        fn bar<const X: usize>() {}
    """, """
        #[rustc_legacy_const_generics(1.0)]
        fn bar<const X: usize>() {}
    """)

    fun `test literal with suffixes in attrs are allowed in attr macros`() = checkByText("""
        #[attr_macro_foo(1usize)]
        #[attr_macro_foo(1u8)]
        #[attr_macro_foo(1u16)]
        #[attr_macro_foo(1u32)]
        #[attr_macro_foo(1u64)]
        #[attr_macro_foo(1isize)]
        #[attr_macro_foo(1i8)]
        #[attr_macro_foo(1i16)]
        #[attr_macro_foo(1i32)]
        #[attr_macro_foo(1i64)]
        #[attr_macro_foo(1.0f32)]
        #[attr_macro_foo(1.0f64)]
        fn bar<const X: usize>() {}
    """)

    fun `test duplicates`() = checkByText("""
        #[<warning descr="Unused attribute">ignore</warning>]
        #[ignore]
        fn foo() {}


        #[<warning descr="Unused attribute">macro_use</warning>]
        #[macro_use(lazy_static)]
        #[macro_use]
        extern crate a;

        #[<error descr="Multiple 'proc_macro' attributes">proc_macro</error>]
        #[proc_macro]
        pub fn make_answer(_item: TokenStream) -> TokenStream {
            "fn answer() -> u32 { 42 }".parse().unwrap()
        }

        #[instruction_set(arm::a32)]
        #[<error descr="Multiple 'instruction_set' attributes">instruction_set(arm::a32)</error>]
        fn foo_arm_code() {}

        #[<warning descr="Unused attribute">should_panic(expected = "values don't match")</warning>]
        #[should_panic(expected = "values don't match")]
        fn mytest() {
            assert_eq!(1, 2, "values don't match");
        }

        extern {
            #[link_name = "actual_symbol_name"]
            #[<warning descr="Unused attribute">link_name = "actual_symbol_name"</warning>]
            fn name_in_rust();
        }


        #[cfg_attr(unix, cfg_attr(unix, <warning descr="Unused attribute">macro_use</warning>))]
        #[cfg_attr(unix, cfg_attr(unix, macro_use))]
        fn nested_cfg() {}
    """)

    fun `test no error for cfg feature`() = checkByText("""
        #[cfg(feature = "foo")]
        fn bar() {}
    """)

    fun `test duplicates remove first`() = checkFixByText("Remove `#[instruction_set(arm::a32)]`", """
        #[instruction_set(arm::a32)]
        #[<error descr="Multiple 'instruction_set' attributes">instruction_set(arm::a32)/*caret*/</error>]
        fn foo_arm_code() {}
    """, """
        #[instruction_set(arm::a32)]
        fn foo_arm_code() {}
    """)

    fun `test duplicates remove last`() = checkFixByText("Remove `#[ignore]`", """
        #[<warning descr="Unused attribute">ignore/*caret*/</warning>]
        #[ignore]
        fn foo() {}
    """, """
        #[ignore]
        fn foo() {}
    """)

    fun `test deprecated attr E0538`() = checkByText("""
        #[deprecated]
        fn foo() {}

        #[deprecated = "hello world"]
        fn bar() {}

        #[deprecated(
            since = "a",
            <error descr="Multiple 'since' items [E0538]">since = "b"</error>,
            note = "c"
        )]
        fn f1() { }

        #[deprecated(
            since="1.0.0",
            note="First deprecation note.",
            <error descr="Multiple 'note' items [E0538]">note="Second deprecation note."</error>
        )]
        fn f2() {}
    """)

    fun `test deprecated attr E0541`() = checkByText("""
        #[deprecated(
            since="1.0.0",
            suggestion="42"
        )]
        fn f1() {}

        #[deprecated(
            since="1.0.0",
            <error descr="Unknown meta item 'hello' [E0541]">hello="world"</error>
        )]
        fn f2() {}
    """)

    fun `test deprecated attr E0551`() = checkByText("""
        #[deprecated(<error descr="Incorrect meta item [E0551]">since(b)</error>, note = "a")]
        fn f1() {}

        #[deprecated(<error descr="Incorrect meta item [E0551]">since</error>, note = "a")]
        fn f2() {}
    """)
}
