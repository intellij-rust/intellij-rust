/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

class RsVisibilityCompletionTest : RsCompletionTestBase() {
    fun `test named field decl`() = checkCompletion("pub", """
        struct S {
            /*caret*/a: u32
        }
    """, """
        struct S {
            pub /*caret*/a: u32
        }
    """)

    fun `test inside struct block fields`() = checkContainsCompletion("pub", """
        struct S {
            /*caret*/
        }
    """)

    fun `test move caret after completing pub()`() = checkCompletion("pub()", """
        struct S {
            /*caret*/a: u32
        }
    """, """
        struct S {
            pub(/*caret*/) a: u32
        }
    """)

    fun `test no completion in named field decl of enum variant`() = checkNoCompletion("""
        enum E {
            S {
                /*caret*/a: u32
            }
        }
    """)

    fun `test no completion in named field decl with visibility`() = checkNotContainsCompletion("pub", """
        struct S {
            pub /*caret*/ a: u32
        }
    """)

    fun `test tuple field decl`() = checkCompletion("pub", """
        struct S(/*caret*/u32);
    """, """
        struct S(pub /*caret*/u32);
    """)

    fun `test inside struct tuple fields`() = checkContainsCompletion("pub", """
        struct S(/*caret*/);
    """)

    fun `test inside inherent impl`() = checkCompletion("pub", """
        struct S;

        impl S {
            /*caret*/
        }
    """, """
        struct S;

        impl S {
            pub /*caret*/
        }
    """)

    fun `test no completion in tuple field decl of enum variant`() = checkNotContainsCompletion("pub", """
        enum E {
            S(/*caret*/u32)
        }
    """)

    fun `test no completion in tuple field decl with visibility`() = checkNotContainsCompletion("pub", """
        struct S(pub /*caret*/u32);
    """)

    fun `test no completion before enum variant`() = checkNotContainsCompletion("pub", """
        enum E {
            /*caret*/ V1
        }
    """)

    fun `test no completion inside trait`() = checkNotContainsCompletion("pub", """
        trait Trait {
            /*caret*/ fn foo();
        }
    """)

    fun `test no completion inside trait impl`() = checkNotContainsCompletion("pub", """
        trait Trait {
            fn foo();
        }
        impl Trait for () {
            /*caret*/ fn foo() {}
        }
    """)

    fun `test no completion for function after unsafe`() = checkNotContainsCompletion("pub", """
        unsafe /*caret*/fn foo() {}
    """)

    fun `test no completion for function after extern`() = checkNotContainsCompletion("pub", """
        extern "C" /*caret*/fn foo() {}
    """)

    fun `test no completion for function after const`() = checkNotContainsCompletion("pub", """
        const /*caret*/fn foo() {}
    """)

    fun `test no completion for function after async`() = checkNotContainsCompletion("pub", """
        async /*caret*/fn foo() {}
    """)

    fun `test fn`() = checkContainsCompletion(DEFAULT_VISIBILITIES, """
        /*caret*/fn foo() {}
    """)

    fun `test const`() = checkContainsCompletion(DEFAULT_VISIBILITIES, """
        /*caret*/const FOO: u32 = 0;
    """)

    fun `test static`() = checkContainsCompletion(DEFAULT_VISIBILITIES, """
        /*caret*/static FOO: u32 = 0;
    """)

    fun `test struct`() = checkContainsCompletion(DEFAULT_VISIBILITIES, """
        /*caret*/struct S;
    """)

    fun `test enum`() = checkContainsCompletion(DEFAULT_VISIBILITIES, """
        /*caret*/enum E { V1 }
    """)

    fun `test use`() = checkContainsCompletion(DEFAULT_VISIBILITIES, """
        /*caret*/use foo;
    """)

    fun `test extern crate`() = checkContainsCompletion(DEFAULT_VISIBILITIES, """
        /*caret*/extern crate foo;
    """)

    fun `test mod`() = checkContainsCompletion(DEFAULT_VISIBILITIES, """
        /*caret*/mod foo;
    """)

    companion object {
        private val DEFAULT_VISIBILITIES = listOf("pub", "pub(crate)", "pub()")
    }
}
