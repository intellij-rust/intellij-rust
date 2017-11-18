/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

class RsAssertEqualInspectionTest : RsInspectionsTestBase(RsAssertEqualInspection()) {
    fun `test simple assert_eq fix`() = checkFixByText("Convert to assert_eq!", """
        fn main() {
            let x = 10;
            let y = 10;
            <weak_warning descr="assert!(a == b) can be assert_eq!(a, b)">assert!(x == y<caret>)</weak_warning>;
        }
    """, """
        fn main() {
            let x = 10;
            let y = 10;
            assert_eq!(x, y);
        }
    """)

    fun `test expr assert_eq fix`() = checkFixByText("Convert to assert_eq!", """
        fn answer() -> i32 {
            return 42
        }

        fn main() {
            <weak_warning descr="assert!(a == b) can be assert_eq!(a, b)">assert!(answer() == 42<caret>)</weak_warning>;
        }
    """, """
        fn answer() -> i32 {
            return 42
        }

        fn main() {
            assert_eq!(answer(), 42);
        }
    """)

    fun `test simple assert_eq fix with format_args`() = checkFixByText("Convert to assert_eq!", """
        fn main() {
            let x = 10;
            let y = 10;
            <weak_warning descr="assert!(a == b) can be assert_eq!(a, b)">assert!(x == y, "format {}", 0<caret>)</weak_warning>;
        }
    """, """
        fn main() {
            let x = 10;
            let y = 10;
            assert_eq!(x, y, "format {}", 0);
        }
    """)

    fun `test simple assert_ne fix`() = checkFixByText("Convert to assert_ne!", """
        fn main() {
            let x = 10;
            let y = 42;
            <weak_warning descr="assert!(a != b) can be assert_ne!(a, b)">assert!(x != y<caret>)</weak_warning>;
        }
    """, """
        fn main() {
            let x = 10;
            let y = 42;
            assert_ne!(x, y);
        }
    """)

    fun `test expr assert_ne fix`() = checkFixByText("Convert to assert_ne!", """
        fn answer() -> i32 {
            return 42
        }

        fn main() {
            <weak_warning descr="assert!(a != b) can be assert_ne!(a, b)">assert!(answer() != 50<caret>)</weak_warning>;
        }
    """, """
        fn answer() -> i32 {
            return 42
        }

        fn main() {
            assert_ne!(answer(), 50);
        }
    """)

    fun `test simple assert_ne fix with format_args`() = checkFixByText("Convert to assert_ne!", """
        fn main() {
            let x = 10;
            let y = 10;
            <weak_warning descr="assert!(a == b) can be assert_eq!(a, b)">assert!(x != y, "format {}", 0<caret>)</weak_warning>;
        }
    """, """
        fn main() {
            let x = 10;
            let y = 10;
            assert_ne!(x, y, "format {}", 0);
        }
    """)
}
