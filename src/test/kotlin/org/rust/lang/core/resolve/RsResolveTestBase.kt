/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import com.intellij.openapi.vfs.VirtualFileFilter
import org.intellij.lang.annotations.Language
import org.rust.fileTreeFromText
import org.rust.lang.RsTestBase
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.RsNamedElement
import org.rust.lang.core.psi.ext.RsReferenceElement
import org.rust.openapiext.Testmark

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

        val resolved = refElement.checkedResolve()
        val target = findElementInEditor<RsNamedElement>("X")

        check(resolved == target) {
            "$refElement `${refElement.text}` should resolve to $target, was $resolved instead"
        }
    }

    protected fun checkByCode(@Language("Rust") code: String, mark: Testmark) =
        mark.checkHit { checkByCode(code) }

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

        val element = reference.checkedResolve()
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

    protected fun stubOnlyResolve(@Language("Rust") code: String, mark: Testmark) =
        mark.checkHit { stubOnlyResolve(code) }
}

private fun RsReferenceElement.checkedResolve(): RsElement {
    return reference.resolve() ?: run {
        val multiResolve = reference.multiResolve()
        check(multiResolve.size != 1)
        if (multiResolve.isEmpty()) {
            error("Failed to resolve $text")
        } else {
            error("Failed to resolve $text, multiple variants:\n${multiResolve.joinToString()}")
        }
    }
}
