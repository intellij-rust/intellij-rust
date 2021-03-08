/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.formatter

import org.rust.ide.typing.RsTypingTestBase

class RsAutoIndentMacrosTest : RsTypingTestBase() {
    fun `test macro call argument one line braces`() = doTestByText("""
        foo! {/*caret*/}
    """, """
        foo! {
            /*caret*/
        }
    """)

    fun `test macro call argument one line parens`() = doTestByText("""
        foo! (/*caret*/);
    """, """
        foo! (
            /*caret*/
        );
    """)

    fun `test macro call argument one line brackets`() = doTestByText("""
        foo! [/*caret*/];
    """, """
        foo! [
            /*caret*/
        ];
    """)

    fun `test macro call argument two lines`() = doTestByText("""
        foo! {/*caret*/
        }
    """, """
        foo! {
            /*caret*/
        }
    """)

    fun `test macro call argument one line with extra token`() = doTestByText("""
        foo! {/*caret*/foo}
    """, """
        foo! {
            /*caret*/foo}
    """)

    fun `test macro call argument tt one line braces`() = doTestByText("""
        foo! {
            {/*caret*/}
        }
    """, """
        foo! {
            {
                /*caret*/
            }
        }
    """)

    fun `test macro call argument tt one line parens`() = doTestByText("""
        foo! {
            (/*caret*/)
        }
    """, """
        foo! {
            (
                /*caret*/
            )
        }
    """)

    fun `test macro call argument tt one line brackets`() = doTestByText("""
        foo! {
            [/*caret*/]
        }
    """, """
        foo! {
            [
                /*caret*/
            ]
        }
    """)

    fun `test macro call argument tt one line with extra token`() = doTestByText("""
        foo! {
            {/*caret*/foo}
        }
    """, """
        foo! {
            {
                /*caret*/foo}
        }
    """)

    fun `test macro definition body one line braces`() = doTestByText("""
        macro_rules! foo {/*caret*/}
    """, """
        macro_rules! foo {
            /*caret*/
        }
    """)

    fun `test macro definition body one line parens`() = doTestByText("""
        macro_rules! foo (/*caret*/);
    """, """
        macro_rules! foo (
            /*caret*/
        );
    """)

    fun `test macro definition body one line brackets`() = doTestByText("""
        macro_rules! foo [/*caret*/];
    """, """
        macro_rules! foo [
            /*caret*/
        ];
    """)

    fun `test macro definition body two lines`() = doTestByText("""
        macro_rules! foo {/*caret*/
        }
    """, """
        macro_rules! foo {
            /*caret*/
        }
    """)

    fun `test macro definition body one line with extra tokens`() = doTestByText("""
        macro_rules! foo {/*caret*/() => {}}
    """, """
        macro_rules! foo {
            /*caret*/() => {}}
    """)

    fun `test macro definition case pattern one line parens`() = doTestByText("""
        macro_rules! foo {
            (/*caret*/) => {}
        }
    """, """
        macro_rules! foo {
            (
                /*caret*/
            ) => {}
        }
    """)

    fun `test macro definition case pattern one line braces`() = doTestByText("""
        macro_rules! foo {
            {/*caret*/} => {}
        }
    """, """
        macro_rules! foo {
            {
                /*caret*/
            } => {}
        }
    """)

    fun `test macro definition case pattern one line brackets`() = doTestByText("""
        macro_rules! foo {
            [/*caret*/] => {}
        }
    """, """
        macro_rules! foo {
            [
                /*caret*/
            ] => {}
        }
    """)

    fun `test macro definition case pattern one line with extra token`() = doTestByText("""
        macro_rules! foo {
            (/*caret*/foo) => {}
        }
    """, """
        macro_rules! foo {
            (
                /*caret*/foo) => {}
        }
    """)

    fun `test macro definition case body one line braces`() = doTestByText("""
        macro_rules! foo {
            () => {/*caret*/}
        }
    """, """
        macro_rules! foo {
            () => {
                /*caret*/
            }
        }
    """)

    fun `test macro definition case body one line parens`() = doTestByText("""
        macro_rules! foo {
            () => (/*caret*/)
        }
    """, """
        macro_rules! foo {
            () => (
                /*caret*/
            )
        }
    """)

    fun `test macro definition case body one line brackets`() = doTestByText("""
        macro_rules! foo {
            () => [/*caret*/]
        }
    """, """
        macro_rules! foo {
            () => [
                /*caret*/
            ]
        }
    """)

    fun `test macro definition case body one line with extra token`() = doTestByText("""
        macro_rules! foo {
            () => {/*caret*/foo}
        }
    """, """
        macro_rules! foo {
            () => {
                /*caret*/foo}
        }
    """)

    fun `test macro definition case body tt one line braces`() = doTestByText("""
        macro_rules! foo {
            () => {
                {/*caret*/}
            }
        }
    """, """
        macro_rules! foo {
            () => {
                {
                    /*caret*/
                }
            }
        }
    """)

    fun `test macro definition case body tt one line parens`() = doTestByText("""
        macro_rules! foo {
            () => {
                (/*caret*/)
            }
        }
    """, """
        macro_rules! foo {
            () => {
                (
                    /*caret*/
                )
            }
        }
    """)

    fun `test macro definition case body tt one line brackets`() = doTestByText("""
        macro_rules! foo {
            () => {
                [/*caret*/]
            }
        }
    """, """
        macro_rules! foo {
            () => {
                [
                    /*caret*/
                ]
            }
        }
    """)

    fun `test macro definition case body tt one line with extra token`() = doTestByText("""
        macro_rules! foo {
            () => {
                {/*caret*/foo}
            }
        }
    """, """
        macro_rules! foo {
            () => {
                {
                    /*caret*/foo}
            }
        }
    """)

    fun `test macro definition case body tt 2 one line braces`() = doTestByText("""
        macro_rules! foo {
            () => {
                {
                    {/*caret*/}
                }
            }
        }
    """, """
        macro_rules! foo {
            () => {
                {
                    {
                        /*caret*/
                    }
                }
            }
        }
    """)

    fun `test macro definition case body tt 2 one line parens`() = doTestByText("""
        macro_rules! foo {
            () => {
                {
                    (/*caret*/)
                }
            }
        }
    """, """
        macro_rules! foo {
            () => {
                {
                    (
                        /*caret*/
                    )
                }
            }
        }
    """)

    fun `test macro definition case body tt 2 one line brackets`() = doTestByText("""
        macro_rules! foo {
            () => {
                {
                    [/*caret*/]
                }
            }
        }
    """, """
        macro_rules! foo {
            () => {
                {
                    [
                        /*caret*/
                    ]
                }
            }
        }
    """)

    fun `test macro definition case body tt 2 one line with extra token`() = doTestByText("""
        macro_rules! foo {
            () => {
                {
                    {/*caret*/foo}
                }
            }
        }
    """, """
        macro_rules! foo {
            () => {
                {
                    {
                        /*caret*/foo}
                }
            }
        }
    """)

    fun `test macro definition case pattern between tokens 1`() = doTestByText("""
        macro_rules! foo {
            (
                foo/*caret*/
                bar
            ) => {}
        }
    """, """
        macro_rules! foo {
            (
                foo
                /*caret*/
                bar
            ) => {}
        }
    """)

    fun `test macro definition case pattern between tokens 2`() = doTestByText("""
        macro_rules! foo {
            (
                foo/*caret*/bar
                baz
            ) => {}
        }
    """, """
        macro_rules! foo {
            (
                foo
                /*caret*/bar
                baz
            ) => {}
        }
    """)

    fun `test macro definition case body between tokens 1`() = doTestByText("""
        macro_rules! foo {
            () => {
                foo/*caret*/
                bar
            }
        }
    """, """
        macro_rules! foo {
            () => {
                foo
                /*caret*/
                bar
            }
        }
    """)

    fun `test macro definition case body between tokens 2`() = doTestByText("""
        macro_rules! foo {
            () => {
                foo/*caret*/bar
                baz
            }
        }
    """, """
        macro_rules! foo {
            () => {
                foo
                /*caret*/bar
                baz
            }
        }
    """)
}
