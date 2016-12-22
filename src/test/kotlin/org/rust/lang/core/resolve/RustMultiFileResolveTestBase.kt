package org.rust.lang.core.resolve

import org.assertj.core.api.Assertions.assertThat
import org.rust.lang.core.psi.RustCompositeElement
import org.rust.lang.core.resolve.ref.RustReference


abstract class RustMultiFileResolveTestBase : RustResolveTestBase() {

    private fun trimDir(path: String): String {
        val idx = path.substring(1).indexOfFirst {
            it == '/'
        } + 1
        return path.substring(idx)
    }

    private fun configureByFile(file: String) {
        myFixture.configureFromExistingVirtualFile(
            myFixture.copyFileToProject(file, trimDir(file))
        )
    }

    protected fun doTestResolved(vararg files: String) {
        assertThat(configureAndResolve(*files)).isNotNull()
    }

    protected fun doTestUnresolved(vararg files: String) {
        assertThat(configureAndResolve(*files)).isNull()
    }

    protected fun configureAndResolve(vararg files: String): RustCompositeElement? {
        files.reversed().forEach {
            configureByFile(it)
        }

        val usage = myFixture.file.findReferenceAt(myFixture.caretOffset)!! as RustReference

        return usage.resolve()
    }
}
