/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import org.intellij.lang.annotations.Language
import org.rust.ide.inspections.RsDbgUsageInspection
import org.rust.ide.inspections.RsInspectionsTestBase


class RemoveDbgFixesTest : RsInspectionsTestBase(RsDbgUsageInspection::class) {

    private fun checkFixByText(@Language("Rust") before: String, @Language("Rust") after: String){
        checkFixByFileTreeWithoutHighlighting("Remove dbg!", before, after)
    }

    private fun checkFixIsUnavailable(@Language("Rust") text: String){
        checkFixIsUnavailable("Remove dbg!", text)
    }

    fun `test remove dbg! from expr`() = checkFixByText("""
        //- main.rs
        fn test() {
            let a = 1 + dbg!(3/*caret*/);
        }
    """, """
        //- main.rs
        fn test() {
            let a = 1 + 3/*caret*/;
        }
    """)

    fun `test remove dbg! from stmt`() = checkFixByText("""
        //- main.rs
        fn test() {
            dbg!(3/*caret*/);
        }
    """, """
        //- main.rs
        fn test() {
            3/*caret*/;
        }
    """)

    fun `test remove recursive dbg!`() = checkFixByText("""
        //- main.rs
        fn test() {
            dbg!(dbg!(3/*caret*/));
        }
    """, """
        //- main.rs
        fn test() {
            dbg!(3/*caret*/);
        }
    """)

    fun `test remove dbg! from function parameter`() = checkFixByText("""
        //- main.rs
        fn f1(a: usize, b: usize) {}

        fn test() {
            f1(1 + dbg!((3 + 1/*caret*/) * 2), dbg!(10));
        }
    """, """
        //- main.rs
        fn f1(a: usize, b: usize) {}

        fn test() {
            f1(1 + ((3 + 1/*caret*/) * 2), dbg!(10));
        }
    """)

    fun `test remove dbg!`() = checkFixByText("""
        //- main.rs
        fn f1(a: usize) {}

        fn test() {
            dbg!(f1(3/*caret*/));
        }
    """, """
        //- main.rs
        fn f1(a: usize) {}

        fn test() {
            f1(3/*caret*/);
        }
    """)


    fun `test remove dbg! 2`() = checkFixByText("""
        //- main.rs
        fn test() {
            let a = dbg!(dbg!(1) + 3/*caret*/);
        }
    """, """
        //- main.rs
        fn test() {
            let a = dbg!(1) + 3/*caret*/;
        }
    """)

    fun `test remove outer dbg!`() = checkFixByText("""
        //- main.rs
        fn test() {
            let a = dbg!(1 +/*caret*/ dbg!(3));
        }
    """, """
        //- main.rs
        fn test() {
            let a = 1 +/*caret*/ dbg!(3);
        }
    """)

    fun `test remove dbg! with paren`() = checkFixByText("""
        //- main.rs
        fn test() {
            let a = dbg!((1 + 3/*caret*/));
        }
    """, """
        //- main.rs
        fn test() {
            let a = (1 + 3/*caret*/);
        }
    """)

    fun `test remove dbg! with binary expr`() = checkFixByText("""
        //- main.rs
        fn main() {
            assert_eq!(dbg!(/*caret*/1 + 1) * 2, 4);
        }
    """, """
        //- main.rs
        fn main() {
            assert_eq!((/*caret*/1 + 1) * 2, 4);
        }
    """)

    fun `test remove dbg! with dot expr`() = checkFixByText("""
        //- main.rs
        fn main() {
            assert_eq!(dbg!(1i32 - 2/*caret*/).abs(), 1);
        }
    """, """
        //- main.rs
        fn main() {
            assert_eq!((1i32 - 2/*caret*/).abs(), 1);
        }
    """)

    fun `test cursor in dbg!`() = checkFixByText("""
        //- main.rs
        fn test() {
            let a = db/*caret*/g!(1 + 3);
        }
    """, """
        //- main.rs
        fn test() {
            let a = /*caret*/1 + 3;
        }
    """)

    fun `test cursor in whitespace`() = checkFixByText("""
        //- main.rs
        fn test() {
            let a = dbg!(1 + 3             /*caret*/        );
        }
    """, """
        //- main.rs
        fn test() {
            let a = 1 + 3/*caret*/;
        }
    """)

    fun `test not available for custom dbg! macro`() = checkFixIsUnavailable("""
        macro_rules! dbg {
            ($ e:expr) => { $ e };
        }
        fn test() {
            let a = 1 + dbg!(3/*caret*/);
        }
    """)

    fun `test not available if not expression`() = checkFixIsUnavailable("""
        dbg!(3/*caret*/);
    """)
}
