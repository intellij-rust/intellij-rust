/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

class RsPatternArgumentInFunctionPointerTypeTest : RsAnnotatorTestBase(RsSyntaxErrorsAnnotator::class) {
    fun `test E0561 mut identifier`() = checkByText("""
        type Foo = fn(<error descr="Patterns aren't allowed in function pointer types [E0561]">mut arg</error>: u8);
    """)

    fun `test E0561 reference`() = checkByText("""
        type Foo = fn(<error descr="Patterns aren't allowed in function pointer types [E0561]">&arg</error>: u8);
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
        type Foo = fn(u8, <error descr="Patterns aren't allowed in function pointer types [E0561]">mut first</error>: i32, _: &str);
    """)


    fun `test E0561 macros`() = checkByText("""
        macro_rules! foo {
            () => { _ };
        }

        macro_rules! proxy_foo {
            () => { foo!() };
        }

        macro_rules! bad {
            () => { (a, b) };
        }

        macro_rules! proxy_bad {
            () => { bad!() };
        }

        type A = fn(<error descr="Patterns aren't allowed in function pointer types [E0561]">foo!()</error>: (i32, i32));
        type B = fn(<error descr="Patterns aren't allowed in function pointer types [E0561]">proxy_foo!()</error>: (i32, i32));
        type C = fn(<error descr="Patterns aren't allowed in function pointer types [E0561]">bad!()</error>: (i32, i32));
        type D = fn(<error descr="Patterns aren't allowed in function pointer types [E0561]">proxy_bad!()</error>: (i32, i32));
    """)

}
