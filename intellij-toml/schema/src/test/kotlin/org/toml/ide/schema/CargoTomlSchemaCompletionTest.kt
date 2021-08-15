/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.schema

class CargoTomlSchemaCompletionTest : CargoTomlSchemaCompletionTestBase() {
    fun `test top level completion`() = checkContainsCompletion(
        listOf("package", "features", "dependencies"),
        """
            <caret>
        """
    )

    fun `test completion inside table`() = checkContainsCompletion(
        listOf("name", "version", "authors", "edition"),
        """
            [package]
            <caret>
        """
    )

    fun `test completion in key segments`() = checkContainsCompletion(
        listOf("name", "version", "authors", "edition"),
        """
            package.<caret>
        """
    )

    fun `test competion in table header`() = checkContainsCompletion(
        listOf("name", "version", "authors", "edition"),
        """
            [package.<caret>]
        """
    )

    fun `test completion in table key`() = checkContainsCompletion(
        listOf("dependencies", "dev-dependencies", "features", "package"),
        """
            [<caret>]
        """
    )

    fun `test completion inside inline tables`() = checkContainsCompletion(
        listOf("name", "version", "authors", "edition"),
        """
            package = { <caret> }
        """
    )

    fun `test completion in variable path`() = checkContainsCompletion(
        listOf("version", "features"),
        """
            [dependencies]
            foo = { <caret> }
        """
    )
}
