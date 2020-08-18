/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustPerformanceTests

import com.fasterxml.jackson.databind.json.JsonMapper
import java.io.File

/**
 * Provides ability to check plugin analysis on custom real project.
 * It's supposed to be used by CI
 */
class CustomRealProjectAnalysisTest : RsRealProjectAnalysisTest() {

    fun test() {
        val name = System.getenv(PROJECT_NAME) ?: error("$PROJECT_NAME variable is not specified")
        val path = System.getenv(PROJECT_PATH) ?: name
        val url = System.getenv(PROJECT_URL).orEmpty()
        val exclude = System.getenv(PROJECT_EXCLUDE_PATHS)?.takeIf { it.isNotEmpty() }?.split(",").orEmpty()
        val info = RealProjectInfo(name, path, url, exclude)

        val consumer = JsonConsumer(info)
        doTest(info, consumer)
    }

    companion object {
        const val PROJECT_NAME = "PROJECT_NAME"
        const val PROJECT_PATH = "PROJECT_PATH"
        const val PROJECT_URL = "PROJECT_URL"
        const val PROJECT_EXCLUDE_PATHS = "PROJECT_EXCLUDE_PATHS"
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
            JsonMapper().writerWithDefaultPrettyPrinter()
                .writeValue(File(testDir, "${info.name}.json"), annotations)
        }
    }
}
