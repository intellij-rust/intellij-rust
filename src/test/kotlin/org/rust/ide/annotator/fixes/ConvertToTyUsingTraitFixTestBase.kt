/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.ide.inspections.RsInspectionsTestBase
import org.rust.ide.inspections.RsTypeCheckInspection

@ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
abstract class ConvertToTyUsingTraitFixTestBase(
    isExpectedMut: Boolean, private val trait: String, private val method: String, protected val imports: String = ""
) : RsInspectionsTestBase(RsTypeCheckInspection()) {
    private val ref = if (isExpectedMut) "&mut " else "&"
    private val fixName = "Convert to ${ref}A using `$trait` trait"

    fun `test Trait with A subs is impl for B`() = checkFixByText(fixName, """
        $imports

        struct A;
        struct B;

        impl $trait<A> for B { fn $method(&self) -> ${ref}A { ${ref}A } }

        fn main () {
            let a: ${ref}A = <error>B<caret></error>;
        }
    """, """
        $imports

        struct A;
        struct B;

        impl $trait<A> for B { fn $method(&self) -> ${ref}A { ${ref}A } }

        fn main () {
            let a: ${ref}A = B.$method();
        }
    """)

    fun `test Trait with C subs is impl for B`() = checkFixIsUnavailable(fixName, """
        $imports

        struct A;
        struct B;
        struct C;

        impl $trait<C> for B { fn $method(&self) -> ${ref}C { ${ref}C; } }

        fn main () {
            let a: ${ref}A = <error>B<caret></error>;
        }
    """)

    fun `test Trait is not impl for B`() = checkFixIsUnavailable(fixName, """
        struct A;
        struct B;

        fn main () {
            let a: ${ref}A = <error>B<caret></error>;
        }
    """)
}
