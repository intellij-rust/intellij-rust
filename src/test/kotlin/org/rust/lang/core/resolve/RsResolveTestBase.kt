package org.rust.lang.core.resolve

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.psi.impl.PsiManagerEx
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.rust.lang.RsTestBase
import org.rust.lang.core.psi.RsNamedElement
import org.rust.lang.core.psi.RsReferenceElement

abstract class RsResolveTestBase : RsTestBase() {

    final override val dataPath = ""

    protected fun checkByCode(@Language("Rust") code: String) {
        InlineFile(code)

        val (refElement, data) = findElementAndDataInEditor<RsReferenceElement>("^")

        if (data == "unresolved") {
            val resolved = refElement.reference.resolve()
            check(resolved == null) {
                "$refElement `${refElement.text}`should be unresolved, was resolved to\n$resolved `${resolved?.text}`"
            }
            return
        }

        val resolved = checkNotNull(refElement.reference.resolve()) {
            "Failed to resolve ${refElement.text}"
        }

        val target = findElementInEditor<RsNamedElement>("X")

        assertThat(resolved).isEqualTo(target)
    }

    protected fun stubOnlyResolve(@Language("Rust") code: String) {
        val files = ProjectFile.parseFileCollection(code)
        for ((path, text) in files) {
            myFixture.tempDirFixture.createFile(path, text)
        }

        PsiManagerEx.getInstanceEx(project)
            .setAssertOnFileLoadingFilter(VirtualFileFilter { file ->
                !file.path.endsWith(files[0].path)
            }, testRootDisposable)

        myFixture.configureFromTempProjectFile(files[0].path)

        val (reference, resolveFile) = findElementAndDataInEditor<RsReferenceElement>()

        if (resolveFile == "unresolved") {
            val element = reference.reference.resolve()
            if (element != null) {
                error("Should not resolve ${reference.text} to ${element.text}")
            }
            return
        }

        val element = reference.reference.resolve()
            ?: error("Failed to resolve ${reference.text}")
        val actualResolveFile = element.containingFile.virtualFile

        if (resolveFile.startsWith("...")) {
            check(actualResolveFile.path.endsWith(resolveFile.drop(3))) {
                "Should resolve to $resolveFile, was ${actualResolveFile.path} instead"
            }
        } else {
            val expectedResolveFile = myFixture.findFileInTempDir(resolveFile)
                ?: error("Can't find `$resolveFile` file")

            check(actualResolveFile == expectedResolveFile) {
                "Should resolve to ${expectedResolveFile.path}, was ${actualResolveFile.path} instead"
            }
        }
    }

    // BACKCOMPAT: 2016.2
    // See org.rust.lang.core.psi.impl.RsStubbedElementImpl.WithParent
    protected fun is2016_2(): Boolean {
        val info = ApplicationInfo.getInstance()
        return (info.majorVersion == "2016" && info.minorVersion == "2")
    }
}
