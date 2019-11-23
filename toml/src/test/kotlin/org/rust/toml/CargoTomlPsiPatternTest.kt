/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml

import com.intellij.patterns.ElementPattern
import com.intellij.psi.PsiElement
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.toml.CargoTomlPsiPattern.buildPath
import org.rust.toml.CargoTomlPsiPattern.inDependencyKeyValue
import org.rust.toml.CargoTomlPsiPattern.inKey
import org.rust.toml.CargoTomlPsiPattern.inSpecificDependencyHeaderKey
import org.rust.toml.CargoTomlPsiPattern.inSpecificDependencyKeyValue
import org.rust.toml.CargoTomlPsiPattern.onDependencyKey
import org.rust.toml.CargoTomlPsiPattern.onSpecificDependencyHeaderKey
import org.rust.toml.CargoTomlPsiPattern.packageWorkspacePath
import org.rust.toml.CargoTomlPsiPattern.path
import org.rust.toml.CargoTomlPsiPattern.workspacePath

class CargoTomlPsiPatternTest : RsTestBase() {

    fun `test inKey header 1`() = testPattern(inKey, """
        [key]
        #^
    """)

    fun `test inKey header 2`() = testPattern(inKey, """
        [key.key]
            #^
    """)

    fun `test inKey header in Xargo toml file`() = testPattern(inKey, """
        [key.key]
            #^
    """, fileName = "Xargo.toml")

    fun `test inKey table keyValue`() = testPattern(inKey, """
        [header]
        key = ""
        #^
    """)

    fun `test inKey inline table keyValue`() = testPattern(inKey, """
        [header]
        key = { key = "" }
               #^
    """)

    fun `test onDependencyKey dependencies`() = testPattern(onDependencyKey, """
        [dependencies]
        key = ""
        #^
    """)

    fun `test onDependencyKey dev-dependencies`() = testPattern(onDependencyKey, """
        [dev-dependencies]
        key = ""
        #^
    """)

    fun `test onDependencyKey build-dependencies`() = testPattern(onDependencyKey, """
        [build-dependencies]
        key = ""
        #^
    """)

    fun `test onDependencyKey target_'cfg(windows)'_dependencies`() = testPattern(onDependencyKey, """
        [target.'cfg(windows)'.dependencies]
        key = ""
        #^
    """)

    fun `test inDependencyKeyValue 1`() = testPattern(inDependencyKeyValue, """
        [dependencies]
        key = ""
           #^
    """)

    fun `test inDependencyKeyValue 2`() = testPattern(inDependencyKeyValue, """
        [dependencies]
        key = ""
             #^
    """)

    fun `test inDependencyKeyValue 3`() = testPattern(inDependencyKeyValue, """
        [dependencies]
        key = "" ""
                #^
    """)

    fun `test inDependencyKeyValue 4`() = testPatternNegative(inDependencyKeyValue, """
        [dependencies]
        key = { key = "" }
                #^
    """)

    fun `test onSpecificDependencyHeaderKey 1`() = testPattern(onSpecificDependencyHeaderKey, """
        [dependencies.key]
                     #^
    """)

    fun `test onSpecificDependencyHeaderKey 2`() = testPatternNegative(onSpecificDependencyHeaderKey, """
        [dependencies.key]
              #^
    """)

    fun `test inSpecificDependencyHeaderKey`() = testPattern(inSpecificDependencyHeaderKey, """
        [dependencies.key]
                     #^
    """)

    fun `test inSpecificDependencyKeyValue 1`() = testPattern(inSpecificDependencyKeyValue, """
        [dependencies.key]
        version = ""
        #^
    """)

    fun `test inSpecificDependencyKeyValue 2`() = testPattern(inSpecificDependencyKeyValue, """
        [dependencies.key]
        version = ""
                 #^
    """)

    fun `test inSpecificDependencyKeyValue 3`() = testPatternNegative(inSpecificDependencyKeyValue, """
        [dependencies]
        version = ""
        #^
    """)

    fun `test workspace member path`() = testPattern(workspacePath, """
        [workspace]
        members = [ "path/to/crate" ]
                        #^
    """)

    fun `test workspace default member path`() = testPattern(workspacePath, """
        [workspace]
        default-members = [ "path/to/crate" ]
                                 #^
    """)

    fun `test package workspace path`() = testPattern(packageWorkspacePath, """
        [package]
        workspace = "path/to/root/crate"
                         #^
    """)

    fun `test path`() = testPattern(path, """
        path = "path/to/crate"
                   #^
    """)

    fun `test path in inline table`() = testPattern(path, """
        crate_name = { path = "path/to/crate" }
                                   #^
    """)

    fun `test build path`() = testPattern(buildPath, """
        [package]
        build = "build.rs"
                  #^
    """)

    private inline fun <reified T : PsiElement> testPattern(
        pattern: ElementPattern<T>,
        @Language("Toml") code: String,
        fileName: String = "Cargo.toml"
    ) {
        InlineFile(code, fileName)
        val element = findElementInEditor<T>()
        assertTrue(pattern.accepts(element))
    }

    private fun <T> testPatternNegative(pattern: ElementPattern<T>, @Language("Toml") code: String) {
        InlineFile(code, "Cargo.toml")
        val element = findElementInEditor<PsiElement>()
        assertFalse(pattern.accepts(element))
    }
}
