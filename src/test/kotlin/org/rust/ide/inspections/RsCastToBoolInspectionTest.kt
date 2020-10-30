/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

class RsCastToBoolInspectionTest : RsInspectionsTestBase(RsCastToBoolInspection::class) {

    fun `test cast number to bool`() = checkErrors("""
        fn main() {
            <error descr="It is not allowed to cast to a bool. [E0054]">5 as bool</error>;
        }
    """)

    fun `test cast bool to bool`() = checkErrors("""
        fn main() {
            true as bool;
        }
    """)

    fun `test cast to bool in expression`() = checkErrors("""
        fn main() {
            let a = (<error descr="It is not allowed to cast to a bool. [E0054]">5 as bool</error>) == false;
        }
    """)

    // Casts from types that are not primitive emit other errors than E0054.
    fun `test cast from non primitive type`() = checkErrors("""
        fn main() {
            struct S;
            S as bool;
        }
    """)

    // Casts from unit to bool emit E0605 instead of E0054.
    fun `test cast from unit to bool`() = checkErrors("""
        fn main() {
            () as bool;
        }
    """)

}
