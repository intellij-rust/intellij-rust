package org.rust.lang.core.resolve

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.LanguageLevelModuleExtension
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import org.assertj.core.api.Assertions.assertThat
import org.rust.cargo.project.module.RustExecutableModuleType
import org.rust.lang.core.resolve.ref.RustReference


class RustMultiFileResolveTestCase : RustResolveTestCaseBase() {

    override fun getProjectDescriptor(): LightProjectDescriptor {
        return object : DefaultLightProjectDescriptor() {

            override fun getModuleType(): ModuleType<*> = RustExecutableModuleType.INSTANCE

            override fun createMainModule(project: Project): Module {
                return super.createMainModule(project)
            }
        }
    }

    private fun configureByFile(path: String) {
        myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject(path, StringUtil.getShortName(path, '/')));
    }

    private fun doTest(vararg files: String) {
        // myFixture.configureByFiles(*files)

        files.reversed().forEach {
            configureByFile(it)
        }

        /*
        buildModulesIndex(myFixture.tempDirFixture.getFile(files.first()))
        */

        val usage = file.findReferenceAt(myFixture.caretOffset)!! as RustReference
        val declaration = usage.resolve()

        assertThat(declaration).isNotNull()
    }

    /*
    private fun buildModulesIndex(head: VirtualFile) {
        myModule.getComponent(RustModulesIndexImpl::class.java)!!
                .buildFrom(listOf(head.parent))
    }
    */

    //
    // NOTA BENE:
    //  First file supplied would be designated as Crate's root module
    //

    fun testChildMod()          = doTest("child_mod/main.rs", "child_mod/child.rs")
    fun testNestedChildMod()    = doTest("nested_child_mod/main.rs", "nested_child_mod/inner/child.rs")
    fun testGlobalPath()        = doTest("global_path/foo.rs", "global_path/lib.rs", "global_path/bar.rs")
    fun testUseViewPath()       = doTest("global_path/foo.rs", "global_path/lib.rs", "global_path/bar.rs")
    fun testModDecl()           = doTest("mod_decl/main.rs", "mod_decl/foo.rs")
    fun testModDeclNonOwn()     = doTest("mod_decl_non_own/foo.rs", "mod_decl_non_own/bar.rs")
}
