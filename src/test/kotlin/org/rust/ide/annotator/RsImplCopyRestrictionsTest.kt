/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

class RsImplCopyRestrictionsTest : RsAnnotatorTestBase(RsErrorAnnotator::class) {
    fun `test E0206 error when impl Copy for non-ADT`() = checkErrors("""
        #[lang = "copy"]
        trait Copy {}

        struct Bar;

        impl Copy for <error descr="The trait `Copy` may not be implemented for this type [E0206]">&'static mut Bar</error> { }
    """)

    fun `test E0206 error when impl Copy for trait`() = checkErrors("""
        #[lang = "copy"]
        trait Copy {}

        trait Bar {}

        impl Copy for <error descr="The trait `Copy` may not be implemented for this type [E0206]">dyn Bar</error> {}
    """)

    fun `test E0206 no error when impl Copy for struct`() = checkErrors("""
        #[lang = "copy"]
        trait Copy {}

        struct Bar {}

        impl Copy for Bar {}
    """)

    fun `test E0206 no error when impl Copy for enum`() = checkErrors("""
        #[lang = "copy"]
        trait Copy {}

        enum Bar {}

        impl Copy for Bar {}
    """)

    fun `test E0206 no error when impl Copy for unknown type`() = checkErrors("""
        #[lang = "copy"]
        trait Copy {}

        impl Copy for Bar {}
    """)

    fun `test E0206 no error when impl Copy for primitive type`() = checkErrors("""
        #[lang = "copy"]
        trait Copy {}

        impl Copy for i32 {}
    """)

    fun `test E0206 no error when impl Copy for tuple`() = checkErrors("""
        #[lang = "copy"]
        trait Copy {}

        impl Copy for (i8, i8) {}
    """)

    fun `test E0206 no error when impl Copy for reference`() = checkErrors("""
        #[lang = "copy"]
        trait Copy {}

        struct Bar {}

        impl Copy for &Bar {}
    """)
}
