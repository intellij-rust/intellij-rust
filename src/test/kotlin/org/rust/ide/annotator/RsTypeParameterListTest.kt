/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

class RsTypeParameterListTest : RsAnnotatorTestBase(RsErrorAnnotator::class) {
    fun `test E0128 error when use forward declared simple default`() = checkErrors("""
        struct Foo<A, T = /*error descr="Generic parameters with a default cannot use forward declared identifiers [E0128]"*/U/*error**/, U = ()> {
            field: A,
            field1: T,
            field2: U,
        }
    """)

    fun `test E0128 error when use forward declared dyn trait object default`() = checkErrors("""
        trait A{}

        struct Foo<A, T = /*error descr="Generic parameters with a default cannot use forward declared identifiers [E0128]"*/U/*error**/, U = dyn A> {
            field: A,
            field1: T,
            field2: U,
        }
    """)

    fun `test E0128 no error when use identifier declared before`() = checkErrors("""
        struct Foo<A, U = (), T = U> {
            field: A,
            field1: T,
            field2: U,
        }
    """)

    fun `test E0128 error when use forward declared identifier in type declaration`() = checkErrors("""
        struct Foo<T> {
            field: T
        }

        type Bar<T = /*error descr="Generic parameters with a default cannot use forward declared identifiers [E0128]"*/U/*error**/, U = ()> = Foo<B>;
    """)

    fun `test E0128 error when use nested forward declared identifier in type declaration`() = checkErrors("""
        struct Foo<A = Vec<Vec</*error descr="Generic parameters with a default cannot use forward declared identifiers [E0128]"*/B/*error**/>>, B = ()> {
            field: A,
            field1: B
        }
    """)

    fun `test E0128 error when use multiple forward declared identifier in type declaration`() = checkErrors("""
        struct Foo<A = O</*error descr="Generic parameters with a default cannot use forward declared identifiers [E0128]"*/B/*error**/, /*error descr="Generic parameters with a default cannot use forward declared identifiers [E0128]"*/C/*error**/>, B = (), C = ()> {
            field: A,
            field1: B
        }

        struct O<A, B> {
            a: A,
            b: B
        }
    """)
}
