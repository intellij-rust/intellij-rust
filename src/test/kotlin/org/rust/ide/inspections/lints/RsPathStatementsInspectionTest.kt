/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints

import org.rust.ide.inspections.RsInspectionsTestBase

class RsPathStatementsInspectionTest : RsInspectionsTestBase(RsPathStatementsInspection::class) {
    fun `test path statement with no effect`() = checkByText("""
        fn main() {
            let n = 0;
            /*weak_warning descr="Path statement with no effect"*/n;/*weak_warning**/
        }
    """, checkWeakWarn = true)

    fun `test path statement with no effect in block`() = checkByText("""
        fn main() {
            let n = 0;
            let _m = {
                /*weak_warning descr="Path statement with no effect"*/n;/*weak_warning**/
                n
            };
        }
    """, checkWeakWarn = true)

    fun `test allow redundant_semicolons`() = checkByText("""
        #[allow(path_statements)]
        fn main() {
            let n = 0;
            n;
        }
    """, checkWeakWarn = true)

    fun `test fix by removing statement`() = checkFixByText("Remove `n;`", """
        fn main() {
            let n = 0;
            /*caret*//*weak_warning descr="Path statement with no effect"*/n;/*weak_warning**/
        }
    """, """
        fn main() {
            let n = 0;
        /*caret*/}
    """, checkWeakWarn = true)

    fun `test path statement drops value`() = checkByText("""
        #[lang = "drop"]   pub trait Drop {}
        struct Droppable;
        impl Drop for Droppable {}

        fn main() {
            let n = Droppable {};
            /*weak_warning descr="Path statement drops value"*/n;/*weak_warning**/
        }
    """, checkWeakWarn = true)

    fun `test fix by replacing with drop`() = checkFixByText("Use `drop` to clarify the intent: `drop(n);`", """
        #[lang = "drop"] pub trait Drop {}
        struct Droppable;
        impl Drop for Droppable {}

        fn main() {
            let n = Droppable {};
            /*caret*//*weak_warning descr="Path statement drops value"*/n;/*weak_warning**/
        }
    """, """
        #[lang = "drop"] pub trait Drop {}
        struct Droppable;
        impl Drop for Droppable {}

        fn main() {
            let n = Droppable {};
            /*caret*/drop(n);
        }
    """, checkWeakWarn = true)
}
