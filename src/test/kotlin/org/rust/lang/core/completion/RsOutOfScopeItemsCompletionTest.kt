/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import org.intellij.lang.annotations.Language
import org.rust.ProjectDescriptor
import org.rust.WithDependencyRustProjectDescriptor
import org.rust.hasCaretMarker
import org.rust.ide.settings.RsCodeInsightSettings
import org.rust.openapiext.Testmark

class RsOutOfScopeItemsCompletionTest : RsCompletionTestBase() {

    fun `test suggest an non-imported symbol and add proper import`() = doTestByText("""
        mod collections {
            pub struct BTreeMap;
        }

        fn main() {
            let _ = BTreeM/*caret*/
        }
    """, """
        use crate::collections::BTreeMap;

        mod collections {
            pub struct BTreeMap;
        }

        fn main() {
            let _ = BTreeMap/*caret*/
        }
    """)

    fun `test doesn't suggest a symbol that already in scope`() = doTestByText("""
        use collections::BTreeMap;

        mod collections {
            pub struct BTreeMap;
        }

        fn main() {
            let _ = BTreeM/*caret*/
        }
    """, """
        use collections::BTreeMap;

        mod collections {
            pub struct BTreeMap;
        }

        fn main() {
            let _ = BTreeMap/*caret*/
        }
    """)

    fun `test doesn't suggest a symbol that leads to name collision`() = doTestByText("""
        struct BTreeMap;

        mod collections {
            pub struct BTreeMap;
        }

        fn main() {
            let _ = BTreeM/*caret*/
        }
    """, """
        struct BTreeMap;

        mod collections {
            pub struct BTreeMap;
        }

        fn main() {
            let _ = BTreeMap/*caret*/
        }
    """)

    fun `test suggest a symbol with same name as in scope but in different namespace`() = doTestByText("""
        fn foo() {}
        mod inner {
            pub struct foo {}
        }
        fn test(x: fo/*caret*/) {}
    """, """
        use crate::inner::foo;

        fn foo() {}
        mod inner {
            pub struct foo {}
        }
        fn test(x: foo/*caret*/) {}
    """)

    fun `test same item as in scope but with different name`() = doTestByText("""
        use crate::mod1::foo as bar;
        mod mod1 {
            pub fn foo() {}
        }
        fn main() {
            fo/*caret*/
        }
    """, """
        use crate::mod1::{foo as bar, foo};
        mod mod1 {
            pub fn foo() {}
        }
        fn main() {
            foo()/*caret*/
        }
    """)

    fun `test doesn't suggest an non-imported symbol when setting disabled`() = doTestByText("""
        struct BTreeMap;

        mod collections {
            pub struct BTreeMap;
            pub struct BTreeSet;
        }

        fn main() {
            let _ = BTree/*caret*/
        }
    """, """
        struct BTreeMap;

        mod collections {
            pub struct BTreeMap;
            pub struct BTreeSet;
        }

        fn main() {
            let _ = BTreeMap/*caret*/
        }
    """, suggestOutOfScopeItems = false)

    fun `test suggest non-imported symbols for empty path`() = doTestContainsCompletion("BTreeMap", """
        mod collections {
            pub struct BTreeMap;
        }
        fn main() {
            let _ = /*caret*/;
        }
    """)

    fun `test suggest non-imported symbols for empty path in macro bodies`() = doTestContainsCompletion("BTreeMap", """
        mod collections {
            pub struct BTreeMap;
        }
        macro_rules! foo {
            ($($ i:item)*) => { $($ i)* };
        }
        foo! {
            fn main() {
                let _ = /*caret*/
            }
        }
    """)

    fun `test enum completion`() = doTestByText("""
        mod a {
            pub enum Enum {
                V1, V2
            }
        }

        fn main() {
            let a = Enu/*caret*/
        }
    """, """
        use crate::a::Enum;

        mod a {
            pub enum Enum {
                V1, V2
            }
        }

        fn main() {
            let a = Enum/*caret*/
        }
    """)

    fun `test enum variant completion`() = doTestByText("""
        enum Enum { V1 }

        fn main() {
            let a = V/*caret*/
        }
    """, """
        use crate::Enum::V1;

        enum Enum { V1 }

        fn main() {
            let a = V1/*caret*/
        }
    """)

    fun `test completion inside inline module`() = doTestByText("""
        mod foo {
            pub struct Bar;
        }
        mod baz {
            fn x(x: Ba/*caret*/) {}
        }
    """, """
        mod foo {
            pub struct Bar;
        }
        mod baz {
            use crate::foo::Bar;

            fn x(x: Bar/*caret*/) {}
        }
    """)

    fun `test completion inside pub inline module`() = doTestByText("""
        mod foo {
            pub struct Bar;
        }
        pub mod baz {
            fn x(x: Ba/*caret*/) {}
        }
    """, """
        mod foo {
            pub struct Bar;
        }
        pub mod baz {
            use crate::foo::Bar;

            fn x(x: Bar/*caret*/) {}
        }
    """)

    fun `test insert handler`() = doTestByText("""
        mod foo {
            pub fn bar(x: i32) {}
        }

        fn main() {
            ba/*caret*/
        }
    """, """
        use crate::foo::bar;

        mod foo {
            pub fn bar(x: i32) {}
        }

        fn main() {
            bar(/*caret*/)
        }
    """)

    fun `test insert handler for multiple carets`() = doTestByText("""
        mod foo {
            pub fn bar(x: i32) {}
        }

        fn main() {
            ba/*caret*/
            ba/*caret*/
            ba/*caret*/
        }
    """, """
        use crate::foo::bar;

        mod foo {
            pub fn bar(x: i32) {}
        }

        fn main() {
            bar(/*caret*/)
            bar(/*caret*/)
            bar(/*caret*/)
        }
    """)

    fun `test do not import out of scope items when setting disabled`() = doTestByText("""
        mod collections {
            pub struct BTreeMap;
        }

        fn main() {
            let _ = BTreeM/*caret*/
        }
    """, """
        mod collections {
            pub struct BTreeMap;
        }

        fn main() {
            let _ = BTreeMap/*caret*/
        }
    """, importOutOfScopeItems = false)

    fun `test macro body`() = doTestByText("""
        mod foo { pub struct Foo; }
        macro_rules! foo {
            ($($ i:item)*) => { $($ i)* };
        }
        foo! {
            fn bar() {
                F/*caret*/
            }
        }
    """, """
        use crate::foo::Foo;

        mod foo { pub struct Foo; }
        macro_rules! foo {
            ($($ i:item)*) => { $($ i)* };
        }
        foo! {
            fn bar() {
                Foo/*caret*/
            }
        }
    """)

    fun `test no completion in macro body if expands to a module`() = checkNoCompletion("""
        mod foo { pub struct Foo; }
        macro_rules! foo {
            ($($ i:item)*) => { $($ i)* };
        }
        foo! {
            mod bar {
                fn bar() {
                    F/*caret*/
                }
            }
        }
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test pub extern crate`() = doTestByFileTree("""
        //- trans-lib/lib.rs
        pub struct FooBar;
        //- dep-lib/lib.rs
        pub extern crate trans_lib;
        //- lib.rs
        extern crate dep_lib_target;

        fn foo(x: FooB/*caret*/) {}
    """, """
        extern crate dep_lib_target;

        use dep_lib_target::trans_lib::FooBar;

        fn foo(x: FooBar/*caret*/) {}
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test show all re-exports of single item`() {
        withOutOfScopeSettings {
            checkContainsCompletionByFileTree(listOf(
                "Bar (dep_lib_target::Bar)",
                "Bar (dep_lib_target_2::Bar)"
            ), """
                //- trans-common-lib/lib.rs
                pub struct Bar;
                //- dep-lib/lib.rs
                pub use trans_common_lib::Bar;
                //- dep-lib-2/lib.rs
                pub use trans_common_lib::Bar;
                //- lib.rs
                fn foo(x: Ba/*caret*/) {}
            """) {
                val presentation = presentation
                "${presentation.itemText}${presentation.tailText}"
            }
        }
    }

    fun `test macro`() = doTestByFileTree("""
    //- lib.rs
        #[macro_export]
        macro_rules! foo_macro { () => {} }
    //- main.rs
        fn main() {
            foo_m/*caret*/
        }
    """, """
        use test_package::foo_macro;

        fn main() {
            foo_macro!()
        }
    """)

    fun `test macro 2`() = doTestByFileTree("""
    //- lib.rs
        #[macro_export]
        pub macro foo_macro() {}
    //- main.rs
        fn main() {
            foo_m/*caret*/
        }
    """, """
        use test_package::foo_macro;

        fn main() {
            foo_macro!()
        }
    """)

    // e.g. `lazy_static`
    fun `test macro with same name as dependency`() = doTestByFileTree("""
    //- lib.rs
        #[macro_export]
        macro_rules! test_package { () => {} }
    //- main.rs
        test_p/*caret*/
    """, """
        use test_package::test_package;
        test_package!()
    """)

    fun `test macro as type reference`() = doTestByFileTree("""
    //- lib.rs
        #[macro_export]
        macro_rules! foo_macro { () => { i32 } }
    //- main.rs
        fn func(x: foo_m/*caret*/) {}
    """, """
        use test_package::foo_macro;

        fn func(x: foo_macro!()) {}
    """)

    fun `test suggest a non-imported symbol and add proper import for first segment of the path`() = doTestByText("""
        mod collections {
            pub struct BTreeMap;
            impl BTreeMap { pub fn new() -> BTreeMap { todo!() } }
        }

        fn main() {
            let _ = BTreeMap::n/*caret*/
        }
    """, """
        use crate::collections::BTreeMap;

        mod collections {
            pub struct BTreeMap;
            impl BTreeMap { pub fn new() -> BTreeMap { todo!() } }
        }

        fn main() {
            let _ = BTreeMap::new()/*caret*/
        }
    """)

    fun `test suggest a non-imported symbol and add proper import for first segment of the path 2`() = doTestContainsCompletion("new", """
        mod collections {
            pub struct BTreeMap;
            impl BTreeMap { pub fn new() -> BTreeMap { todo!() } }
        }
        mod reexports {
            pub use crate::collections::BTreeMap;
        }

        fn main() {
            let _ = BTreeMap::n/*caret*/
        }
    """)

    fun `test suggest a non-imported symbol for first segment of the path if the import option is disabled`() = doTestByText("""
        mod collections {
            pub struct BTreeMap;
            impl BTreeMap { pub fn new() -> BTreeMap { todo!() } }
        }

        fn main() {
            let _ = BTreeMap::n/*caret*/
        }
    """, """
        mod collections {
            pub struct BTreeMap;
            impl BTreeMap { pub fn new() -> BTreeMap { todo!() } }
        }

        fn main() {
            let _ = BTreeMap::new()/*caret*/
        }
    """, importOutOfScopeItems = false)

    fun `test don't suggest a non-imported symbol for first segment of the path when the option disabled`() = doTestNoCompletion("""
        mod collections {
            pub struct BTreeMap;
            impl BTreeMap { pub fn new() -> BTreeMap { todo!() } }
        }

        fn main() {
            let _ = BTreeMap::n/*caret*/
        }
    """, suggestOutOfScopeItems = false)

    fun `test suggest a non-imported symbol and add proper import for first segment of the path (mod)`() = doTestByText("""
        mod foo {
            pub mod bar {
                pub struct Baz;
            }
        }

        fn main() {
            let _ = bar::B/*caret*/
        }
    """, """
        use crate::foo::bar;

        mod foo {
            pub mod bar {
                pub struct Baz;
            }
        }

        fn main() {
            let _ = bar::Baz/*caret*/
        }
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test attribute proc macro in attribute`() = doSingleCompletionByFileTree("""
    //- dep-proc-macro/lib.rs
        #[proc_macro_attribute]
        pub fn attr_as_is(_attr: TokenStream, item: TokenStream) -> TokenStream { item }
    //- lib.rs
        #[attr_as_/*caret*/]
        fn func() {}
    """, """
        use dep_proc_macro::attr_as_is;

        #[attr_as_is/*caret*/]
        fn func() {}
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test no function like proc macro in attribute`() = checkNoCompletionByFileTree("""
    //- dep-proc-macro/lib.rs
        #[proc_macro]
        pub fn function_like_as_is(input: TokenStream) -> TokenStream { return input; }
    //- lib.rs
        #[function_like_/*caret*/]
        fn func() {}
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test no attr proc macro inside block`() = checkNoCompletionByFileTree("""
    //- dep-proc-macro/lib.rs
        #[proc_macro_attribute]
        pub fn macro_attr(_attr: TokenStream, item: TokenStream) -> TokenStream { item }
        #[proc_macro_derive(macro_derive)]
        pub fn macro_derive(_item: TokenStream) -> TokenStream { "".parse().unwrap() }
    //- lib.rs
        fn main() {
            macro_/*caret*/
        }
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test no attr proc macro at top level`() = checkNoCompletionByFileTree("""
    //- dep-proc-macro/lib.rs
        #[proc_macro_attribute]
        pub fn macro_attr(_attr: TokenStream, item: TokenStream) -> TokenStream { item }
        #[proc_macro_derive(macro_derive)]
        pub fn macro_derive(_item: TokenStream) -> TokenStream { "".parse().unwrap() }
    //- lib.rs
        macro_/*caret*/
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test no completion of method from transitive dependency`() = checkNoCompletionByFileTree("""
    //- trans-lib/lib.rs
        pub trait Trait {
            fn method(&self) {}
        }
        impl<T> Trait for T {}
    //- dep-lib/lib.rs
    //- lib.rs
        struct Foo {}
        fn func(foo: &Foo) {
            foo.me/*caret*/
        }
    """)

    fun `test no completion of method from private trait`() = doTestNoCompletion("""
        mod mod1 {
            mod mod2 {
                pub trait Trait {
                    fn method(&self) {}
                }
                impl<T> Trait for T {}
            }
        }
        struct Foo {}
        fn func(foo: &Foo) {
            foo.me/*caret*/
        }
    """)

    fun `test complete method from local trait`() = doTestByText("""
        struct Foo {}
        fn func(foo: &Foo) {
            trait Trait {
                fn method(&self) {}
            }
            impl<T> Trait for T {}
            foo.me/*caret*/
        }
    """, """
        struct Foo {}
        fn func(foo: &Foo) {
            trait Trait {
                fn method(&self) {}
            }
            impl<T> Trait for T {}
            foo.method()/*caret*/
        }
    """)

    private fun doTestByText(
        @Language("Rust") before: String,
        @Language("Rust") after: String,
        suggestOutOfScopeItems: Boolean = true,
        importOutOfScopeItems: Boolean = true
    ) = doTest(before, after, suggestOutOfScopeItems, importOutOfScopeItems, ::doSingleCompletion)

    private fun doTestByFileTree(
        @Language("Rust") before: String,
        @Language("Rust") after: String,
        suggestOutOfScopeItems: Boolean = true,
        importOutOfScopeItems: Boolean = true
    ) = doTest(before, after, suggestOutOfScopeItems, importOutOfScopeItems, ::doSingleCompletionByFileTree)

    private fun doTest(
        @Language("Rust") before: String,
        @Language("Rust") after: String,
        suggestOutOfScopeItems: Boolean = true,
        importOutOfScopeItems: Boolean = true,
        check: (String, String) -> Unit
    ) = withOutOfScopeSettings(suggestOutOfScopeItems, importOutOfScopeItems) { check(before, after) }

    private fun doTestContainsCompletion(
        variant: String,
        @Language("Rust") code: String,
        suggestOutOfScopeItems: Boolean = true,
        importOutOfScopeItems: Boolean = true
    ) = withOutOfScopeSettings(suggestOutOfScopeItems, importOutOfScopeItems) { checkContainsCompletion(variant, code) }

    private fun doTestNoCompletion(
        @Language("Rust") code: String,
        suggestOutOfScopeItems: Boolean = true,
        importOutOfScopeItems: Boolean = true
    ) = withOutOfScopeSettings(suggestOutOfScopeItems, importOutOfScopeItems) { checkNoCompletion(code) }

    private fun withOutOfScopeSettings(
        suggestOutOfScopeItems: Boolean = true,
        importOutOfScopeItems: Boolean = true,
        action: () -> Unit
    ) {
        val settings = RsCodeInsightSettings.getInstance()
        val suggestInitialValue = settings.suggestOutOfScopeItems
        val importInitialValue = settings.importOutOfScopeItems
        settings.suggestOutOfScopeItems = suggestOutOfScopeItems
        settings.importOutOfScopeItems = importOutOfScopeItems
        try {
            action()
        } finally {
            settings.suggestOutOfScopeItems = suggestInitialValue
            settings.importOutOfScopeItems = importInitialValue
        }
    }

    private fun doTest(@Language("Rust") text: String, testmark: Testmark) {
        check(hasCaretMarker(text)) {
            "Please add `/*caret*/` marker"
        }
        myFixture.configureByText("main.rs", replaceCaretMarker(text))
        testmark.checkNotHit {
            myFixture.completeBasicAllCarets(null)
        }
    }
}
