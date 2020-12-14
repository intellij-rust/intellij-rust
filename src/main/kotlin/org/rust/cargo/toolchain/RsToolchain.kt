/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.exists
import com.intellij.util.text.SemVer
import org.rust.cargo.toolchain.flavors.RsToolchainFlavor
import org.rust.cargo.toolchain.tools.Cargo
import org.rust.cargo.util.hasExecutable
import org.rust.cargo.util.pathToExecutable
import org.rust.stdext.isExecutable
import java.io.File
import java.nio.file.Path

open class RsToolchain(val location: Path) {
    val presentableLocation: String = pathToExecutable(Cargo.NAME).toString()

    fun looksLikeValidToolchain(): Boolean = RsToolchainFlavor.getFlavor(location) != null

    // for executables from toolchain
    fun pathToExecutable(toolName: String): Path = location.pathToExecutable(toolName)

    // for executables installed using `cargo install`
    fun pathToCargoExecutable(toolName: String): Path {
        // Binaries installed by `cargo install` (e.g. Grcov, Evcxr) are placed in ~/.cargo/bin by default:
        // https://doc.rust-lang.org/cargo/commands/cargo-install.html
        // But toolchain root may be different (e.g. on Arch Linux it is usually /usr/bin)
        val path = pathToExecutable(toolName)
        if (path.exists()) return path

        val exeName = if (SystemInfo.isWindows) "$toolName.exe" else toolName
        val cargoBinPath = File(FileUtil.expandUserHome("~/.cargo/bin")).toPath()
        return cargoBinPath.resolve(exeName).toAbsolutePath()
    }

    fun hasExecutable(exec: String): Boolean = location.hasExecutable(exec)

    fun hasCargoExecutable(exec: String): Boolean = pathToCargoExecutable(exec).isExecutable()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RsToolchain) return false

        if (location != other.location) return false

        return true
    }

    override fun hashCode(): Int {
        return location.hashCode()
    }

    override fun toString(): String {
        return "RsToolchain(location=$location)"
    }


    companion object {
        val MIN_SUPPORTED_TOOLCHAIN = SemVer.parseFromText("1.32.0")!!

        /** Environment variable to unlock unstable features of rustc and cargo.
         *  It doesn't change real toolchain.
         *
         * @see <a href="https://github.com/rust-lang/cargo/blob/06ddf3557796038fd87743bd3b6530676e12e719/src/cargo/core/features.rs#L447">features.rs</a>
         */
        const val RUSTC_BOOTSTRAP: String = "RUSTC_BOOTSTRAP"

        fun suggest(): RsToolchain? =
            RsToolchainFlavor.getFlavors()
                .asSequence()
                .flatMap { it.suggestHomePaths().asSequence() }
                .map { RsToolchain(it.toAbsolutePath()) }
                .firstOrNull()
    }
}
