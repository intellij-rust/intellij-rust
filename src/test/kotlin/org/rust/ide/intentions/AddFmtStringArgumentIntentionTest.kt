/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import org.intellij.lang.annotations.Language
import org.rust.ide.intentions.addFmtStringArgument.AddFmtStringArgumentIntention

class AddFmtStringArgumentIntentionTest : RsIntentionTestBase(AddFmtStringArgumentIntention::class) {
    fun `test no args`() = doTest("""
        fn main() {
            println!("x = /*caret*/");
        }
    """, """
        fn main() {
            println!("x = {}", x);
        }
    """)

    fun `test single arg`() = doTest("""
        fn main() {
            println!("a = {}, x = /*caret*/", a);
        }
    """, """
        fn main() {
            println!("a = {}, x = {}", a, x);
        }
    """)

    fun `test multiple args add at the end`() = doTest("""
        fn main() {
            println!("a = {}, b = {}, x = /*caret*/", a, b);
        }
    """, """
        fn main() {
            println!("a = {}, b = {}, x = {}", a, b, x);
        }
    """)

    fun `test multiple args add at the beginning`() = doTest("""
        fn main() {
            println!("x = /*caret*/, a = {}, b = {}", a, b);
        }
    """, """
        fn main() {
            println!("x = {}, a = {}, b = {}", x, a, b);
        }
    """)

    fun `test multiple args add at the middle`() = doTest("""
        fn main() {
            println!("a = {}, x = /*caret*/, b = {}", a, b);
        }
    """, """
        fn main() {
            println!("a = {}, x = {}, b = {}", a, x, b);
        }
    """)

    fun `test multiple args add complex expr`() = doTest("""
        fn main() {
            println!("a = {}, complex = /*caret*/, b = {}", a, b);
        }
    """, """
        fn main() {
            println!("a = {}, complex = {}, b = {}", a, x.foo(a, b) * (5 + 7), b);
        }
    """, "x.foo(a, b) * (5 + 7)")

    fun `test unavailable before literal`() = doUnavailableTest("""
        fn main() {
            println!(/*caret*/"x = ");
        }
    """)

    fun `test unavailable after literal`() = doUnavailableTest("""
        fn main() {
            println!("x = "/*caret*/);
        }
    """)

    fun `test unavailable outside fmt macro`() = doUnavailableTest("""
        fn main() {
            foo(/*caret*/);
        }
    """)

    fun `test write`() = doTest("""
        fn main() {
            write!(f, "x = /*caret*/");
        }
    """, """
        fn main() {
            write!(f, "x = {}", x);
        }
    """)

    fun `test multiple args write`() = doTest("""
        fn main() {
            write!(f, "a = {}, x = /*caret*/, b = {}", a, b);
        }
    """, """
        fn main() {
            write!(f, "a = {}, x = {}, b = {}", a, x, b);
        }
    """)

    fun doTest(@Language("Rust") before: String, @Language("Rust") after: String, fragmentText: String = "x") {
        AddFmtStringArgumentIntention.CODE_FRAGMENT_TEXT = fragmentText
        doAvailableTest(before, after)
    }
}
