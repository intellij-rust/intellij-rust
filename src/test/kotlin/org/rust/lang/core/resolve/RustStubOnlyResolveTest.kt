package org.rust.lang.core.resolve

import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.psi.impl.PsiManagerImpl
import org.intellij.lang.annotations.Language
import org.rust.lang.core.psi.RustReferenceElement

class RustStubOnlyResolveTest : RustResolveTestBase() {
    fun testChildMod() = doTest("""
    //- main.rs
        mod child;

        fn main() {
            child::foo();
                  //^ child.rs
        }

    //- child.rs
        pub fn foo() {}
    """)

    fun testNestedChildMod() = doTest("""
    //- main.rs
        mod inner {
            pub mod child;
        }

        fn main() {
            inner::child::foo();
                         //^ inner/child.rs
        }

    //- inner/child.rs
        fn foo() {}
    """)

    fun testModDecl() = doTest("""
    //- main.rs
        mod foo;
           //^ foo.rs

        fn main() {}
    //- foo.rs

        // Empty file
    """)

    fun testModDecl2() = doTest("""
    //- foo/mod.rs
        use bar::Bar;
                //^ bar.rs

    //- main.rs
        mod bar;
        mod foo;

        fn main() {}

    //- bar.rs
        struct Bar {}
    """)

    private fun doTest(@Language("Rust") code: String) {
        val fileSeparator = """^\s* //- (\S+)\s*$""".toRegex(RegexOption.MULTILINE)
        val fileNames = fileSeparator.findAll(code).map { it.groupValues[1] }.toList()
        val fileTexts = fileSeparator.split(code).filter(String::isNotBlank)

        check(fileNames.size == fileTexts.size)
        for ((name, text) in fileNames.zip(fileTexts)) {
            myFixture.tempDirFixture.createFile(name, text)
        }
        (psiManager as PsiManagerImpl)
            .setAssertOnFileLoadingFilter(VirtualFileFilter { file ->
                !file.path.endsWith(fileNames[0])
            }, testRootDisposable)

        myFixture.configureFromTempProjectFile(fileNames[0])
        val (reference, resolveFile) = findElementAndDataInEditor<RustReferenceElement>()
        val expectedResolveFile = myFixture.findFileInTempDir(resolveFile)
            ?: error("Not `$resolveFile` file")

        val element = reference.reference.resolve()
            ?: error("Failed to resolve ${reference.text}")

        val actualResolveFile = element.containingFile.virtualFile
        check(actualResolveFile == expectedResolveFile) {
            "Should resolve to ${expectedResolveFile.path}, was ${actualResolveFile.path} instead"
        }
    }
}
