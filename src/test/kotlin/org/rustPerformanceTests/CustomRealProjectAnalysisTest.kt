/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustPerformanceTests

import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.util.*

/**
 * Provides ability to check plugin analysis on custom real project.
 * It's supposed to be used by CI
 */
@RunWith(Parameterized::class)
class CustomRealProjectAnalysisTest(
    @Suppress("unused") private val projectName: String,
    private val info: RealProjectInfo,
    analyzeDependencies: Boolean,
) : RsRealProjectAnalysisTest(analyzeDependencies) {

    @Test
    fun test() {
        val consumer = JsonConsumer(info)
        doTest(info, consumer)
    }

    companion object {

        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun data(): Collection<Array<Any>> {
            val projectsStr = System.getenv("PROJECTS") ?: error("Can't find `PROJECTS` env variable")
            val projects = JsonMapper().registerKotlinModule().readValue<List<RealProjectInfo>>(projectsStr)
            val analyzeDependencies = System.getenv("ANALYZE_DEPENDENCIES").toBooleanStrict()
            return projects.map { arrayOf(it.name, it, analyzeDependencies) }
        }
    }

    private class JsonConsumer(private val info: RealProjectInfo): AnnotationConsumer {

        private val annotations = mutableListOf<Annotation>()

        override fun consumeAnnotation(annotation: Annotation) {
            annotations += annotation
        }

        // Should be synchronized with `scripts/calculate_regressions.py`
        override fun finish() {
            val testDir = File("regressions")
            testDir.mkdirs()
            val resultSuffix = System.getenv("RESULT_SUFFIX").orEmpty()
            JsonMapper().writerWithDefaultPrettyPrinter()
                .writeValue(File(testDir, "${info.name}$resultSuffix.json"), annotations.sortedWith(
                    Comparator.comparing(Annotation::filePath)
                        .thenComparingInt(Annotation::line)
                        .thenComparingInt(Annotation::column)
                ))
        }
    }
}
