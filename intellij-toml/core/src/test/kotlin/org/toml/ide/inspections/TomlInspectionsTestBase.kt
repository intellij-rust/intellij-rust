/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.inspections

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.openapi.util.io.StreamUtil
import org.toml.ide.annotator.TomlAnnotationTestBase
import org.toml.ide.annotator.TomlAnnotationTestFixture
import java.io.InputStreamReader
import kotlin.reflect.KClass

abstract class TomlInspectionsTestBase(
    private val inspectionClass: KClass<out InspectionProfileEntry>
) : TomlAnnotationTestBase() {

    override fun createAnnotationFixture(): TomlAnnotationTestFixture =
        TomlAnnotationTestFixture(this, myFixture, inspectionClasses = listOf(inspectionClass))

    private lateinit var inspection: InspectionProfileEntry

    override fun setUp() {
        super.setUp()
        inspection = annotationFixture.enabledInspections[0]
    }

    fun testInspectionHasDocumentation() {
        val description = "inspectionDescriptions/${inspection.javaClass.simpleName.dropLast("Inspection".length)}.html"
        val text = getResourceAsString(description)
            ?: error("No inspection description for ${inspection.javaClass} ($description)")
        checkHtmlStyle(text)
    }

    companion object {
        @JvmStatic
        fun checkHtmlStyle(html: String) {
            val re = "<body>(.*)</body>".toRegex(RegexOption.DOT_MATCHES_ALL)
            val body = (re.find(html)?.let { it.groups[1]!!.value } ?: html).trim()
            check(body[0].isUpperCase()) { "Please start description with the capital latter" }
            check(body.last() == '.') { "Please end description with a period" }
        }

        @JvmStatic
        fun getResourceAsString(path: String): String? {
            val stream = TomlInspectionsTestBase::class.java.classLoader.getResourceAsStream(path) ?: return null
            return StreamUtil.readText(InputStreamReader(stream, Charsets.UTF_8))
        }
    }
}
