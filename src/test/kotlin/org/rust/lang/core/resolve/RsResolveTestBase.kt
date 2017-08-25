/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.psi.impl.PsiManagerEx
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.rust.fileTreeFromText
import org.rust.lang.RsTestBase
import org.rust.lang.core.psi.ext.RsNamedElement
import org.rust.lang.core.psi.ext.RsReferenceElement

abstract class RsResolveTestBase : RsTestBase() {
    open protected fun checkByCode(@Language("Rust") code: String) {
        InlineFile(code)

        val (refElement, data) = findElementAndDataInEditor<RsReferenceElement>("^")

        if (data == "unresolved") {
            val resolved = refElement.reference.resolve()
            check(resolved == null) {
                "$refElement `${refElement.text}`should be unresolved, was resolved to\n$resolved `${resolved?.text}`"
            }
            return
        }

        val resolved = refElement.reference.resolve()
        if (resolved == null) {
            val multiResolve = refElement.reference.multiResolve()
            check(multiResolve.size != 1)
            if (multiResolve.isEmpty()) {
                fail("Failed to resolve ${refElement.text}")
            } else {
                fail("Failed to resolve ${refElement.text}, multiple variants:\n${multiResolve.joinToString()}")
            }
        }

        val target = findElementInEditor<RsNamedElement>("X")

        assertThat(resolved).isEqualTo(target)
    }

    protected fun stubOnlyResolve(@Language("Rust") code: String) {
        val testProject = fileTreeFromText(code)
            .createAndOpenFileWithCaretMarker()

        checkAstNotLoaded(VirtualFileFilter { file ->
            !file.path.endsWith(testProject.fileWithCaret)
        })

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
}
