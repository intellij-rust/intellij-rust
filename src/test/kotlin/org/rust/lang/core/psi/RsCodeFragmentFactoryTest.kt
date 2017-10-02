/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi

import org.rust.cargo.project.model.cargoProjects
import org.rust.lang.RsTestBase

class RsCodeFragmentFactoryTest : RsTestBase() {
    fun `test resolve string path`() {
        InlineFile("mod foo { struct S; }")
        val target = project.cargoProjects.allProjects.single().workspace!!.packages.single().targets.first()
        val path = RsCodeFragmentFactory(project).createCrateRelativePath("::foo::S", target)
        val declaration = path!!.reference.resolve()
        check((declaration as RsStructItem).name == "S")
    }

    fun `test resolve local variable by name`() {
        InlineFile("""
            fn foo() {
                let x = 92;
                loop { println!("", x) }
                //^
            }
        """)
        val loop = findElementInEditor<RsLoopExpr>()
        val path = RsCodeFragmentFactory(project).createPath("x", loop)
        val declaration = path!!.reference.resolve()
        check((declaration as RsPatBinding).name == "x")
    }
}
