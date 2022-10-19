/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import org.rust.ProjectDescriptor
import org.rust.WithDependencyRustProjectDescriptor

class RsAttributeCompletionTest : RsAttributeCompletionTestBase() {
    fun `test derive on struct`() = doSingleAttributeCompletion("""
        #[der/*caret*/]
        struct Bar;
    """, """
        #[derive(/*caret*/)]
        struct Bar;
    """)

    fun `test warn on trait`() = doSingleAttributeCompletion("""
        #[war/*caret*/]
        trait Bar {}
    """, """
        #[warn(/*caret*/)]
        trait Bar {}
    """)

    fun `test inline on fn`() = doSingleAttributeCompletion("""
        #[inl/*caret*/]
        fn foo() {}
    """, """
        #[inline/*caret*/]
        fn foo() {}
    """)

    fun `test outer allow on fn`() = doSingleAttributeCompletion("""
        #[allo/*caret*/]
        fn foo() {}
    """, """
        #[allow(/*caret*/)]
        fn foo() {}
    """)

    fun `test inner allow on fn`() = doSingleAttributeCompletion("""
        fn foo() {
            #![allo/*caret*/]
        }
    """, """
        fn foo() {
            #![allow(/*caret*/)]
        }
    """)

    fun `test simd on tuple struct`() = doSingleAttributeCompletion("""
        #[si/*caret*/]
        struct Bar(u8, u8);
    """, """
        #[simd/*caret*/]
        struct Bar(u8, u8);
    """)

    fun `test forbid on static`() = doSingleAttributeCompletion("""
        #[forb/*caret*/]
        static BAR: u8 = 1;
    """, """
        #[forbid(/*caret*/)]
        static BAR: u8 = 1;
    """)

    fun `test thread local on static mut`() = doSingleAttributeCompletion("""
        #[thre/*caret*/]
        static mut BAR: u8 = 1;
    """, """
        #[thread_local/*caret*/]
        static mut BAR: u8 = 1;
    """)

    fun `test deny on enum`() = doSingleAttributeCompletion("""
        #[den/*caret*/]
        enum Foo {
            BAR,
            BAZ
        }
    """, """
        #[deny(/*caret*/)]
        enum Foo {
            BAR,
            BAZ
        }
    """)

    fun `test no mangle on enum`() = doSingleAttributeCompletion("""
        #[no_ma/*caret*/]
        mod foo {}
    """, """
        #[no_mangle/*caret*/]
        mod foo {}
    """)

    fun `test outer deny on file`() = doSingleAttributeCompletion("""
        #![den/*caret*/]
    """, """
        #![deny(/*caret*/)]
    """)

    fun `test macro use on mod`() = doSingleAttributeCompletion("""
        #[macr/*caret*/]
        mod foo {}
    """, """
        #[macro_use/*caret*/]
        mod foo {}
    """)

    fun `test macro use on mod 2`() = doSingleAttributeCompletion("""
        #[macr/*caret*/]
        mod foo;
    """, """
        #[macro_use/*caret*/]
        mod foo;
    """)

    fun `test outer warn on mod`() = doSingleAttributeCompletion("""
        mod foo {
            #![war/*caret*/]
        }
    """, """
        mod foo {
            #![warn(/*caret*/)]
        }
    """)

    fun `test export name on trait impl method`() = doSingleAttributeCompletion("""
        struct HasDrop;
        impl Drop for HasDrop {
            #[allo/*caret*/]
            fn drop(&mut self) {}
        }
    """, """
        struct HasDrop;
        impl Drop for HasDrop {
            #[allow(/*caret*/)]
            fn drop(&mut self) {}
        }
    """)

    fun `test linked from on extern block`() = doSingleAttributeCompletion("""
        #[linke/*caret*/]
        extern {
            fn bar(baz: size_t) -> size_t;
        }
    """, """
        #[linked_from/*caret*/]
        extern {
            fn bar(baz: size_t) -> size_t;
        }
    """)

    fun `test linkage on extern block decl`() = doSingleAttributeCompletion("""
        extern {
            #[linka/*caret*/]
            fn bar(baz: size_t) -> size_t;
        }
    """, """
        extern {
            #[linkage/*caret*/]
            fn bar(baz: size_t) -> size_t;
        }
    """)

    fun `test no link on extern crate`() = doSingleAttributeCompletion("""
        #[no_l/*caret*/]
        extern crate bar;
    """, """
        #[no_link/*caret*/]
        extern crate bar;
    """)

    fun `test macro export on macro`() = doSingleAttributeCompletion("""
        #[macr/*caret*/]
        macro_rules! bar {}
    """, """
        #[macro_export/*caret*/]
        macro_rules! bar {}
    """)

    fun `test cfg on mod`() = checkContainsCompletion("cfg", """
        #[cf/*caret*/]
        mod foo {}
    """)

    fun `test cfg on file inner`() = checkContainsCompletion("cfg", """
        #![cf/*caret*/]
    """)

    fun `test deprecated`() = doSingleAttributeCompletion("""
        #[dep/*caret*/]
        mod foo {}
    """, """
        #[deprecated/*caret*/]
        mod foo {}
    """)

    fun `test do not complete existing attributes`() = checkNoCompletion("""
        #[deprecated]
        #[dep/*caret*/]
        mod foo {}
    """)

    fun `test repr completion on enum`() = doSingleAttributeCompletion("""
        #[rep/*caret*/]
        enum Foo {}
    """, """
        #[repr(/*caret*/)]
        enum Foo {}
    """)

    fun `test repr completion on struct`() = doSingleAttributeCompletion("""
        #[rep/*caret*/]
        struct Foo {}
    """, """
        #[repr(/*caret*/)]
        struct Foo {}
    """)

    fun `test track_caller on function`() = doSingleAttributeCompletion("""
        #[track/*caret*/]
        fn foo() {}
    """, """
        #[track_caller/*caret*/]
        fn foo() {}
    """)

    fun `test no track_caller on struct`() = checkNoCompletion("""
        #[track/*caret*/]
        struct Foo;
    """)

    fun `test non_exhaustive on enum`() = doSingleAttributeCompletion("""
        #[non_/*caret*/]
        enum Foo {}
    """, """
        #[non_exhaustive/*caret*/]
        enum Foo {}
    """)

    fun `test non_exhaustive on struct`() = doSingleAttributeCompletion("""
        #[non_/*caret*/]
        struct S {
            a: u32
        }
    """, """
        #[non_exhaustive/*caret*/]
        struct S {
            a: u32
        }
    """)

    fun `test non_exhaustive on enum variant`() = doSingleAttributeCompletion("""
        enum Foo {
            #[non_/*caret*/]
            A
        }
    """, """
        enum Foo {
            #[non_exhaustive/*caret*/]
            A
        }
    """)

    fun `test no non_exhaustive on function`() = checkNoCompletion("""
        #[non_/*caret*/]
        fn foo() {}
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test attribute proc macro (unqualified)`() = doSingleCompletionByFileTree("""
    //- dep-proc-macro/lib.rs
        #[proc_macro_attribute]
        pub fn attr_as_is(_attr: TokenStream, item: TokenStream) -> TokenStream { item }
    //- lib.rs
        use dep_proc_macro::attr_as_is;
        #[attr_as_/*caret*/]
        fn func() {}
    """, """
        use dep_proc_macro::attr_as_is;
        #[attr_as_is/*caret*/]
        fn func() {}
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test attribute proc macro (qualified)`() = doSingleCompletionByFileTree("""
    //- dep-proc-macro/lib.rs
        #[proc_macro_attribute]
        pub fn attr_as_is(_attr: TokenStream, item: TokenStream) -> TokenStream { item }
    //- lib.rs
        #[dep_proc_macro::attr_as_/*caret*/]
        fn func() {}
    """, """
        #[dep_proc_macro::attr_as_is/*caret*/]
        fn func() {}
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test no function like proc macro`() = checkNoCompletionByFileTree("""
    //- dep-proc-macro/lib.rs
        #[proc_macro]
        pub fn function_like_as_is(input: TokenStream) -> TokenStream { return input; }
    //- lib.rs
        use dep_proc_macro::function_like_as_is;
        #[function_like_/*caret*/]
        fn func() {}
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test single variant when there is local import`() = doSingleCompletionByFileTree("""
    //- dep-proc-macro/lib.rs
        #[proc_macro_attribute]
        pub fn attr_as_is(_attr: TokenStream, item: TokenStream) -> TokenStream { item }
    //- lib.rs
        fn main() {
            use dep_proc_macro::attr_as_is;
            #[attr_as_/*caret*/]
            fn func() {}
        }
    """, """
        fn main() {
            use dep_proc_macro::attr_as_is;
            #[attr_as_is]
            fn func() {}
        }
    """)

    fun `test target_feature on function`() = doSingleAttributeCompletion("""
        #[target_f/*caret*/]
        fn foo() {}
    """, """
        #[target_feature(/*caret*/)]
        fn foo() {}
    """)

    fun `test no target_feature on type`() = checkNoCompletion("""
        #[target_f/*caret*/]
        type Foo = bool;
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test proc_macro completions on function in proc-macro crate`() = checkAttributeCompletionByFileTree(
        listOf("proc_macro", "proc_macro_derive", "proc_macro_attribute"), """
        //- dep-proc-macro/lib.rs
        #[proc_ma/*caret*/]
        fn mac() {}
    """)

    fun `test no proc_macro attributes on function in non-proc-macro crate`() = checkNoCompletion("""
        #[proc_ma/*caret*/]
        fn mac() {}
    """)
}
