/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.util

class CargoArgsParser private constructor(
    private val cargoArgs: List<String>,
    private val optionsToArgsCountRange: Map<String, OptionArgsCountRange>
) {
    private fun splitArgs(): SplitCargoArgs {
        val commandOptions = mutableListOf<String>()
        val positionalArguments = mutableListOf<String>()

        var i = 0
        optionLoop@ while (i < cargoArgs.size) {
            val arg = cargoArgs[i]
            i += when {
                arg == "--" -> { // End of options
                    // Collect remaining arguments as positional-only arguments
                    positionalArguments.addAll(cargoArgs.subList(i, cargoArgs.size))
                    break@optionLoop
                }
                arg.startsWith("-") -> { // An option
                    val optionArgsCount = getActualOptionArgsCount(i)
                    val optionWithArgs = cargoArgs.subList(i, i + optionArgsCount + 1)
                    commandOptions.addAll(optionWithArgs)
                    optionWithArgs.size
                }
                else -> { // An positional arg
                    positionalArguments.add(arg)
                    1
                }
            }
        }

        return SplitCargoArgs(commandOptions, positionalArguments)
    }

    private fun getActualOptionArgsCount(optionIdx: Int): Int {
        val option = cargoArgs[optionIdx]
        // Unknown options are assumed to be the flagging options
        val (minArgsCount, maxArgsCount) = optionsToArgsCountRange.getOrDefault(option, OptionArgsCountRange.ZERO)
        val optionArgsCount = cargoArgs.asSequence()
            .drop(optionIdx + 1)
            .takeWhile { !OPTION_NAME_RE.matches(it) }
            .take(maxArgsCount)
            .count()
        check(optionArgsCount >= minArgsCount) { "Too few arguments for option `$option`" }
        return optionArgsCount
    }

    companion object {
        fun parseArgs(commandName: String, cargoArgs: List<String>): ParsedCargoArgs =
            when (commandName) {
                "run" -> parseRunArgs(cargoArgs)
                "test" -> parseTestArgs(cargoArgs)
                else -> error("Unsupported command")
            }

        private fun parseRunArgs(cargoArgs: List<String>): ParsedCargoArgs {
            val argsParser = CargoArgsParser(cargoArgs, RUN_OPTIONS)
            val (commandArguments, positionalArguments) = argsParser.splitArgs()
            val executableArguments = if (positionalArguments.isNotEmpty() && positionalArguments.first() == "--") {
                positionalArguments.drop(1)
            } else {
                positionalArguments
            }
            return ParsedCargoArgs(commandArguments, executableArguments)
        }

        private fun parseTestArgs(cargoArgs: List<String>): ParsedCargoArgs {
            val argsParser = CargoArgsParser(cargoArgs, TEST_OPTIONS)
            val (commandOptions, positionalArguments) = argsParser.splitArgs()
            val (positionalPre, positionalPost) = splitOnDoubleDash(positionalArguments)

            // Don't drop the last element of the `positionalPre` so that Cargo will check the arguments
            val commandArguments = commandOptions + positionalPre

            // The last positional argument before `--` and all arguments after are passed to the test binary
            val executableArguments = listOfNotNull(positionalPre.lastOrNull()) + positionalPost

            return ParsedCargoArgs(commandArguments, executableArguments)
        }

        private const val OPTION_CHAR_CLASS: String = "[a-zA-Z0-9]"
        private val OPTION_NAME_RE: Regex =
            Regex("^(-$OPTION_CHAR_CLASS)|(--$OPTION_CHAR_CLASS+([-_.]$OPTION_CHAR_CLASS+)*)$")

        private val RUN_OPTIONS: Map<String, OptionArgsCountRange> =
            hashMapOf(
                "--bin" to OptionArgsCountRange.ONE,
                "--example" to OptionArgsCountRange.ONE,
                "-p" to OptionArgsCountRange.ONE,
                "--package" to OptionArgsCountRange.ONE,
                "-j" to OptionArgsCountRange.ONE,
                "--jobs" to OptionArgsCountRange.ONE,
                "--features" to OptionArgsCountRange.MANY,
                "--target" to OptionArgsCountRange.ONE,
                "--target-dir" to OptionArgsCountRange.ONE,
                "--manifest-path" to OptionArgsCountRange.ONE,
                "--message-format" to OptionArgsCountRange.ONE,
                "--color" to OptionArgsCountRange.ONE,
                "-Z" to OptionArgsCountRange.ONE
            )
        private val TEST_OPTIONS: Map<String, OptionArgsCountRange> =
            RUN_OPTIONS + hashMapOf(
                "--test" to OptionArgsCountRange.ONE,
                "--bench" to OptionArgsCountRange.ONE,
                "--exclude" to OptionArgsCountRange.ONE
            )
    }
}

private data class OptionArgsCountRange(val min: Int, val max: Int) {
    companion object {
        val ZERO = OptionArgsCountRange(0, 0)
        val ONE = OptionArgsCountRange(1, 1)
        val MANY = OptionArgsCountRange(1, Int.MAX_VALUE)
    }
}

private data class SplitCargoArgs(val commandOptions: List<String>, val positionalArguments: List<String>)
data class ParsedCargoArgs(val commandArguments: List<String>, val executableArguments: List<String>)

fun splitOnDoubleDash(arguments: List<String>): Pair<List<String>, List<String>> {
    val idx = arguments.indexOf("--")
    if (idx == -1) return arguments to emptyList()
    return arguments.take(idx) to arguments.drop(idx + 1)
}
