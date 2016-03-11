package org.rust

import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.LightProjectDescriptor
import org.rust.cargo.project.RustSdkType
import org.rust.cargo.project.module.RustModuleType
import org.rust.lang.RustTestCase
import org.rust.lang.RustTestCaseBase

abstract class RustWithSdkTestCaseBase : RustTestCaseBase() {
    final override fun getProjectDescriptor(): LightProjectDescriptor = object : LightProjectDescriptor() {
        override fun getSdk(): Sdk? {
            val sdk = ProjectJdkImpl("RustTest", RustSdkType.INSTANCE)
            val sdkModificator = sdk.sdkModificator

            val sdkSrc = "${RustTestCase.testResourcesPath}/rustc-nightly/src"
            val sdkSrcFile = LocalFileSystem.getInstance().findFileByPath(sdkSrc)
            sdkModificator.addRoot(sdkSrcFile, OrderRootType.CLASSES)
            sdkModificator.commitChanges()
            return sdk
        }

        override fun getModuleType(): ModuleType<*> = RustModuleType.INSTANCE
    }
}
