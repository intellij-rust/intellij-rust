/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import org.rust.MockAdditionalCfgOptions
import org.rust.ProjectDescriptor
import org.rust.WithDependencyRustProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.lang.core.resolve.KnownDerivableTrait
import org.rust.lang.core.resolve.withDependencies

@ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
class RsDeriveCompletionProviderTest : RsCompletionTestBase() {
    fun `test complete on struct`() = doSingleCompletion("""
        #[derive(Debu/*caret*/)]
        struct Test {
            foo: u8
        }
    """, """
        #[derive(Debug/*caret*/)]
        struct Test {
            foo: u8
        }
    """)

    fun `test complete on enum`() = doSingleCompletion("""
        #[derive(Debu/*caret*/)]
        enum Test {
            Something
        }
    """, """
        #[derive(Debug/*caret*/)]
        enum Test {
            Something
        }
    """)

    fun `test complete with dependencies`() {
        KnownDerivableTrait.values()
            .filter { it.isStd && it.dependencies.isNotEmpty() }
            .forEach {
                checkContainsCompletion(it.withDependencies.joinToString(", "), """
                    #[derive(${it.name.dropLast(1)}/*caret*/)]
                    struct Foo;
                """)
            }
    }

    fun `test complete with partially implemented dependencies`() = checkContainsCompletion("Ord, Eq, PartialEq", """
        #[derive(PartialOrd, Or/*caret*/)]
        struct Foo;
    """)

    fun `test complete with manually implemented dependencies`() = doSingleCompletion("""
        #[derive(Cop/*caret*/)]
        enum Foo { Something }

        impl Clone for Foo {
            fn clone(&self) -> Foo { Foo::Something }
        }
    """, """
        #[derive(Copy/*caret*/)]
        enum Foo { Something }

        impl Clone for Foo {
            fn clone(&self) -> Foo { Foo::Something }
        }
    """)

    fun `test doesnt complete on fn`() = checkNoCompletion("""
        #[foo(PartialE/*caret*/)]
        fn foo() { }
    """)

    fun `test doesnt complete on mod`() = checkNoCompletion("""
        #[foo(PartialE/*caret*/)]
        mod foo { }
    """)

    fun `test doesnt complete non derive attr`() = checkNoCompletion("""
        #[foo(PartialE/*caret*/)]
        enum Test { Something }
    """)

    fun `test doesnt complete inner attr`() = checkNoCompletion("""
        mod bar {
            #![derive(PartialE/*caret*/)]
        }
    """)

    fun `test doesnt complete already derived`() = checkNoCompletion("""
        #[derive(Debug, Debu/*caret*/)]
        enum Test { Something }
    """)

    fun `test doesn't complete already derived`() = checkNoCompletion("""
        #[derive(Clon/*caret*/)]
        enum Foo { Something }

        impl Clone for Foo {
            fn clone(&self) -> Foo { Foo::Something }
        }
    """)

    fun `test no serde Serialize completion if no serde traits`() = checkNoCompletion("""
        trait Serialize {}
        #[derive(Ser/*caret*/)]
        struct S;
    """)

    fun `test no completion in non primitive path`() = checkNoCompletion("""
        #[derive(std::marker::Clo/*caret*/)]
        struct S;
    """)

    fun `test complete in cfg_attr`() = doSingleCompletion("""
        #[cfg_attr(windows, derive(Debu/*caret*/))]
        struct Test {
            foo: u8
        }
    """, """
        #[cfg_attr(windows, derive(Debug/*caret*/))]
        struct Test {
            foo: u8
        }
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test no completion if already derived under cfg_attr`() = checkNoCompletion("""
        #[cfg_attr(intellij_rust, derive(Debug))]
        #[cfg_attr(intellij_rust, derive(Debu/*caret*/))]
        struct Test {
            foo: u8
        }
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test derive proc macro (unqualified)`() = doSingleCompletionByFileTree("""
    //- dep-proc-macro/lib.rs
        #[proc_macro_derive(DeriveEmpty)]
        pub fn derive_empty(_item: TokenStream) -> TokenStream { "".parse().unwrap() }
    //- lib.rs
        use dep_proc_macro::DeriveEmpty;
        #[derive(Derive/*caret*/)]
        struct Test {}
    """, """
        use dep_proc_macro::DeriveEmpty;
        #[derive(DeriveEmpty/*caret*/)]
        struct Test {}
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test derive proc macro (out of scope)`() = doSingleCompletionByFileTree("""
    //- dep-proc-macro/lib.rs
        #[proc_macro_derive(DeriveEmpty)]
        pub fn derive_empty(_item: TokenStream) -> TokenStream { "".parse().unwrap() }
    //- lib.rs
        #[derive(Derive/*caret*/)]
        struct Test {}
    """, """
        use dep_proc_macro::DeriveEmpty;

        #[derive(DeriveEmpty/*caret*/)]
        struct Test {}
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test no function like proc macro (unqualified)`() = checkNoCompletionByFileTree("""
    //- dep-proc-macro/lib.rs
        #[proc_macro]
        pub fn function_like_as_is(input: TokenStream) -> TokenStream { return input; }
    //- lib.rs
        use dep_proc_macro::function_like_as_is;
        #[derive(function_like_/*caret*/)]
        struct Test {}
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test no function like proc macro (out of scope)`() = checkNoCompletionByFileTree("""
    //- dep-proc-macro/lib.rs
        #[proc_macro]
        pub fn function_like_as_is(input: TokenStream) -> TokenStream { return input; }
    //- lib.rs
        #[derive(function_like_/*caret*/)]
        struct Test {}
    """)
}
