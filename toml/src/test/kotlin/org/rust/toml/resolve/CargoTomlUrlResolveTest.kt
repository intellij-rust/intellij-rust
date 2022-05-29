/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.resolve

import com.intellij.openapi.paths.WebReference
import org.intellij.lang.annotations.Language
import org.rust.IgnoreInPlatform
import org.rust.RsTestBase

class CargoTomlUrlResolveTest : RsTestBase() {

    // BACKCOMPAT: 2022.1. Drop it
    @IgnoreInPlatform(222)
    fun `test no reference for package name`() = checkNoUrlReference("""
        [package]
        name = "<caret>https://github.com/foo/bar"
    """)

    fun `test no reference if literal is not absolute URL`() = checkNoUrlReference("""
        [package]
        documentation = "<caret>github.com/foo/bar"
    """)

    fun `test reference http`() = checkUrlReference("""
        [package]
        homepage = "<caret>http://github.com/foo/bar"
    """, "http://github.com/foo/bar")

    fun `test reference in homepage`() = checkUrlReference("""
        [package]
        homepage = "<caret>https://github.com/foo/bar"
    """, "https://github.com/foo/bar")

    fun `test reference in repository`() = checkUrlReference("""
        [package]
        repository = "<caret>https://github.com/foo/bar"
    """, "https://github.com/foo/bar")

    fun `test reference in documentation`() = checkUrlReference("""
        [package]
        homepage = "<caret>https://github.com/foo/bar"
    """, "https://github.com/foo/bar")

    fun `test reference in dependency git URL`() = checkUrlReference("""
        [dependencies]
        foo = { git = "<caret>https://github.com/foo/bar" }
    """, "https://github.com/foo/bar")

    fun `test reference in specific dependency git URL`() = checkUrlReference("""
        [dependencies.foo]
        git = "<caret>https://github.com/foo/bar"
    """, "https://github.com/foo/bar")

    private fun checkUrlReference(@Language("TOML") code: String, url: String) {
        InlineFile(code, "Cargo.toml")
        val reference = myFixture.getReferenceAtCaretPosition()
        assertInstanceOf(reference, WebReference::class.java)
        assertEquals(url, (reference as WebReference).url)
    }

    private fun checkNoUrlReference(@Language("TOML") code: String) {
        InlineFile(code, "Cargo.toml")
        assertNull(myFixture.getReferenceAtCaretPosition())
    }
}
