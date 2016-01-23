package org.rust.cargo

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.PlatformTestCase
import org.rust.cargo.project.RustSdkType

abstract class CargoTestCaseBase : PlatformTestCase() {
    abstract val testDataPath: String

    private val sdkPath: String? get() = RustSdkType.INSTANCE.suggestHomePath()

    override fun runTest() {
        if (sdkPath == null) {
            System.err?.println("SKIP $name: no Rust sdk found")
            return
        }
        super.runTest()
    }

    override fun getTestProjectJdk(): Sdk? = sdkPath?.let {
        SdkConfigurationUtil.createAndAddSDK(it, RustSdkType.INSTANCE)!!
    }

    override fun setUp() {
        super.setUp()
        val data = LocalFileSystem.getInstance().findFileByPath(testDataPath)
            ?: throw RuntimeException("No such directory: $testDataPath")

        data.refresh(/* asynchronous = */ false, /* recursive = */ true)
        copyDirContentsTo(data, project.baseDir)
    }

}
