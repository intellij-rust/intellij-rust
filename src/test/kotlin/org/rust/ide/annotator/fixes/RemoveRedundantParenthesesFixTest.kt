/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import org.rust.ide.annotator.RsAnnotatorTestBase
import org.rust.ide.annotator.RsExpressionAnnotator

class RemoveRedundantParenthesesFixTest : RsAnnotatorTestBase(RsExpressionAnnotator::class) {
    fun `test simple parentheses`() = checkFixIsUnavailable("Remove parentheses from expression", """
        fn test() {
            let i = (/*caret*/1 + 2);
        }
    """)

    fun `test receiver parentheses`() = checkFixIsUnavailable("Remove parentheses from expression", """
        fn test() {
            (/*caret*/1..2).contains(&3);
        }
    """)

    fun `test nested parentheses`() = checkFixByText("Remove parentheses from expression", """
        fn test() {
            let _ = ((4 + 3)/*caret*/);
        }
    """, """
        fn test() {
            let _ = (4 + 3);
        }
    """)

    fun `test nested parentheses on nested necessary parentheses`() = checkFixByText("Remove parentheses from expression", """
        pub fn total() -> u64 {
            ((-1 + (2 +/*caret*/ 3).pow(64))) as u64
        }
    """, """
        pub fn total() -> u64 {
            (-1 + (2 + 3).pow(64)) as u64
        }
    """)

    fun `test predicate`() = checkFixByText("Remove parentheses from expression", """
        fn test() {
            if (1 < 2/*caret*/) {}
        }
    """, """
        fn test() {
            if 1 < 2 {}
        }
    """)

    fun `test return`() = checkFixByText("Remove parentheses from expression", """
        fn add(x: i32, y: i32) -> i32 {
            return (x + y/*caret*/);
        }
    """, """
        fn add(x: i32, y: i32) -> i32 {
            return x + y;
        }
    """)

    fun `test match`() = checkFixByText("Remove parentheses from expression", """
        fn test() {
            match (x/*caret*/) {
                | 0
                | 1 => 0,
                | _ => 42,
            };
        }
    """, """
        fn test() {
            match x {
                | 0
                | 1 => 0,
                | _ => 42,
            };
        }
    """)

    fun `test for`() = checkFixByText("Remove parentheses from expression", """
        fn test() {
            for i in (0..1/*caret*/) {
            }
        }
    """, """
        fn test() {
            for i in 0..1 {
            }
        }
    """)

    fun `test for with struct literal`() = checkFixIsUnavailable("Remove parentheses from expression", """
        struct SomeIter {
            foo: ()
        }
        impl Iterator for SomeIter {
            type Item = u32;
            fn next(&mut self) -> Option<<Self as Iterator>::Item> {
                Some(1)
            }
        }
        fn test() {
            for val in (SomeIter { foo: () }/*caret*/) {}
        }
    """)
}

