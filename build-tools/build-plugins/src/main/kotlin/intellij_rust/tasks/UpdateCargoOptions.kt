/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package intellij_rust.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.jsoup.Jsoup
import java.io.Writer

@CacheableTask
abstract class UpdateCargoOptions : DefaultTask() {

    /** The `CargoOptions.kt` that will be created by this task */
    @get:OutputFile
    abstract val cargoOptions: RegularFileProperty

    /** The base URL for all cargo commands */
    @get:Input
    val baseCommandUrl: String = "https://doc.rust-lang.org/cargo/commands"

    init {
        group = BasePlugin.BUILD_GROUP
    }

    @TaskAction
    fun run() {
        cargoOptions.get().asFile.bufferedWriter().use {
            it.appendLine(
                """
                    /*
                     * Use of this source code is governed by the MIT license that can be
                     * found in the LICENSE file.
                     */

                    package org.rust.cargo.util

                    data class CargoOption(val name: String, val description: String) {
                        val longName: String get() = "--${'$'}name"
                    }

                """.trimIndent()
            )
            it.writeCargoOptions(baseCommandUrl)
        }
    }

    private fun Writer.writeCargoOptions(baseUrl: String) {

        data class CargoOption(
            val name: String,
            val description: String
        )

        data class CargoCommand(
            val name: String,
            val description: String,
            val options: List<CargoOption>
        )

        fun fetchCommand(commandUrl: String): CargoCommand {
            val document = Jsoup.connect("$baseUrl/$commandUrl").get()

            val fullCommandDesc = document.select("div[class=sectionbody] > p").text()
            val parts = fullCommandDesc.split(" - ", limit = 2)
            check(parts.size == 2) { "Invalid page format: $baseUrl/$commandUrl$" }
            val commandName = parts.first().removePrefix("cargo-")
            val commandDesc = parts.last()

            val options = document
                .select("dt > strong:matches(^--)")
                .map { option ->
                    val optionName = option.text().removePrefix("--")
                    val nextSiblings = generateSequence(option.parent()) { it.nextElementSibling() }
                    val descElement = nextSiblings.first { it.tagName() == "dd" }
                    val fullOptionDesc = descElement.select("p").text()
                    val optionDesc = fullOptionDesc.substringBefore(". ").removeSuffix(".")
                    CargoOption(optionName, optionDesc)
                }

            return CargoCommand(commandName, commandDesc, options)
        }

        fun fetchCommands(): List<CargoCommand> {
            val document = Jsoup.connect("$baseUrl/cargo.html").get()
            val urls = document.select("dt > a[href]").map { it.attr("href") }
            return urls.map { fetchCommand(it) }
        }

        fun writeEnumVariant(command: CargoCommand, isLast: Boolean) {
            val variantName = command.name.toUpperCase().replace('-', '_')
            val renderedOptions = command.options.joinToString(
                separator = ",\n            ",
                prefix = "\n            ",
                postfix = "\n        "
            ) { "CargoOption(\"${it.name}\", \"\"\"${it.description}\"\"\")" }

            appendLine(
                """
                    |    $variantName(
                    |        description = "${command.description}",
                    |        options = ${if (command.options.isEmpty()) "emptyList()" else "listOf($renderedOptions)"}
                    |    )${if (isLast) ";" else ","}
                """.trimMargin()
            )
            appendLine()
        }

        val commands = fetchCommands()
        appendLine("enum class CargoCommands(val description: String, val options: List<CargoOption>) {")
        for ((index, command) in commands.withIndex()) {
            writeEnumVariant(command, isLast = index == commands.size - 1)
        }
        appendLine("    val presentableName: String get() = name.toLowerCase().replace('_', '-')")
        appendLine("}")
    }

}
