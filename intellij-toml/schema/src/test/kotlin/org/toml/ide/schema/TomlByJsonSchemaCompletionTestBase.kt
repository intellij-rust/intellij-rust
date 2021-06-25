/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.schema

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.application.WriteAction
import com.intellij.psi.PsiFile
import com.intellij.testFramework.EditorTestUtil
import com.jetbrains.jsonSchema.impl.JsonSchemaReader
import org.intellij.lang.annotations.Language
import org.toml.TomlTestBase
import java.io.IOException

abstract class TomlByJsonSchemaCompletionTestBase : TomlTestBase() {
    private var myItems: List<LookupElement>? = null

    override fun tearDown() {
        myItems = null
        super.tearDown()
    }

    protected fun testBySchema(
        @Language("JSON") schema: String,
        @Language("TOML") text: String,
        vararg variants: String,
    ) {
        val filename = "text.toml"

        val position = EditorTestUtil.getCaretPosition(text)
        assert(position >= 0)
        val completionText = text.replace("<caret>", "IntelliJIDEARulezzz")

        val fileInTemp = myFixture.findFileInTempDir(filename)
        if (fileInTemp != null) {
            WriteAction.run<IOException> { fileInTemp.delete(null) }
        }

        val file = myFixture.addFileToProject(filename, completionText.trimIndent())
        val element = file.findElementAt(position)
        assert(element != null)

        val schemaInTemp = myFixture.findFileInTempDir("testSchema.json")
        if (schemaInTemp != null) {
            WriteAction.run<IOException> { schemaInTemp.delete(null) }
        }

        val schemaFile: PsiFile = myFixture.addFileToProject("testSchema.json", schema.trimIndent())
        val schemaObject = JsonSchemaReader.readFromFile(project, schemaFile.virtualFile)

        val foundVariants = TomlJsonSchemaCompletionContributor.getCompletionVariants(schemaObject, element!!, element)
            .sortedBy { it.lookupString }
        val actual = foundVariants.map { it.lookupString }
        assertOrderedEquals(actual, *variants)
        myItems = foundVariants
    }
}
