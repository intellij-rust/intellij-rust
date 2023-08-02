/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import org.rust.*

@SkipTestWrapping // TODO RsAttrErrorAnnotator in macros
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

    fun `test E0589 align with no arguments`() = checkErrors("""
        #[repr(<error descr="`align` needs an argument [E0589]">align</error>)]
        struct S {}
    """)


    fun `test E0589 align with no arguments fix`() = checkFixByText("Add parentheses to `align`", """
        #[repr(<error descr="`align` needs an argument [E0589]">/*caret*/align</error>)]
        struct S {}
    """, """
        #[repr(align())]
        struct S {}
    """)

    fun `test E0589 align not an unsuffixed integer`() = checkErrors("""
        #[repr(<error descr="`align` argument must be an unsuffixed integer [E0589]">align("8")</error>)]
        struct A {}

        #[repr(<error descr="`align` argument must be an unsuffixed integer [E0589]">align("hello world :)")</error>)]
        struct A1 {}

        #[repr(<error descr="`align` argument must be an unsuffixed integer [E0589]">align(8.0)</error>)]
        struct B {}

        #[repr(<error descr="`align` argument must be an unsuffixed integer [E0589]">align(8i64)</error>)]
        struct C {}
    """)

    fun `test E0589 align string literal fix`() = checkFixByText("Change to align(8)", """
        #[repr(<error descr="`align` argument must be an unsuffixed integer [E0589]">/*caret*/align("8")</error>)]
        struct A {}
    """, """
        #[repr(align(8))]
        struct A {}
    """)

    fun `test E0589 align suffixed integer fix`() = checkFixByText("Change to align(8)", """
        #[repr(<error descr="`align` argument must be an unsuffixed integer [E0589]">/*caret*/align(8i64)</error>)]
        struct A {}
    """, """
        #[repr(align(8))]
        struct A {}
    """)

    fun `test E0589 align not a power of two`() = checkErrors("""
        #[repr(<error descr="`align` argument must be a power of two [E0589]">align(7)</error>)]
        struct A {}

        #[repr(<error descr="`align` argument must be a power of two [E0589]">align(0)</error>)]
        struct B {}

        #[repr(<error descr="`align` argument must be a power of two [E0589]">align(1073741825)</error>)]
        struct C {}
    """)


    fun `test E0589 align larger than 2^29`() = checkErrors("""
        #[repr(<error descr="`align` argument must not be larger than 2^29 [E0589]">align(1073741824)</error>)]
        struct A {}

        #[repr(<error descr="`align` argument must not be larger than 2^29 [E0589]">align(4294967296)</error>)]
        struct B {}
    """)


    fun `test E0589 correct align`() = checkErrors("""
        #[repr(align(8))]
        struct A {}

        #[repr(align(1))]
        struct B {}

        #[repr(align(536870912))]
        struct C {}
    """)

    fun `test E0693 align with incorrect representation hint`() = checkErrors("""
        #[repr(<error descr="Incorrect `repr(align)` attribute format [E0693]">align = 8</error>)]
        struct A {}

        #[repr(<error descr="Incorrect `repr(align)` attribute format [E0693]">align = "8"</error>)]
        struct B {}
    """)

    fun `test E0693 align with incorrect number of arguments`() = checkErrors("""
        #[repr(<error descr="`align` takes exactly one argument in parentheses [E0693]">align()</error>)]
        struct A {}

        #[repr(<error descr="`align` takes exactly one argument in parentheses [E0693]">align(8, 4)</error>)]
        struct B {}

        #[repr(<error descr="`align` takes exactly one argument in parentheses [E0693]">align(8, 4, 16)</error>)]
        struct C {}


        #[repr(<error descr="`align` takes exactly one argument in parentheses [E0693]">align(all(8))</error>)]
        struct D {}
    """)

    fun `test E0552 unrecognized repr`() = checkErrors("""
        #[repr(<error descr="Unrecognized representation CD [E0552]">CD</error>)]
        struct Test(i32);
    """)

    fun `test empty enum with repr E0084`() = checkErrors("""
        #[<error descr="Enum with no variants can't have `repr` attribute [E0084]">repr</error>(u8)]
        enum Test {}
    """)

    fun `test enum without body with repr E0084`() = checkErrors("""
        #[repr(u8)] // There should not be a `repr` error when enum doesn't have a body
        enum Test<EOLError descr="<, where or '{' expected"></EOLError>
    """)


    fun `test E0517 placement repr C`() = checkErrors("""
        #[repr(<error descr="C attribute should be applied to struct, enum, or union [E0517]">C</error>)]
        type Test = i32;

        #[repr(C)]
        struct Test1(i32);

        #[repr(C)]
        enum Test2 { AA }
    """)

    fun `test E0517 placement repr transparent`() = checkErrors("""
        #[repr(<error descr="transparent attribute should be applied to struct, enum, or union [E0517]">transparent</error>)]
        type Test = i32;

        #[repr(transparent)]
        struct Test1(i32);

        #[repr(transparent)]
        enum Test2 { AA }
    """)

    fun `test E0517 placement repr align`() = checkErrors("""
        #[repr(<error descr="align attribute should be applied to struct, enum, or union [E0517]">align(2)</error>)]
        type Test = i32;

        #[repr(align(2))]
        struct Test1(i32);

        #[repr(align(2))]
        enum Test2 { AA }
    """)

    fun `test E0517 placement repr primitive representations`() = checkErrors("""
        #[repr(<error descr="u32 attribute should be applied to enum [E0517]">u32</error>)]
        type Test = i32;

        #[repr(<error descr="i32 attribute should be applied to enum [E0517]">i32</error>)]
        struct Test1(i32);

        #[repr(isize)]
        enum Test2 { AA }
    """)

    fun `test E0517 placement packed`() = checkErrors("""
        #[repr(<error descr="packed attribute should be applied to struct or union [E0517]">packed</error>)]
        type Test = i32;

        #[repr(packed)]
        struct Test1(i32);

        #[repr(<error descr="packed attribute should be applied to struct or union [E0517]">packed</error>)]
        enum Test2 { AA }
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test E0517 custom repr proc macro attr`() = checkByFileTree("""
    //- dep-proc-macro/lib.rs
        use proc_macro::TokenStream;

        #[proc_macro_attribute]
        pub fn repr(attr: TokenStream, item: TokenStream) -> TokenStream {
            item
        }
    //- main.rs
        extern crate dep_proc_macro;

        use dep_proc_macro::repr;

        #[repr/*caret*/(<error descr="C attribute should be applied to struct, enum, or union [E0517]">C</error>)]
        type Foo = i32;
    """)
}
