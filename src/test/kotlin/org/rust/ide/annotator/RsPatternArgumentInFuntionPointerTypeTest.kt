/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

class RsPatternArgumentInFuntionPointerTypeTest : RsAnnotatorTestBase(RsSyntaxErrorsAnnotator::class) {
    fun `test E0561 mut identifier`() = checkByText("""
        type Foo = fn(<error descr="Patterns aren't allowed in function pointer types [E0561]">mut arg: u8</error>);
    """)

    fun `test E0561 reference`() = checkByText("""
        type Foo = fn(<error descr="Patterns aren't allowed in function pointer types [E0561]">& arg: u8</error>);
    """)


    fun `test E0561 identifier`() = checkByText("""
        type Foo = fn(param: u8);
    """)

    fun `test E0561 wildcard`() = checkByText("""
        type Foo = fn(_: u8);
    """)

    fun `test E0561 no ident`() = checkByText("""
        type Foo = fn(u8);
    """)

    fun `test E0561 multiple identifiers`() = checkByText("""
        type Foo = fn(u8, first: i32, _: &str);
    """)

    fun `test E0561 multiple identifiers one mut`() = checkByText("""
        type Foo = fn(u8, <error descr="Patterns aren't allowed in function pointer types [E0561]">mut first: i32</error>, _: &str);
    """)

}
