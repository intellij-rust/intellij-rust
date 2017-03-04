package org.rust.cargo.toolchain

data class CargoCommandLine(
    val command: String, // Can't be `enum` because of custom subcommands
    val additionalArguments: List<String> = emptyList(),
    val printBacktrace: Boolean = true,
    val environmentVariables: Map<String, String> = emptyMap()
)
