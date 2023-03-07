/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hierarchy

import com.intellij.ide.hierarchy.HierarchyTreeStructure
import com.intellij.testFramework.codeInsight.hierarchy.HierarchyViewTestFixture
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.lang.core.psi.ext.RsQualifiedNamedElement

abstract class RsCallHierarchyTestBase : RsTestBase() {
    protected abstract val type: HierarchyType

    protected fun doTest(@Language("Rust") code: String, expected: String) {
        InlineFile(code).withCaret()
        val hierarchy = createHierarchy(type)
        checkHierarchy(hierarchy, expected)
    }

    private fun checkHierarchy(structure: HierarchyTreeStructure, expectedStructure: String) {
        HierarchyViewTestFixture.doHierarchyTest(structure, expectedStructure.trimIndent())
    }

    private fun createHierarchy(type: HierarchyType): HierarchyTreeStructure {
        val element = myFixture.elementAtCaret as RsQualifiedNamedElement
        return when (type) {
            HierarchyType.Callee -> RsCalleeTreeStructure(element)
        }
    }

    protected enum class HierarchyType {
        Callee
    }
}
