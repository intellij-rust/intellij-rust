package org.rust.lang.core.resolve

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.project.Project
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import org.assertj.core.api.Assertions.assertThat
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.resolve.ref.RustReference
import java.io.File


abstract class RustMultiFileResolveTestCaseBase : RustResolveTestCaseBase() {

    abstract val moduleType: ModuleType<*>

    override fun getProjectDescriptor(): LightProjectDescriptor {
        return object : DefaultLightProjectDescriptor() {

            override fun getModuleType(): ModuleType<*> = moduleType

            override fun createMainModule(project: Project): Module {
                return super.createMainModule(project)
            }
        }
    }

    private fun trimDir(path: String): String {
        val idx = path.substring(1).indexOfFirst {
            it == '/'
        } + 1
        return path.substring(idx)
    }

    private fun configureByFile(file: String) {
        myFixture.configureFromExistingVirtualFile(
            myFixture.copyFileToProject(file, trimDir(file))
        );
    }

    protected fun doTestResolved(vararg files: String) {
        assertThat(configureAndResolve(*files)).isNotNull()
    }

    protected fun doTestUnresolved(vararg files: String) {
        assertThat(configureAndResolve(*files)).isNull()
    }

    protected fun configureAndResolve(vararg files: String): RustNamedElement? {
        files.reversed().forEach {
            configureByFile(it)
        }

        val usage = file.findReferenceAt(myFixture.caretOffset)!! as RustReference

        return usage.resolve()
    }
}
