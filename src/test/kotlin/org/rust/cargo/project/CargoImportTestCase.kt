package org.rust.cargo.project

import com.intellij.openapi.roots.ProjectRootManager
import org.assertj.core.api.Assertions.assertThat
import org.rust.lang.RustTestCaseBase

class CargoImportTestCase : RustTestCaseBase() {
    override val dataPath = "todo"

    fun testModuleStructure() {
        check(false) {"write me"}
//        createProjectSubFile("src/main.rs", "fn main() {}")
//        createProjectSubFile("src/lib.rs", "")
//        importProject("""
//            [package]
//            name = "hello"
//            version = "0.1.0"
//            authors = ["Aleksey Kladov <aleksey.kladov@gmail.com>"]
//
//            [dependencies]
//            libc = "=0.2.7"
//        """)
//
//        assertModules("hello")
//        assertModuleLibDep("hello", "Cargo: libc 0.2.7")
//
//        assertTargets("src/main.rs", "src/lib.rs")
//        assertExternCrates("libc", "hello")

//        val sdk = ProjectRootManager.getInstance(myTestFixture.project).projectSdk
//        assertThat(sdk).isNotNull()
    }
}
