/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.wordSelection

class RsListSelectionHandlerTest : RsSelectionHandlerTestBase() {

    fun `test function value parameters list`() = doTest("""
        fn foo(a: u3<caret>2, b: bool) {}
    """, """
        fn foo(a: <selection>u3<caret>2</selection>, b: bool) {}
    """, """
        fn foo(<selection>a: u3<caret>2</selection>, b: bool) {}
    """, """
        fn foo(<selection>a: u3<caret>2, b: bool</selection>) {}
    """, """
        fn foo<selection>(a: u3<caret>2, b: bool)</selection> {}
    """)

    fun `test function type parameters list`() = doTest("""
        fn add<'a, T<caret>: 'a>(a: &'a str, b: T) {}
    """, """
        fn add<'a, <selection>T<caret></selection>: 'a>(a: &'a str, b: T) {}
    """, """
        fn add<'a, <selection>T<caret>: 'a</selection>>(a: &'a str, b: T) {}
    """, """
        fn add<<selection>'a, T<caret>: 'a</selection>>(a: &'a str, b: T) {}
    """, """
        fn add<selection><'a, T<caret>: 'a></selection>(a: &'a str, b: T) {}
    """)

    fun `test function value arguments list`() = doTest("""
        fn foo() { u32::pow(1<caret>2, 4); }
    """, """
        fn foo() { u32::pow(<selection>1<caret>2</selection>, 4); }
    """, """
        fn foo() { u32::pow(<selection>1<caret>2, 4</selection>); }
    """, """
        fn foo() { u32::pow<selection>(1<caret>2, 4)</selection>; }
    """)

    fun `test function type arguments list`() = doTest("""
        fn foo() -> Result<u32, bo<caret>ol> { Ok(0) }
    """, """
        fn foo() -> Result<u32, <selection>bo<caret>ol</selection>> { Ok(0) }
    """, """
        fn foo() -> Result<<selection>u32, bo<caret>ol</selection>> { Ok(0) }
    """, """
        fn foo() -> Result<selection><u32, bo<caret>ol></selection> { Ok(0) }
    """)
}
