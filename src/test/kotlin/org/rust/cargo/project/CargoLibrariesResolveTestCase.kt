package org.rust.cargo.project

import org.rust.lang.RustTestCaseBase

class CargoLibrariesResolveTestCase : RustTestCaseBase() {
    override val dataPath: String = "todo"

    fun testResolve() {
        var main = """
            extern crate rand;

            use rand::distributions;

            mod foo;

            fn main() {
                let _ = distributions::nor<ref>mal::Normal::new(0.0, 1.0);
            }
        """

        val referenceOffset = main.indexOf("<ref>")
        check(referenceOffset > 0)
        main = main.replace("<ref>", "")
        check(false) {
            "TODO"
        }

        //        val mainFile = createProjectSubFile("src/main.rs", main)
        //        createProjectSubFile("src/foo.rs", "")
        //        importProject("""
        //            [package]
        //            name = "hello"
        //            version = "0.1.0"
        //            authors = ["Aleksey Kladov <aleksey.kladov@gmail.com>"]
        //
        //            [dependencies]
        //            rand = "=0.3.14"
        //        """)
        //
        //        val psiFile = PsiManager.getInstance(myProject).findFile(mainFile)
        //        val reference = psiFile?.findReferenceAt(referenceOffset)!!
        //        assertThat(reference.resolve()).isNotNull()
    }
}
