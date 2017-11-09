/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.highlight

import com.intellij.codeInsight.highlighting.HighlightUsagesHandler
import org.intellij.lang.annotations.Language
import org.rust.lang.RsTestBase

class RsHighlightExitPointsHandlerFactoryTest : RsTestBase() {

    fun doTest(@Language("Rust") check: String, vararg usages: String) {
        InlineFile(check)
        HighlightUsagesHandler.invoke(myFixture.project, myFixture.editor, myFixture.file)
        val highlighters = myFixture.editor.markupModel.allHighlighters
        val actual = highlighters.map { myFixture.file.text.substring(it.startOffset, it.endOffset) }.toList()
        assertSameElements(actual, usages.toList())
    }

    fun `test highlight all returns`() = doTest("""
        fn main() {
            if true {
                /*caret*/return 1;
            }
            return 0;
        }
    """, "return 1", "return 0")

    fun `test highlight try macro as return`() = doTest("""
        fn main() {
            if true {
                try!(Err(()))
            }
            /*caret*/return 0;
        }
    """, "try!(Err(()))", "return 0")

    fun `test highlight diverging macros as return`() = doTest("""
        fn main() {
            if true {
                panic!("test")
            } else {
                unimplemented!()
            }
            /*caret*/return 0;
        }
    """, "panic!(\"test\")", "unimplemented!()", "return 0")

    fun `test highlight ? operator as return`() = doTest("""
        fn main() {
            if true {
                Err(())/*caret*/?
            }
            return 0;
        }
    """, "?", "return 0")

    fun `test highlight complex return as return`() = doTest("""
        struct S;
        impl S {
            fn foo(self) -> Result<S, i32> {Ok(self)}
            fn bar(self) -> S {self}
        }
        fn main() {
            let s = S;
            s.foo()/*caret*/?.bar().foo()?;
            return 0;
        }
    """, "?", "?", "return 0")

    fun `test highlight last stmt lit as return`() = doTest("""
        fn test() {}
        fn main() {
            if true {
                /*caret*/return 1;
            }
            test();
            0
        }
    """, "return 1", "0")

    fun `test highlight last stmt call as return`() = doTest("""
        fn test() -> i32 {}
        fn main() {
            if true {
                /*caret*/return 1;
            }
            test()
        }
    """, "return 1", "test()")

    fun `test highlight should not highlight inner function`() = doTest("""
        fn main() {
            fn bar() {
                return 2;
            }
            /*caret*/return 1;
        }
    """, "return 1")

    fun `test highlight should not highlight inner lambda`() = doTest("""
        fn main() {
            let one = || { return 1; };
            /*caret*/return 2;
        }
    """, "return 2")

    fun `test highlight should not highlight outer function`() = doTest("""
        fn main() {
            let one = || { /*caret*/return 1; };
            return 2;
        }
    """, "return 1")

    fun `test highlight last stmt if as return`() = doTest("""
        fn test() -> i32 {}
        fn main() {
            if true {
                /*caret*/return 1;
            }
            if false { 2 } else { 3 }
        }
    """, "return 1", "2", "3")

    fun `test highlight last stmt if in if and match as return`() = doTest("""
        fn test() -> i32 {}
        fn main() {
            if true {
                /*caret*/return 1;
            }
            if false {
                match None {
                    Some(_) => 2,
                    None => 3,
                }
            } else {
                if true {
                    4
                } else {
                    5
                }
            }
        }
    """, "return 1", "2", "3", "4", "5")

    fun `test highlight last stmt match as return`() = doTest("""
        fn test() -> i32 {}
        fn main() {
            if true {
                /*caret*/return 1;
            }
            match Some("test") {
                Some(s) => { 2 }
                _ => 3,
            }
        }
    """, "return 1", "2", "3")

    fun `test highlight last stmt match with inner as return`() = doTest("""
        fn test() -> Result<i32,i32> {}
        fn main() {
            if true {
                /*caret*/return 1;
            }
            match test()? {
                Some(s) => 2,
                _ => 3,
            }
        }
    """, "return 1", "?", "2", "3")

    fun `test highlight last stmt match with pat as return`() = doTest("""
        fn test() -> Result<i32,i32> {}
        fn main() {
            if true {
                /*caret*/return 1;
            }
            match "test" {
                "test" => 2,
                _ => 3,
            }
        }
    """, "return 1", "2", "3")

    fun `test highlight last stmt match in match and if as return`() = doTest("""
        fn test() -> Result<i32,i32> {}
        fn main() {
            if true {
                /*caret*/return 1;
            }
            match None {
                Some(_) => match "test" {
                    "test" => 2,
                    _ => 3,
                },
                _ => if true {
                    4
                } else {
                    5
                },
            }
        }
    """, "return 1", "2", "3", "4", "5")

    fun `test return in macro`() = doTest("""
        macro_rules! foo {
            () => { /*caret*/return }
        }
    """)

}
