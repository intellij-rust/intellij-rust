package org.rust.lang.core.psi

import org.rust.cargo.project.workspace.cargoWorkspace
import org.rust.lang.RsTestBase

class RsCodeFragmentFactoryTest : RsTestBase() {
    override val dataPath: String = ""

    fun `test resolve string path`() {
        InlineFile("mod foo { struct S; }")
        val target = myModule.cargoWorkspace!!.packages.single().targets.first()
        val path = RsCodeFragmentFactory(project).createCrateRelativePath("::foo::S", target)
        val declaration = path!!.reference.resolve()
        check((declaration as RsStructItem).name == "S")
    }

    fun `test resolve local vaiable by name`() {
        InlineFile("""
            fn foo() {
                let x = 92;
                loop { println!("", x) }
                //^
            }
        """)
        val loop = findElementInEditor<RsLoopExpr>()
        val path = RsCodeFragmentFactory(project).createLocalVariable("x", loop)
        val declaration = path!!.reference.resolve()
        check((declaration as RsPatBinding).name == "x")
    }
}

