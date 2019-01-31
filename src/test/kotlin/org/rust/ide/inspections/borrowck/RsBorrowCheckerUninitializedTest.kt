/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.borrowck

import org.rust.ide.inspections.RsBorrowCheckerInspection
import org.rust.ide.inspections.RsInspectionsTestBase

class RsBorrowCheckerUninitializedTest : RsInspectionsTestBase(RsBorrowCheckerInspection()) {
    fun `test E0381 error no init`() = checkByText("""
        fn main() {
            let x: i32;
            <error descr="Use of possibly uninitialized variable">x</error>;
        }
    """, checkWarn = false)

    fun `test E0381 error init inside then`() = checkByText("""
        fn main() {
            let x: i32;
            if something { x = 1 } else {};
            <error descr="Use of possibly uninitialized variable">x</error>;
        }
    """, checkWarn = false)

    fun `test E0381 error init inside else`() = checkByText("""
        fn main() {
            let x: i32;
            if something {} else { x = 1 };
            <error descr="Use of possibly uninitialized variable">x</error>;
        }
    """, checkWarn = false)

    fun `test no E0381 error init inside then and else`() = checkByText("""
        fn main() {
            let x: i32;
            if something { x = 1 } else { x = 2 };
            x;
        }
    """, checkWarn = false)

    fun `test E0381 error init inside match arm`() = checkByText("""
        fn main() {
            let x: i32;
            match 42 {
                0...10 => { x = 1 }
                _ => {}
            };
            <error descr="Use of possibly uninitialized variable">x</error>;
        }
    """, checkWarn = false)

    fun `test no E0381 error init inside all match arms`() = checkByText("""
        fn main() {
            let x: i32;
            match 42 {
                0...10 => { x = 1 }
                _ => { x = 2 }
            };
            x;
        }
    """, checkWarn = false)

    fun `test E0381 error init inside while`() = checkByText("""
        fn main() {
            let x: i32;
            while something {
                x = 1;
            };
            <error descr="Use of possibly uninitialized variable">x</error>;
        }
    """, checkWarn = false)
}
