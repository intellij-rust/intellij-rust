/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import org.rust.MockAdditionalCfgOptions
import org.rust.MockRustcVersion

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

    fun `test no error empty feature`() = checkByText("""
        #![feature()]
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


    fun `test E0536 valid cfg not pattern`() = checkErrors("""
        #[cfg(not(unix))]
        fn one() {}

        #[cfg(not(all(unix, feature = "magic")))]
        fn two() {}

        #[cfg(not(any(unix, windows)))]
        fn three() {}

        #[cfg(not(feature = "magic"))]
        fn four() {}
    """)


    fun `test E0536 invalid cfg not pattern`() = checkErrors("""
        #[cfg(<error desc="Expected 1 cfg-pattern [E0536]">not()</error>)]
        fn one() {}

        #[cfg(<error desc="Expected 1 cfg-pattern [E0536]">not(unix, windows)</error>)]
        fn two() {}

        #[cfg(<error desc="Expected 1 cfg-pattern [E0536]">not(feature = "magic", unix)</error>)]
        fn three() {}
    """)

    fun `test E0536 quick fix to all`() = checkFixByText("""Convert to `all(not(unix), not(feature = "magic"), not(windows))`""", """
        #[cfg(<error descr="Expected 1 cfg-pattern [E0536]">not(/*caret*/unix, feature = "magic", windows)</error>)]
        fn foo() {}
    """, """
        #[cfg(all(not(unix), not(feature = "magic"), not(windows)))]
        fn foo() {}
    """)

    fun `test E0536 quick fix to all not available for empty not`() = checkFixIsUnavailable("""Convert to `all()`""", """
        #[cfg(<error descr="Expected 1 cfg-pattern [E0536]">not(/*caret*/)</error>)]
        fn foo() {}
    """)

    fun `test E0536 invalid cfg_attr not pattern`() = checkErrors("""
        #[cfg_attr(<error desc="Expected 1 cfg-pattern [E0536]">not()</error>, attr)]
        fn one() {}

        #[cfg_attr(<error desc="Expected 1 cfg-pattern [E0536]">not(unix, windows)</error>, attr)]
        fn two() {}

        #[cfg_attr(<error desc="Expected 1 cfg-pattern [E0536]">not(feature = "magic", unix)</error>, attr)]
        fn three() {}
    """)

    fun `test E0536 cfg_attr quick fix to all`() = checkFixByText("""Convert to `all(not(unix), not(feature = "magic"), not(windows))`""", """
        #[cfg_attr(<error descr="Expected 1 cfg-pattern [E0536]">not(/*caret*/unix, feature = "magic", windows)</error>, attr)]
        fn foo() {}
    """, """
        #[cfg_attr(all(not(unix), not(feature = "magic"), not(windows)), attr)]
        fn foo() {}
    """)

    fun `test E0536 cfg_attr quick fix to all not available for empty not`() = checkFixIsUnavailable("""Convert to `all()`""", """
        #[cfg_attr(<error descr="Expected 1 cfg-pattern [E0536]">not(/*caret*/)</error>, attr)]
        fn foo() {}
    """)

    fun `test no E0537 valid cfg`() = checkErrors("""
        #[cfg(any(foo, bar))]
        #[cfg(all(foo, baz))]
        #[cfg(not(foo))]
        #[cfg(all(not(foo)))]
        #[cfg(not(any(foo)))]
        #[cfg(foo)]
        fn foo() {}
    """)

    fun `test E0537 invalid cfg`() = checkErrors("""
        #[cfg(<error descr="Invalid predicate `an` [E0537]">an</error>(foo))]
        #[cfg(<error descr="Invalid predicate `allx` [E0537]">allx</error>(foo))]
        #[cfg(<error descr="Invalid predicate `non` [E0537]">non</error>(foo))]
        #[cfg(<error descr="Invalid predicate `non` [E0537]">non</error>(an(foo)))]
        #[cfg(all(x, <error descr="Invalid predicate `bar` [E0537]">bar</error>()))]
        #[cfg(all(x, not(y), <error descr="Invalid predicate `baz` [E0537]">baz</error>()))]
        #[cfg(not(<error descr="Invalid predicate `foo` [E0537]">foo</error>()))]
        #[cfg(all(not(any(bar, <error descr="Invalid predicate `baz` [E0537]">baz</error>()))))]
        #[cfg(any(x, not(y), <error descr="Invalid predicate `baz` [E0537]">baz</error>()))]
        fn foo() {}
    """)

    fun `test no E0537 valid cfg_attr`() = checkErrors("""
        #[cfg_attr(any(foo), bar)]
        #[cfg_attr(all(foo), bar)]
        #[cfg_attr(not(foo), bar)]
        #[cfg_attr(all(not(foo)), bar)]
        #[cfg_attr(not(any(foo)), bar)]
        #[cfg_attr(non, bar)]
        fn foo() {}
    """)

    fun `test E0537 invalid cfg_attr`() = checkErrors("""
        #[cfg_attr(<error descr="Invalid predicate `an` [E0537]">an</error>(foo), bar)]
        #[cfg_attr(<error descr="Invalid predicate `allx` [E0537]">allx</error>(foo), bar)]
        #[cfg_attr(<error descr="Invalid predicate `non` [E0537]">non</error>(foo), bar)]
        #[cfg_attr(<error descr="Invalid predicate `non` [E0537]">non</error>(an(foo)), bar)]
        #[cfg_attr(not(<error descr="Invalid predicate `foo` [E0537]">foo</error>()), non())]
        #[cfg_attr(all(not(any(bar, <error descr="Invalid predicate `baz` [E0537]">baz</error>()))), non())]
        #[cfg_attr(all(x, not(y), <error descr="Invalid predicate `baz` [E0537]">baz</error>()), bar)]
        #[cfg_attr(any(x, not(y), <error descr="Invalid predicate `baz` [E0537]">baz</error>()), bar)]
        fn foo() {}
    """)

    fun `test E0537 ignore non-root attributes`() = checkErrors("""
        #[bar(cfg(an(foo)))]
        fn foo() {}
    """)

    fun `test E0537 nested cfg_attr`() = checkErrors("""
        #[cfg_attr(foo, cfg_attr(<error descr="Invalid predicate `an` [E0537]">an</error>(), baz))]
        fn foo() {}
    """)

    fun `test no E0537 cfg version`() = checkErrors("""
        #[cfg(version())]
        fn foo() {}
    """)

    fun `test E0537 quick fix any`() = checkFixByText("Change to `any`", """
        #[cfg(<error descr="Invalid predicate `an` [E0537]">an/*caret*/</error>(foo))]
        fn foo() {}
    """, """
        #[cfg(any(foo))]
        fn foo() {}
    """)

    fun `test E0537 quick fix all`() = checkFixByText("Change to `all`", """
        #[cfg(<error descr="Invalid predicate `allx` [E0537]">allx/*caret*/</error>(foo))]
        fn foo() {}
    """, """
        #[cfg(all(foo))]
        fn foo() {}
    """)

    fun `test E0537 quick fix not`() = checkFixByText("Change to `not`", """
        #[cfg(<error descr="Invalid predicate `noo` [E0537]">noo/*caret*/</error>(foo))]
        fn foo() {}
    """, """
        #[cfg(not(foo))]
        fn foo() {}
    """)

    fun `test E0537 no quick fix high distance`() = checkFixIsUnavailable("Change to", """
        #[cfg(<error descr="Invalid predicate `a` [E0537]">a/*caret*/</error>(foo))]
        fn foo() {}
    """)


    @MockRustcVersion("1.60.0-nightly")
    fun `test feature attribute in nightly channel`() = checkErrors("""
        #![feature(never_type)]

        fn main() {}
    """)

    @MockRustcVersion("1.60.0-beta")
    fun `test feature attribute in beta channel`() = checkErrors("""
        #![<error descr="`#![feature]` may not be used on the beta release channel [E0554]">feature</error>(never_type)]

        fn main() {}
    """)

    @MockRustcVersion("1.60.0")
    fun `test feature attribute in stable channel`() = checkErrors("""
        #![<error descr="`#![feature]` may not be used on the stable release channel [E0554]">feature</error>(never_type)]

        fn main() {}
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    @MockRustcVersion("1.60.0")
    fun `test feature attribute inside cfg_attr`() = checkErrors("""
        #![cfg_attr(intellij_rust, <error descr="`#![feature]` may not be used on the stable release channel [E0554]">feature</error>(never_type))]

        fn main() {}
    """)

    @MockRustcVersion("1.60.0")
    fun `test feature attribute inside cfg_attr that doesn't expand`() = checkErrors("""
        #![cfg_attr(non_existent, feature(never_type))]

        fn main() {}
    """)

    @MockRustcVersion("1.60.0")
    fun `test outer feature attribute`() = checkErrors("""
        #[feature(never_type)]
        fn main() {}
    """)

    @MockRustcVersion("1.60.0")
    fun `test remove feature attribute quick-fix`() = checkFixByText("Remove attribute `feature`", """
        #![<error>/*caret*/feature</error>(never_type)]

        fn main() {}
    """, """
        fn main() {}
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    @MockRustcVersion("1.60.0")
    fun `test no remove feature attribute quick-fix inside cfg_attr`() = checkFixIsUnavailable("Remove attribute `feature`", """
        #![cfg_attr(intellij_rust, <error>/*caret*/feature</error>(never_type))]

        fn main() {}
    """)

    fun `test E0777 derive string literal error`() = checkErrors("""
        #[derive(<error descr="Expected path to a trait, found literal [E0777]">"Clone"</error>)]
        struct A {}

        #[derive(<error descr="Expected path to a trait, found literal [E0777]">"Clone"</error>, Clone, <error descr="Expected path to a trait, found literal [E0777]">"Debug"</error>)]
        struct B {}

        #[derive(<error descr="Expected path to a trait, found literal [E0777]">16</error>)]
        struct C {}

        #[derive(<error descr="Expected path to a trait, found literal [E0777]">"Debug
"</error>)]
        struct D {}
    """)


    fun `test E0777 derive string literal in cfg_attr that doesn't expand`() = checkErrors("""
        #[cfg_attr(non_existent, derive("Clone"))]
        struct A {}
    """)

    fun `test E0777 derive path`() = checkErrors("""
        #[derive(Clone)]
        struct A {}

        #[derive(Clone, Debug)]
        struct B {}
    """)

    fun `test E0777 remove quotes`() = checkFixByText("Remove quotes", """
        #[derive(<error descr="Expected path to a trait, found literal [E0777]">"/*caret*/Clone"</error>)]
        struct A {}
    """, """
        #[derive(Clone)]
        struct A {}
    """)

    fun `test E0777 remove quotes multiple arguments`() = checkFixByText("Remove quotes", """
        #[derive(Debug, <error descr="Expected path to a trait, found literal [E0777]">"/*caret*/Clone"</error>, Copy)]
        struct A {}
    """, """
        #[derive(Debug, Clone, Copy)]
        struct A {}
    """)

    fun `test E0777 remove quotes multiple arguments in a single string`() = checkFixByText("Remove quotes", """
        #[derive(Debug, <error descr="Expected path to a trait, found literal [E0777]">"/*caret*/Clone, PartialEq"</error>, Copy)]
        struct A {}
    """, """
        #[derive(Debug, Clone, PartialEq, Copy)]
        struct A {}
    """)


    fun `test E0777 remove quotes fix not available for non-string literal`() = checkFixIsUnavailable("Remove quotes", """
        #[derive(<error descr="Expected path to a trait, found literal [E0777]">1/*caret*/6</error>)]
        struct A {}
    """)
}
