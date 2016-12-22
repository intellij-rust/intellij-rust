package org.rust.lang.core.resolve

import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.psi.impl.PsiManagerImpl
import org.intellij.lang.annotations.Language
import org.rust.lang.core.psi.RustReferenceElement

class RustStubOnlyResolveTestCase : RustResolveTestCaseBase() {
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


    private fun doTest(@Language("Rust") code: String) {
        val fileSeparator = """^\s* //- (\S+)\s*$""".toRegex(RegexOption.MULTILINE)
        val fileNames = fileSeparator.findAll(code).map { it.groupValues[1] }.toList()
        val fileTexts = fileSeparator.split(code).filter(String::isNotBlank)

        check(fileNames.size == fileTexts.size)
        for ((name, text) in fileNames.zip(fileTexts).drop(1)) {
            myFixture.tempDirFixture.createFile(name, text)
        }
        (psiManager as PsiManagerImpl)
            .setAssertOnFileLoadingFilter(VirtualFileFilter { file ->
                !file.path.endsWith(fileNames[0])
            }, testRootDisposable)

        val (reference, resolveFile) = InlineFile(fileTexts[0], fileNames[0]).elementAndData<RustReferenceElement>()
        val expectedResolveFile = myFixture.tempDirFixture.getFile(resolveFile)
            ?: error("Not `$resolveFile` file")

        val element = reference.reference.resolve()
            ?: error("Failed to resolve ${reference.text}")

        val actualResolveFile = element.containingFile.virtualFile
        check(actualResolveFile == expectedResolveFile) {
            "Should resolve to ${expectedResolveFile.path}, was ${actualResolveFile.path} instead"
        }
    }
}
