package org.rust.lang.core.resolve

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import org.assertj.core.api.Assertions.assertThat
import org.rust.cargo.CargoProjectDescription
import org.rust.cargo.project.RustSdkType
import org.rust.cargo.project.module.RustModuleType
import org.rust.cargo.project.module.persistence.CargoModuleService
import org.rust.cargo.project.module.persistence.ExternCrateData
import org.rust.cargo.util.getService
import org.rust.lang.RustTestCase
import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.resolve.ref.RustReference


abstract class RustMultiFileResolveTestCaseBase : RustResolveTestCaseBase() {

    abstract val targets: Collection<CargoProjectDescription.Target>

    open val externCrates: Collection<ExternCrateData> = emptyList()

    open val needsSdkSources: Boolean = false

    final override fun getProjectDescriptor(): LightProjectDescriptor = object : DefaultLightProjectDescriptor() {

        override fun getModuleType(): ModuleType<*> = RustModuleType.INSTANCE

        override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
            super.configureModule(module, model, contentEntry)
            module.getService<CargoModuleService>().saveData(targets, externCrates)
        }

        override fun getSdk(): Sdk? {
            if (!needsSdkSources) return null
            val sdk = ProjectJdkImpl("RustTest", RustSdkType.INSTANCE)
            val sdkModificator = sdk.sdkModificator

            val sdkSrc = "${RustTestCase.testResourcesPath}/rustc-nightly/src"
            val sdkSrcFile = LocalFileSystem.getInstance().findFileByPath(sdkSrc)
            sdkModificator.addRoot(sdkSrcFile, OrderRootType.CLASSES)
            sdkModificator.commitChanges()
            return sdk
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

        val usage = myFixture.file.findReferenceAt(myFixture.caretOffset)!! as RustReference

        return usage.resolve()
    }

    protected fun binTarget(path: String): CargoProjectDescription.Target =
        CargoProjectDescription.Target(path, CargoProjectDescription.TargetKind.BIN)

    protected fun libTarget(path: String): CargoProjectDescription.Target =
        CargoProjectDescription.Target(path, CargoProjectDescription.TargetKind.LIB)
}
