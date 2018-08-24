/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor

@ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
class RsAssertEqualInspectionTest : RsInspectionsTestBase(RsAssertEqualInspection()) {
    fun `test simple assert_eq fix`() = checkFixByText("Convert to assert_eq!", """
        fn main() {
            let x = 10;
            let y = 10;
            <weak_warning descr="assert!(a == b) can be assert_eq!(a, b)">assert!(x == y/*caret*/)</weak_warning>;
        }
    """, """
        fn main() {
            let x = 10;
            let y = 10;
            assert_eq!(x, y);
        }
    """, checkWeakWarn = true)

    fun `test expr assert_eq fix`() = checkFixByText("Convert to assert_eq!", """
        fn answer() -> i32 {
            return 42
        }

        fn main() {
            <weak_warning descr="assert!(a == b) can be assert_eq!(a, b)">assert!(answer() == 42/*caret*/)</weak_warning>;
        }
    """, """
        fn answer() -> i32 {
            return 42
        }

        fn main() {
            assert_eq!(answer(), 42);
        }
    """, checkWeakWarn = true)

    fun `test simple assert_eq fix with format_args`() = checkFixByText("Convert to assert_eq!", """
        fn main() {
            let x = 10;
            let y = 10;
            <weak_warning descr="assert!(a == b) can be assert_eq!(a, b)">assert!(x == y, "format {}", 0/*caret*/)</weak_warning>;
        }
    """, """
        fn main() {
            let x = 10;
            let y = 10;
            assert_eq!(x, y, "format {}", 0);
        }
    """, checkWeakWarn = true)

    fun `test simple assert_ne fix`() = checkFixByText("Convert to assert_ne!", """
        fn main() {
            let x = 10;
            let y = 42;
            <weak_warning descr="assert!(a != b) can be assert_ne!(a, b)">assert!(x != y/*caret*/)</weak_warning>;
        }
    """, """
        fn main() {
            let x = 10;
            let y = 42;
            assert_ne!(x, y);
        }
    """, checkWeakWarn = true)

    fun `test expr assert_ne fix`() = checkFixByText("Convert to assert_ne!", """
        fn answer() -> i32 {
            return 42;
        }

        fn main() {
            <weak_warning descr="assert!(a != b) can be assert_ne!(a, b)">assert!(answer() != 50/*caret*/)</weak_warning>;
        }
    """, """
        fn answer() -> i32 {
            return 42;
        }

        fn main() {
            assert_ne!(answer(), 50);
        }
    """, checkWeakWarn = true)

    fun `test simple assert_ne fix with format_args`() = checkFixByText("Convert to assert_ne!", """
        fn main() {
            let x = 10;
            let y = 10;
            <weak_warning descr="assert!(a != b) can be assert_ne!(a, b)">assert!(x != y, "format {}", 0/*caret*/)</weak_warning>;
        }
    """, """
        fn main() {
            let x = 10;
            let y = 10;
            assert_ne!(x, y, "format {}", 0);
        }
    """, checkWeakWarn = true)

    fun `test fix unavailable when arguments do not implement Debug`() = checkFixIsUnavailable("Convert to assert_eq!", """
        #[derive(PartialEq)]
        struct Number(u32);

        fn main() {
            let x = Number(10);
            let y = Number(10);
            assert!(x == y/*caret*/);
        }
    """, checkWeakWarn = true, testmark = RsAssertEqualInspection.Testmarks.debugTraitIsNotImplemented)

    fun `test fix unavailable when arguments do not implement PartialEq`() = checkFixIsUnavailable("Convert to assert_eq!", """
        #[derive(Debug)]
        struct Number(u32);

        fn main() {
            let x = Number(10);
            let y = Number(10);
            assert!(x == y/*caret*/);
        }
    """, checkWeakWarn = true, testmark = RsAssertEqualInspection.Testmarks.partialEqTraitIsNotImplemented)

    fun `test fix available when arguments derive PartialEq & Debug`() = checkFixByText("Convert to assert_ne!", """
        #[derive(Debug, PartialEq)]
        struct Number(u32);

        fn main() {
            let x = Number(10);
            let y = Number(10);
            <weak_warning descr="assert!(a != b) can be assert_ne!(a, b)">assert!(x != y/*caret*/)</weak_warning>;
        }
    """, """
        #[derive(Debug, PartialEq)]
        struct Number(u32);

        fn main() {
            let x = Number(10);
            let y = Number(10);
            assert_ne!(x, y);
        }
    """, checkWeakWarn = true)
}
