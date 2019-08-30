/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.typing.assist

import com.intellij.openapi.actionSystem.IdeActions
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase

/**
 * Unit tests for [RsSmartEnterProcessor]
 */
class RsSmartEnterProcessorTest : RsTestBase() {

    fun `test fix simple method call`() = doTest("""
        fn f() -> i32 {
            /*caret*/f(
        }
    """, """
        fn f() -> i32 {
            f();
            /*caret*/
        }
    """)
    fun `test fix nested method call`() = doTest("""
        fn double(x: i32) -> i32 {
        /*caret*/double(double(x
            double(x)
        }
    """, """
        fn double(x: i32) -> i32 {
            double(double(x));
            /*caret*/
            double(x)
        }
    """)

    fun `test fix method call with string literal`() = doTest("""
        fn f(s: String) -> String {
            f(f(f("((")/*caret*/
        }
    """, """
        fn f(s: String) -> String {
            f(f(f("((")));
            /*caret*/
        }
    """)

    fun `test fix method call multiple lines`() = doTest("""
        fn f(s: String) -> String {
            f("");
            f(
                f("(("/*caret*/
        }
    """, """
        fn f(s: String) -> String {
            f("");
            f(
                f("(("));
            /*caret*/
        }
    """)

    fun `test fix whitespace and semicolon`() = doTest("""
        fn f(x: i32) -> i32 {
            f(f(x))/*caret*/  ;
        }
    """, """
        fn f(x: i32) -> i32 {
            f(f(x));
            /*caret*/
        }
    """)

    fun `test fix semicolon after declaration`() = doTest("""
        struct Point {
            x: i32,
            y: i32,
        }
        
        fn main() {
            let origin = Point { x: 0, y: 0 }/*caret*/
        }
    """, """
        struct Point {
            x: i32,
            y: i32,
        }
        
        fn main() {
            let origin = Point { x: 0, y: 0 };
            /*caret*/
        }
    """)

    fun `test fix declaration with call`() = doTest("""
        fn f() -> i32 {
            return 42;
        }
        
        fn main() {
            let x = f(/*caret*/
        }
    """, """
        fn f() -> i32 {
            return 42;
        }
        
        fn main() {
            let x = f();
            /*caret*/
        }
    """)

    fun `test fix match in let`() = doTest("""
        fn main() {
            let version_req = match version {
                Some(v) => try!(VersionReq::parse(v)),
                None => VersionReq::any()
            }/*caret*/
        }
    """, """
        fn main() {
            let version_req = match version {
                Some(v) => try!(VersionReq::parse(v)),
                None => VersionReq::any()
            };
            /*caret*/
        }
    """)

    fun `test fix call in stmt`() = doTest("""
        fn f(s: String) {
            /*caret*/f(
            let x = 5;
        }
    """, """
        fn f(s: String) {
            f();
            /*caret*/
            let x = 5;
        }
    """)

    fun `test fix current line call only`() = doTest("""
        fn main() {
            let a = {
                1
            };

            println!()

            println!()/*caret*/

            let b = {
                1
            };
        }
    """, """
        fn main() {
            let a = {
                1
            };

            println!()

            println!();
            /*caret*/

            let b = {
                1
            };
        }
    """)

    fun `test fix on left brace`() = doTest("""
        fn main() {
            let a = {
                1
            }/*caret*/
        }
    """, """
        fn main() {
            let a = {
                1
            };
            /*caret*/
        }
    """)

    fun `test fix on right brace`() = doTest("""
        fn main() {
            let a = /*caret*/{
                1
            }
        }
    """, """
        fn main() {
            let a = {
                /*caret*/
                1
            };
        }
    """)

    private fun doTest(@Language("Rust") before: String, @Language("Rust") after: String) = checkByText(before, after) {
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_COMPLETE_STATEMENT)
    }
}
