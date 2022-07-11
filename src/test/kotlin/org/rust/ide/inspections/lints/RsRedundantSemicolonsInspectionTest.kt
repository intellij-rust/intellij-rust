/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints

import org.rust.ide.inspections.RsInspectionsTestBase

class RsRedundantSemicolonsInspectionTest : RsInspectionsTestBase(RsRedundantSemicolonsInspection::class) {
    fun `test single redundant semicolon`() = checkByText("""
        fn main() {
            struct A;
            struct B;/*warning descr="Unnecessary trailing semicolon"*/;/*warning**/
        }
    """)

    fun `test single redundant semicolon after assign statement`() = checkByText("""
        fn main() {
            let _a = 1 + 1;/*warning descr="Unnecessary trailing semicolon"*/;/*warning**/
        }
    """)

    fun `test multiple redundant semicolons on one line`() = checkByText("""
        fn main() {
            struct A;/*warning descr="Unnecessary trailing semicolons"*/; ;/*warning**/
            {/*warning descr="Unnecessary trailing semicolons"*/;;;/*warning**/}
        }
    """)

    fun `test multiple redundant semicolon on many lines`() = checkByText("""
        fn main() {
            struct A;/*warning descr="Unnecessary trailing semicolons"*/;

            ;

            ;/*warning**/

            {/*warning descr="Unnecessary trailing semicolons"*/;
            ;
            ;/*warning**/}
        }
    """)

    fun `test allow redundant_semicolons`() = checkByText("""
        #[allow(redundant_semicolons)]
        fn main() {
            struct A;;
        }
    """)

    fun `test allow unused`() = checkByText("""
        #[allow(unused)]
        fn main() {
            struct A;;
        }
    """)

    fun `test single fix`() = checkFixByText("Remove unnecessary trailing semicolons", """
        fn main() {
            struct A;/*warning descr="Unnecessary trailing semicolon"*//*caret*/;/*warning**/
        }
    """, """
        fn main() {
            struct A;/*caret*/
        }
    """)

    fun `test multiple fix`() = checkFixByText("Remove unnecessary trailing semicolons", """
        fn main() {
            struct A;/*warning descr="Unnecessary trailing semicolons"*/;
            ;/*caret*/
            ;/*warning**/
        }
    """, """
        fn main() {
            struct A;/*caret*/
        }
    """)

    fun `test multiple semicolons after item statements fix`() = checkFixByText("Remove unnecessary trailing semicolons", """
        fn main() {
            struct A {}/*warning descr="Unnecessary trailing semicolon"*//*caret*/;/*warning**/
            struct B {}/*warning descr="Unnecessary trailing semicolon"*/;/*warning**/
        }
    """, """
        fn main() {
            struct A {}/*caret*/
            struct B {};
        }
    """)

    fun `test suppression quick fix for main fn`() = checkFixByText("Suppress `redundant_semicolons` for fn main", """
        fn main() {
            struct A;/*warning descr="Unnecessary trailing semicolon"*//*caret*/;/*warning**/
        }
    """, """
        #[allow(redundant_semicolons)]
        fn main() {
            struct A;;
        }
    """)
}
