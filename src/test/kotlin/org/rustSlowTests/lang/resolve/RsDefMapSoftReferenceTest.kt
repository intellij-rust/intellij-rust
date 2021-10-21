/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustSlowTests.lang.resolve

import org.intellij.lang.annotations.Language
import org.rust.lang.core.psi.ext.RsNamedElement
import org.rust.lang.core.psi.ext.RsReferenceElement
import org.rust.lang.core.resolve.RsResolveTestBase
import org.rust.lang.core.resolve2.DefMapHolder
import org.rust.lang.core.resolve2.DefMapService
import org.rust.lang.core.resolve2.defMapService

/** See [DefMapService.defMaps] for details */
class RsDefMapSoftReferenceTest : RsResolveTestBase() {

    fun test() = doTest("""
        fn foo() {}
         //X
        fn main() {
            foo();
        } //^
    """)

    fun doTest(@Language("Rust") code: String) {
        InlineFile(code)
        val element = findElementInEditor<RsReferenceElement>()
        val reference = element.reference ?: error("Failed to get reference for `${element.text}`")
        val target = findElementInEditor<RsNamedElement>("X")

        check(reference.resolve() == target)

        /** [DefMapHolder] is stored under soft reference */
        project.defMapService.forceClearSoftReferences()
        check(reference.resolve() == target)
    }
}
