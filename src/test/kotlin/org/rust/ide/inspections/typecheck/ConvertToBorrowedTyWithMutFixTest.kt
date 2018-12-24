/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.typecheck


class ConvertToBorrowedTyWithMutFixTest : ConvertToTyUsingTraitFixTestBase(
    true, "BorrowMut", "borrow_mut", "use std::borrow::BorrowMut;") {

    fun `test &String to &mut String`() = checkFixIsUnavailable("Convert to &mut String using `BorrowMut` trait", """
        $imports

        fn main() {
            let mut s = String::from("hello");

            change(<error>&s<caret></error>);
        }

        fn change(some_string: &mut String) {
            some_string.push_str(", world");
        }
    """)

    fun `test String to &mut String`() = checkFixIsUnavailable("Convert to &mut String using `BorrowMut` trait", """
        $imports

        fn main() {
            let s = String::from("hello");

            change(<error>s<caret></error>);
        }

        fn change(some_string: &mut String) {
            some_string.push_str(", world");
        }
    """)
}
