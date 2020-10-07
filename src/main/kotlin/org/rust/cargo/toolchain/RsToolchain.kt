/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.exists
import com.intellij.util.text.SemVer
import org.rust.cargo.CargoConstants
import org.rust.cargo.toolchain.binaries.Evcxr
import org.rust.cargo.toolchain.binaries.Grcov
import org.rust.cargo.toolchain.binaries.WasmPack
import org.rust.cargo.toolchain.components.Cargo
import org.rust.cargo.toolchain.components.Rustc
import org.rust.cargo.toolchain.components.RustcVersion
import org.rust.cargo.toolchain.components.Rustfmt
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

data class RsToolchain(val location: Path) {

    fun looksLikeValidToolchain(): Boolean =
        hasExecutable(CARGO) && hasExecutable(RUSTC)

    fun rustc(): Rustc = Rustc(pathToExecutable(RUSTC))

    fun rawCargo(): Cargo = Cargo(pathToExecutable(CARGO), pathToExecutable(RUSTC))

    fun cargoOrWrapper(cargoProjectDirectory: Path?): Cargo {
        val hasXargoToml = cargoProjectDirectory?.resolve(CargoConstants.XARGO_MANIFEST_FILE)
            ?.let { Files.isRegularFile(it) } == true
        val cargoWrapper = if (hasXargoToml && hasExecutable(XARGO)) XARGO else CARGO
        return Cargo(pathToExecutable(cargoWrapper), pathToExecutable(RUSTC))
    }

    fun rustup(cargoProjectDirectory: Path): Rustup? =
        if (isRustupAvailable)
            Rustup(this, pathToExecutable(RUSTUP), cargoProjectDirectory)
        else
            null

    fun rustfmt(): Rustfmt = Rustfmt(pathToExecutable(RUSTFMT))

    fun grcov(): Grcov? = if (hasCargoExecutable(GRCOV)) Grcov(pathToCargoExecutable(GRCOV)) else null

    fun evcxr(): Evcxr? = if (hasCargoExecutable(EVCXR)) Evcxr(pathToCargoExecutable(EVCXR)) else null

    fun wasmPack(): WasmPack? = if (hasCargoExecutable(WASM_PACK)) WasmPack(pathToCargoExecutable(WASM_PACK)) else null

    val isRustupAvailable: Boolean get() = hasExecutable(RUSTUP)

    val presentableLocation: String = pathToExecutable(CARGO).toString()

    // for executables from toolchain
    private fun pathToExecutable(toolName: String): Path {
        val exeName = if (SystemInfo.isWindows) "$toolName.exe" else toolName
        return location.resolve(exeName).toAbsolutePath()
    }

    // for executables installed using `cargo install`
    private fun pathToCargoExecutable(toolName: String): Path {
        // Binaries installed by `cargo install` (e.g. Grcov, Evcxr) are placed in ~/.cargo/bin by default:
        // https://doc.rust-lang.org/cargo/commands/cargo-install.html
        // But toolchain root may be different (e.g. on Arch Linux it is usually /usr/bin)
        val path = pathToExecutable(toolName)
        if (path.exists()) return path

        val exeName = if (SystemInfo.isWindows) "$toolName.exe" else toolName
        val cargoBinPath = File(FileUtil.expandUserHome("~/.cargo/bin")).toPath()
        return cargoBinPath.resolve(exeName).toAbsolutePath()
    }

    private fun hasExecutable(exec: String): Boolean =
        Files.isExecutable(pathToExecutable(exec))

    private fun hasCargoExecutable(exec: String): Boolean =
        Files.isExecutable(pathToCargoExecutable(exec))

    data class VersionInfo(
        val rustc: RustcVersion?
    )

    companion object {
        private const val RUSTC = "rustc"
        private const val RUSTFMT = "rustfmt"
        private const val CARGO = "cargo"
        private const val RUSTUP = "rustup"
        private const val XARGO = "xargo"
        private const val GRCOV = "grcov"
        private const val EVCXR = "evcxr"
        private const val WASM_PACK = "wasm-pack"

        val MIN_SUPPORTED_TOOLCHAIN = SemVer.parseFromText("1.32.0")!!

        fun suggest(): RsToolchain? = Suggestions.all().mapNotNull {
            val candidate = RsToolchain(it.toPath().toAbsolutePath())
            if (candidate.looksLikeValidToolchain()) candidate else null
        }.firstOrNull()
    }
}

private object Suggestions {
    fun all() = sequenceOf(
        fromRustup(),
        fromPath(),
        forMac(),
        forUnix(),
        forWindows()
    ).flatten()

    private fun fromRustup(): Sequence<File> {
        val file = File(FileUtil.expandUserHome("~/.cargo/bin"))
        return if (file.isDirectory) {
            sequenceOf(file)
        } else {
            emptySequence()
        }
    }

    private fun fromPath(): Sequence<File> = System.getenv("PATH").orEmpty()
        .split(File.pathSeparator)
        .asSequence()
        .filter { !it.isEmpty() }
        .map(::File)
        .filter { it.isDirectory }

    private fun forUnix(): Sequence<File> {
        if (!SystemInfo.isUnix) return emptySequence()

        return sequenceOf(File("/usr/local/bin"))
    }

    private fun forMac(): Sequence<File> {
        if (!SystemInfo.isMac) return emptySequence()

        return sequenceOf(File("/usr/local/Cellar/rust/bin"))
    }

    private fun forWindows(): Sequence<File> {
        if (!SystemInfo.isWindows) return emptySequence()
        val fromHome = File(System.getProperty("user.home") ?: "", ".cargo/bin")

        val programFiles = File(System.getenv("ProgramFiles") ?: "")
        val fromProgramFiles = if (!programFiles.exists() || !programFiles.isDirectory)
            emptySequence()
        else
            programFiles.listFiles { file -> file.isDirectory }.asSequence()
                .filter { it.nameWithoutExtension.toLowerCase().startsWith("rust") }
                .map { File(it, "bin") }

        return sequenceOf(fromHome) + fromProgramFiles
    }
}
