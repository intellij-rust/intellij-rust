/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import org.rust.MockEdition
import org.rust.cargo.project.workspace.CargoWorkspace.Edition

@MockEdition(Edition.EDITION_2018)
class RsConstantConditionIfInspectionTest : RsInspectionsTestBase(RsConstantConditionIfInspection::class) {

    fun `test always true, empty branches`() = checkFixByText("Simplify expression", """
        fn main() {
            if <warning descr="Condition is always ''true''">/*caret*/true</warning> {} else {}
        }
    """, """
        fn main() {}
    """)

    fun `test always false, empty branches`() = checkFixByText("Simplify expression", """
        fn main() {
            if <warning descr="Condition is always ''false''">/*caret*/false</warning> {} else {}
        }
    """, """
        fn main() {}
    """)

    fun `test always true, simple branches`() = checkFixByText("Simplify expression", """
        fn main() {
            if <warning descr="Condition is always ''true''">/*caret*/true</warning> {
                println!("1");
            } else {
                println!("2");
            }
        }
    """, """
        fn main() {
            println!("1");
        }
    """)

    fun `test always false, simple branches`() = checkFixByText("Simplify expression", """
        fn main() {
            if <warning descr="Condition is always ''false''">/*caret*/false</warning> {
                println!("1");
            } else {
                println!("2");
            }
        }
    """, """
        fn main() {
            println!("2");
        }
    """)

    fun `test always true, complex branches`() = checkFixByText("Simplify expression", """
        fn main() {
            if <warning descr="Condition is always ''true''">/*caret*/true</warning> {
                println!("1");
                println!("1");
            } else {
                println!("2");
                println!("2");
            }
        }
    """, """
        fn main() {
            println!("1");
            println!("1");
        }
    """)

    fun `test always false, complex branches`() = checkFixByText("Simplify expression", """
        fn main() {
            if <warning descr="Condition is always ''false''">/*caret*/false</warning> {
                println!("1");
                println!("1");
            } else {
                println!("2");
                println!("2");
            }
        }
    """, """
        fn main() {
            println!("2");
            println!("2");
        }
    """)

    fun `test always true, without else`() = checkFixByText("Simplify expression", """
        fn main() {
            if <warning descr="Condition is always ''true''">/*caret*/true</warning> {
                1;
            }
        }
    """, """
        fn main() {
            1;
        }
    """)

    fun `test always false, without else`() = checkFixByText("Delete expression", """
        fn main() {
            if <warning descr="Condition is always ''false''">/*caret*/false</warning> {
                1;
            }
        }
    """, """
        fn main() {
        }
    """)

    fun `test used as expression, simple branch`() = checkFixByText("Simplify expression", """
        fn main() {
            let _ = if <warning descr="Condition is always ''true''">/*caret*/true</warning> {
                1
            } else {
                2
            };
        }
    """, """
        fn main() {
            let _ = 1;
        }
    """)

    fun `test used as expression, complex branch`() = checkFixByText("Simplify expression", """
        fn main() {
            let _ = if <warning descr="Condition is always ''true''">/*caret*/true</warning> {
                println!("1");
                1
            } else {
                2
            };
        }
    """, """
        fn main() {
            let _ = {
                println!("1");
                1
            };
        }
    """)

    fun `test used as tail expression, simple branch`() = checkFixByText("Simplify expression", """
        fn main() {
            if <warning descr="Condition is always ''true''">/*caret*/true</warning> {
                1
            } else {
                2
            }
        }
    """, """
        fn main() {
            1
        }
    """)

    fun `test used as tail expression, complex branch`() = checkFixByText("Simplify expression", """
        fn main() {
            if <warning descr="Condition is always ''true''">/*caret*/true</warning> {
                println!("1");
                1
            } else {
                2
            }
        }
    """, """
        fn main() {
            println!("1");
            1
        }
    """)

    fun `test if is not last in function 1`() = checkFixByText("Simplify expression", """
        fn main() {
            if <warning descr="Condition is always ''true''">/*caret*/true</warning> {} else {}
            println!("3");
        }
    """, """
        fn main() {
            println!("3");
        }
    """)

    fun `test if is not last in function 2`() = checkFixByText("Simplify expression", """
        fn main() {
            if <warning descr="Condition is always ''true''">/*caret*/true</warning> {
                println!("1");
            } else {
                println!("2");
            }
            println!("3");
        }
    """, """
        fn main() {
            println!("1");
            println!("3");
        }
    """)

    fun `test if is not last in function 3`() = checkFixByText("Simplify expression", """
        fn main() {
            if <warning descr="Condition is always ''true''">/*caret*/true</warning> {
                1
            } else {
                2
            }
            println!("3");
        }
    """, """
        fn main() {
            1;
            println!("3");
        }
    """)

    fun `test cascade if, first true`() = checkFixByText("Simplify expression", """
        fn main() {
            if <warning descr="Condition is always ''true''">/*caret*/true</warning> {
                1;
            } else if a {
                2;
            } else {
                3;
            }
        }
    """, """
        fn main() {
            /*caret*/1;
        }
    """)

    fun `test cascade if, first false`() = checkFixByText("Simplify expression", """
        fn main() {
            if <warning descr="Condition is always ''false''">/*caret*/false</warning> {
                1;
            } else if a {
                2;
            } else {
                3;
            }
        }
    """, """
        fn main() {
            /*caret*/if a {
                2;
            } else {
                3;
            }
        }
    """)

    fun `test cascade if, middle true`() = checkFixByText("Simplify expression", """
        fn main() {
            if a {
                1;
            } else if <warning descr="Condition is always ''true''">/*caret*/true</warning> {
                2;
            } else if b {
                3;
            } else {
                4;
            }
        }
    """, """
        fn main() {
            if a {
                1;
            } else /*caret*/{
                2;
            }
        }
    """)

    fun `test cascade if, middle false`() = checkFixByText("Simplify expression", """
        fn main() {
            if a {
                1;
            } else if <warning descr="Condition is always ''false''">/*caret*/false</warning> {
                2;
            } else if b {
                3;
            } else {
                4;
            }
        }
    """, """
        fn main() {
            if a {
                1;
            } else /*caret*/if b {
                3;
            } else {
                4;
            }
        }
    """)

    fun `test cascade if, last true`() = checkFixByText("Simplify expression", """
        fn main() {
            if a {
                1;
            } else if <warning descr="Condition is always ''true''">/*caret*/true</warning> {
                2;
            } else {
                3;
            }
        }
    """, """
        fn main() {
            if a {
                1;
            } else /*caret*/{
                2;
            }
        }
    """)

    fun `test cascade if, last false`() = checkFixByText("Simplify expression", """
        fn main() {
            if a {
                1;
            } else if <warning descr="Condition is always ''false''">/*caret*/false</warning> {
                2;
            } else {
                3;
            }
        }
    """, """
        fn main() {
            if a {
                1;
            } else /*caret*/{
                3;
            }
        }
    """)

    fun `test cascade if, last without else true`() = checkFixByText("Simplify expression", """
        fn main() {
            if a {
                1;
            } else if <warning descr="Condition is always ''true''">/*caret*/true</warning> {
                2;
            }
        }
    """, """
        fn main() {
            if a {
                1;
            } else /*caret*/{
                2;
            }
        }
    """)

    fun `test cascade if, last without else false`() = checkFixByText("Delete expression", """
        fn main() {
            if a {
                1;
            } else if <warning descr="Condition is always ''false''">/*caret*/false</warning> {
                2;
            }
        }
    """, """
        fn main() {
            if a {
                1;
            }/*caret*/
        }
    """)
}
