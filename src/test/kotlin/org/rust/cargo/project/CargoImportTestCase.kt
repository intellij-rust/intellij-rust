package org.rust.cargo.project

class CargoImportTestCase : CargoImportTestCaseBase() {
    fun testModuleStructure() {
        createProjectSubFile("src/main.rs", "fn main() {}")
        createProjectSubFile("src/lib.rs", "")
        importProject("""
            [package]
            name = "hello"
            version = "0.1.0"
            authors = ["Aleksey Kladov <aleksey.kladov@gmail.com>"]

            [dependencies]
            libc = "0.2.7"
        """)

        assertModules("hello")
        assertModuleLibDep("hello", "Cargo: libc 0.2.7")

        assertTargets("src/main.rs", "src/lib.rs")
        assertExternCrates("libc")
    }
}
