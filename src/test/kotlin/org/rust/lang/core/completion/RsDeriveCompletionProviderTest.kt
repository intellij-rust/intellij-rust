/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import org.rust.ProjectDescriptor
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
            .filter { it.dependencies.isNotEmpty() }
            .forEach {
                checkContainsCompletion(it.withDependencies.joinToString(", "), """
                    #[lang = "failure::Fail"]
                    trait Fail {}

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

    fun `test serde Serialize`() = doSingleCompletion("""
        #[lang = "serde::Serialize"]
        trait Serialize {}
        #[derive(Ser/*caret*/)]
        struct S;
    """, """
        #[lang = "serde::Serialize"]
        trait Serialize {}
        #[derive(Serialize/*caret*/)]
        struct S;
    """)

    fun `test no serde Serialize completion if no serde traits`() = checkNoCompletion("""
        trait Serialize {}
        #[derive(Ser/*caret*/)]
        struct S;
    """)


    // TODO add more tests
    fun `test custom completion`() = doSingleCompletion("""
        trait Foo {}
        #[proc_macro_derive(Foo)]
        pub fn foo_deriver(input: u32 /* todo fix, see RsDeriveIndex:~100 */) { }

        #[derive(Fo/*caret*/)]
        struct Bar;
    """, """
        trait Foo {}
        #[proc_macro_derive(Foo)]
        pub fn foo_deriver(input: u32 /* todo fix, see RsDeriveIndex:~100 */) { }

        #[derive(Foo/*caret*/)]
        struct Bar;
    """)
}
