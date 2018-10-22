/*
* Use of this source code is governed by the MIT license that can be
* found in the LICENSE file.
*/

package org.rust.ide.wordSelection

class RsBlocksAndBodiesSelectionHandlerTest : RsSelectionHandlerTestBase() {
    fun `test fn body basic`() = doTestWithTrimmedMargins(
        """
        |fn foo() {
        |<caret>    let x = 1;
        |}
        |""",

        """
        |fn foo() {
        |<selection>    let x = 1;
        |</selection>}
        |""",

        """
        |fn foo() <selection>{
        |    let x = 1;
        |}
        |</selection>""")

    fun `test fun body multiline`() = doTestWithTrimmedMargins(
        """
        |fn foo() {
        |    let x = 1;
        |
        |<caret>    let x = 1;
        |    let x = 1;
        |
        |    let x = 1;
        |}
        |""",

        """
        |fn foo() {
        |    let x = 1;
        |
        |<selection>    let x = 1;
        |</selection>    let x = 1;
        |
        |    let x = 1;
        |}
        |""",

        """
        |fn foo() {
        |    let x = 1;
        |
        |<selection>    let x = 1;
        |    let x = 1;
        |</selection>
        |    let x = 1;
        |}
        |""",

        """
        |fn foo() {
        |<selection>    let x = 1;
        |
        |    let x = 1;
        |    let x = 1;
        |
        |    let x = 1;
        |</selection>}
        |""")

    fun `test fun body line comments`() = doTestWithTrimmedMargins(
        """
        |fn foo() {
        |    // foo
        |
        |<caret>    // foo
        |    // foo
        |
        |    // foo
        |}
        |""",

        """
        |fn foo() {
        |    // foo
        |
        |<selection>    // foo
        |</selection>    // foo
        |
        |    // foo
        |}
        |""",

        """
        |fn foo() {
        |    // foo
        |
        |<selection>    // foo
        |    // foo
        |</selection>
        |    // foo
        |}
        |""",

        """
        |fn foo() {
        |<selection>    // foo
        |
        |    // foo
        |    // foo
        |
        |    // foo
        |</selection>}
        |""")

    fun `test fun block expression`() = doTestWithTrimmedMargins(
        """
        |fn foo() {
        |    let block = {
        |        let x = 1;
        |
        |<caret>        let x = 1;
        |        let x = 1;
        |
        |        let x = 1;
        |    };
        |}
        |""",

        """
        |fn foo() {
        |    let block = {
        |        let x = 1;
        |
        |<selection>        let x = 1;
        |</selection>        let x = 1;
        |
        |        let x = 1;
        |    };
        |}
        |""",

        """
        |fn foo() {
        |    let block = {
        |        let x = 1;
        |
        |<selection>        let x = 1;
        |        let x = 1;
        |</selection>
        |        let x = 1;
        |    };
        |}
        |""",

        """
        |fn foo() {
        |    let block = {
        |<selection>        let x = 1;
        |
        |        let x = 1;
        |        let x = 1;
        |
        |        let x = 1;
        |</selection>    };
        |}
        |""")

    fun `test struct body`() = doTestWithTrimmedMargins(
        """
        |struct Point {
        |    a : i32,
        |
        |<caret>    a : i32,
        |    a : i32,
        |
        |    a : i32
        |}
        |""",

        """
        |struct Point {
        |    a : i32,
        |
        |<selection>    a : i32,
        |</selection>    a : i32,
        |
        |    a : i32
        |}
        |""",

        """
        |struct Point {
        |    a : i32,
        |
        |<selection>    a : i32,
        |    a : i32,
        |</selection>
        |    a : i32
        |}
        |""",

        """
        |struct Point {
        |<selection>    a : i32,
        |
        |    a : i32,
        |    a : i32,
        |
        |    a : i32
        |</selection>}
        |""")

    fun `test struct literal body`() = doTestWithTrimmedMargins(
        """
        |fn foo() {
        |    let x = Point {
        |        a: 0,
        |
        |<caret>        a: 0,
        |        a: 0,
        |
        |        a: 0
        |    };
        |}
        |""",

        """
        |fn foo() {
        |    let x = Point {
        |        a: 0,
        |
        |<selection>        a: 0,
        |</selection>        a: 0,
        |
        |        a: 0
        |    };
        |}
        |""",

        """
        |fn foo() {
        |    let x = Point {
        |        a: 0,
        |
        |<selection>        a: 0,
        |        a: 0,
        |</selection>
        |        a: 0
        |    };
        |}
        |""",

        """
        |fn foo() {
        |    let x = Point {
        |<selection>        a: 0,
        |
        |        a: 0,
        |        a: 0,
        |
        |        a: 0
        |</selection>    };
        |}
        |""")

    fun `test struct body last field`() = doTestWithTrimmedMargins(
        """
        |fn foo() {
        |    let x = Point {
        |        a : i32,
        |
        |<caret>        a : i32
        |    };
        |}
        |""",

        """
        |fn foo() {
        |    let x = Point {
        |        a : i32,
        |
        |<selection>        a : i32
        |</selection>    };
        |}
        |""",

        """
        |fn foo() {
        |    let x = Point {
        |<selection>        a : i32,
        |
        |        a : i32
        |</selection>    };
        |}
        |""")

    fun `test struct literal body last field`() = doTestWithTrimmedMargins(
        """
        |fn foo() {
        |    let x = Point {
        |        a: 0,
        |
        |<caret>        a: 0
        |    };
        |}
        |""",

        """
        |fn foo() {
        |    let x = Point {
        |        a: 0,
        |
        |<selection>        a: 0
        |</selection>    };
        |}
        |""",

        """
        |fn foo() {
        |    let x = Point {
        |<selection>        a: 0,
        |
        |        a: 0
        |</selection>    };
        |}
        |""")

    fun `test enum body`() = doTestWithTrimmedMargins(
        """
        |enum E {
        |    A,
        |
        |<caret>    A,
        |    A,
        |
        |    A
        |}
        |""",

        """
        |enum E {
        |    A,
        |
        |<selection>    A,
        |</selection>    A,
        |
        |    A
        |}
        |""",

        """
        |enum E {
        |    A,
        |
        |<selection>    A,
        |    A,
        |</selection>
        |    A
        |}
        |""",

        """
        |enum E {
        |<selection>    A,
        |
        |    A,
        |    A,
        |
        |    A
        |</selection>}
        |""")

    fun `test trait body`() = doTestWithTrimmedMargins(
        """
        |trait Trait<T> {
        |    fn f();
        |
        |<caret>    fn f();
        |    fn f();
        |
        |    fn f();
        |}
        |""",

        """
        |trait Trait<T> {
        |    fn f();
        |
        |<selection>    fn f();
        |</selection>    fn f();
        |
        |    fn f();
        |}
        |""",

        """
        |trait Trait<T> {
        |    fn f();
        |
        |<selection>    fn f();
        |    fn f();
        |</selection>
        |    fn f();
        |}
        |""",

        """
        |trait Trait<T> {
        |<selection>    fn f();
        |
        |    fn f();
        |    fn f();
        |
        |    fn f();
        |</selection>}
        |""")


    fun `test match expression`() = doTestWithTrimmedMargins(
        """
        |fn foo() {
        |    let a = match 1 {
        |        1 => 1,
        |
        |<caret>        2 => 2,
        |        3 => {
        |             3
        |        },
        |
        |        _ => 0,
        |    };
        |}
        |""",

        """
        |fn foo() {
        |    let a = match 1 {
        |        1 => 1,
        |
        |<selection>        2 => 2,
        |</selection>        3 => {
        |             3
        |        },
        |
        |        _ => 0,
        |    };
        |}
        |""",

        """
        |fn foo() {
        |    let a = match 1 {
        |        1 => 1,
        |
        |<selection>        2 => 2,
        |        3 => {
        |             3
        |        },
        |</selection>
        |        _ => 0,
        |    };
        |}
        |""",

        """
        |fn foo() {
        |    let a = match 1 {
        |<selection>        1 => 1,
        |
        |        2 => 2,
        |        3 => {
        |             3
        |        },
        |
        |        _ => 0,
        |</selection>    };
        |}
        |""")
}
