/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import org.rust.MockRustcVersion

/**
 * Tests for Redundant Else inspection.
 */
class RsRedundantElseInspectionTest : RsInspectionsTestBase(RsRedundantElseInspection::class) {

    fun `test variable binding`() = checkFixByText("Remove `else`", """
        fn main() {
            let a = 5;
            if let x = a {
            } <warning descr="Redundant `else`"><caret>else</warning> {
            }
        }
    """, """
        fn main() {
            let a = 5;
            if let x = a {
            }
        }
    """)

    fun `test empty single variant enum`() = checkFixByText("Remove `else`", """
        enum E { V1 }

        fn main() {
            let a = E::V1;
            if let E::V1 = a {
            } <warning descr="Redundant `else`"><caret>else</warning> {
            }
        }
    """, """
        enum E { V1 }

        fn main() {
            let a = E::V1;
            if let E::V1 = a {
            }
        }
    """)

    fun `test single field enum variant`() = checkFixByText("Remove `else`", """
        enum E { V1(u32) }

        fn main() {
            let a = E::V1(5);
            if let E::V1(x) = a {
            } <warning descr="Redundant `else`"><caret>else</warning> {
            }
        }
    """, """
        enum E { V1(u32) }

        fn main() {
            let a = E::V1(5);
            if let E::V1(x) = a {
            }
        }
    """)

    fun `test else if`() = checkFixByText("Remove `else`", """
        fn main() {
            let a = 5;
            if let x = a {
            } <warning descr="Redundant `else`"><caret>else</warning> if true {
            }
        }
    """, """
        fn main() {
            let a = 5;
            if let x = a {
            }
        }
    """)

    fun `test first condition irrefutable`() = checkFixByText("Remove `else`", """
        enum E { V1(u32), V2 }

        fn main() {
            let a = 5;
            let b = E::V1(5);
            if let x = a {
            } <warning descr="Redundant `else`">else</warning> if let E::V1(x) = b {
            } <warning descr="Redundant `else`"><caret>else</warning> {
            }
        }
    """, """
        enum E { V1(u32), V2 }

        fn main() {
            let a = 5;
            let b = E::V1(5);
            if let x = a {
            } else if let E::V1(x) = b {
            }
        }
    """)

    fun `test intermediate condition irrefutable`() = checkFixByText("Remove `else`", """
        enum E { V1(u32), V2 }

        fn main() {
            let a = 5;
            let b = E::V1(5);
            if a > 5 {
            } else if let x = a {
            } <warning descr="Redundant `else`"><caret>else</warning> {
            }
        }
    """, """
        enum E { V1(u32), V2 }

        fn main() {
            let a = 5;
            let b = E::V1(5);
            if a > 5 {
            } else if let x = a {
            }
        }
    """)

    fun `test slice 1`() = checkFixByText("Remove `else`", """
        fn main() {
            let vec = &[1, 2][..];
            let single = if let &[..] = &vec {
                Some(s)
            } <warning descr="Redundant `else`"><caret>else</warning> {
                None
            };
        }
    """, """
        fn main() {
            let vec = &[1, 2][..];
            let single = if let &[..] = &vec {
                Some(s)
            };
        }
    """)

    fun `test slice 2`() = checkFixByText("Remove `else`", """
        fn main() {
            let vec = &[1, 2][..];
            let single = if let &[s @ ..] = &vec {
                Some(s)
            } <warning descr="Redundant `else`"><caret>else</warning> {
                None
            };
        }
    """, """
        fn main() {
            let vec = &[1, 2][..];
            let single = if let &[s @ ..] = &vec {
                Some(s)
            };
        }
    """)

    fun `test irrefutable slice`() = checkByText("""
        fn main() {
            let vec = &[1, 2][..];
            let single = if let &[s] = &vec {
                Some(s)
            } else {
                None
            };
        }
    """)

    fun `test nested condition`() = checkByText("""
        enum E { V1(u32), V2 }

        fn main() {
            let a = 5;
            let b = E::V1(5);
            if let x = a {
            } <warning descr="Redundant `else`">else</warning> {
                if a > 5 {
                } else if a <= 5 {
                }
            }
        }
    """)

    @MockRustcVersion("1.56.0")
    fun `test let else (feature unavailable)`() = checkByText("""
        fn main() {
            let x = Some(0) else { return };
            let Some(x) = Some(0) else { return };
        }
    """)

    @MockRustcVersion("1.56.0-nightly")
    fun `test let else (feature available)`() = checkByText("""
        #![feature(let_else)]

        fn main() {
            let x = Some(0) <warning descr="Redundant `else`">else</warning> { return };
            let Some(x) = Some(0) else { return };
        }
    """)
}
