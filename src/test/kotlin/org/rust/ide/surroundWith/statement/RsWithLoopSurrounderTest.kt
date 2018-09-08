/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.surroundWith.statement

import org.rust.ide.surroundWith.RsSurrounderTestBase

class RsWithLoopSurrounderTest : RsSurrounderTestBase(RsWithLoopSurrounder()) {
    fun `test not applicable 1`() = doTestNotApplicable("""
        fn main() {
            let mut server <selection>= Nickel::new();
            server.get("**", hello_world);
            server.listen("127.0.0.1:6767").unwrap();</selection>
        }
    """)

    fun `test not applicable 2`() = doTestNotApplicable("""
        fn main() {
            <selection>#![cfg(test)]
            let mut server = Nickel::new();
            server.get("**", hello_world);
            server.listen("127.0.0.1:6767").unwrap();</selection>
        }
    """)

    fun `test not applicable 3`() = doTestNotApplicable("""
        fn main() {
            loop<selection> {
                for 1 in 1..10 {
                    println!("Hello, world!");
                }
            }</selection>
        }
    """)

    fun `test applicable comment`() = doTest("""
        fn main() {
            <selection>// comment
            let mut server = Nickel::new();
            server.get("**", hello_world);
            server.listen("127.0.0.1:6767").unwrap(); // comment</selection>
        }
    """, """
        fn main() {
            loop {
                // comment
                let mut server = Nickel::new();
                server.get("**", hello_world);
                server.listen("127.0.0.1:6767").unwrap(); // comment
            }
        }
    """)

    fun `test simple 1`() = doTest("""
        fn main() {
            <selection>let mut server = Nickel::new();
            server.get("**", hello_world);
            server.listen("127.0.0.1:6767").unwrap();</selection>
        }
    """, """
        fn main() {
            loop {
                let mut server = Nickel::new();
                server.get("**", hello_world);
                server.listen("127.0.0.1:6767").unwrap();
            }
        }
    """)

    fun `test simple 2`() = doTest("""
        fn main() {
            let mut server = Nickel::new();<selection>
            server.get("**", hello_world);
            server.listen("127.0.0.1:6767").unwrap();</selection>
        }
    """, """
        fn main() {
            let mut server = Nickel::new();
            loop {
                server.get("**", hello_world);
                server.listen("127.0.0.1:6767").unwrap();
            }
        }
    """)

    fun `test simple 3`() = doTest("""
        fn main() {
            <selection>let mut server = Nickel::new();
            server.get("**", hello_world);</selection>
            server.listen("127.0.0.1:6767").unwrap();
        }
    """, """
        fn main() {
            loop {
                let mut server = Nickel::new();
                server.get("**", hello_world);
            }
            server.listen("127.0.0.1:6767").unwrap();
        }
    """)

    fun `test single line`() = doTest("""
        fn main() {
            let mut server = Nickel::new();
            server.get("**", hello_world)<caret>;
            server.listen("127.0.0.1:6767").unwrap();
        }
    """, """
        fn main() {
            let mut server = Nickel::new();
            loop {
                server.get("**", hello_world);
            }
            server.listen("127.0.0.1:6767").unwrap();
        }
    """)

    fun `test nested`() = doTest("""
        fn main() {
            <selection>loop {
                println!("Hello");
            }</selection>
        }
    """, """
        fn main() {
            loop {
                loop {
                    println!("Hello");
                }
            }
        }
    """)
}
