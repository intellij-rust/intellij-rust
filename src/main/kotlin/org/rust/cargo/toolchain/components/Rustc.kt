/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain.components

import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapiext.isUnitTestMode
import com.intellij.util.text.SemVer
import org.rust.cargo.toolchain.RsToolchain
import org.rust.cargo.toolchain.RustChannel
import org.rust.openapiext.*
import java.nio.file.Path
import java.time.LocalDate
import java.time.format.DateTimeParseException

class Rustc(private val rustcPath: Path) {

    fun queryVersions(): RsToolchain.VersionInfo {
        if (!isUnitTestMode) {
            checkIsBackgroundThread()
        }
        return RsToolchain.VersionInfo(scrapeRustcVersion(rustcPath))
    }

    fun getSysroot(projectDirectory: Path): String? {
        if (!isUnitTestMode) {
            checkIsBackgroundThread()
        }
        val timeoutMs = 10000
        val output = GeneralCommandLine(rustcPath)
            .withCharset(Charsets.UTF_8)
            .withWorkDirectory(projectDirectory)
            .withParameters("--print", "sysroot")
            .execute(timeoutMs)
        return if (output?.isSuccess == true) output.stdout.trim() else null
    }

    fun getStdlibFromSysroot(projectDirectory: Path): VirtualFile? {
        val sysroot = getSysroot(projectDirectory) ?: return null
        val fs = LocalFileSystem.getInstance()
        return fs.refreshAndFindFileByPath(FileUtil.join(sysroot, "lib/rustlib/src/rust"))
    }

    fun getCfgOptions(projectDirectory: Path): List<String>? {
        val timeoutMs = 10000
        val output = GeneralCommandLine(rustcPath)
            .withCharset(Charsets.UTF_8)
            .withWorkDirectory(projectDirectory)
            .withParameters("--print", "cfg")
            .execute(timeoutMs)
        return if (output?.isSuccess == true) output.stdoutLines else null
    }
}

data class RustcVersion(
    val semver: SemVer,
    val host: String,
    val channel: RustChannel,
    val commitHash: String? = null,
    val commitDate: LocalDate? = null
)

private fun scrapeRustcVersion(rustc: Path): RustcVersion? {
    val lines = GeneralCommandLine(rustc)
        .withParameters("--version", "--verbose")
        .execute()
        ?.stdoutLines
        ?: return null

    return parseRustcVersion(lines)
}

@VisibleForTesting
fun parseRustcVersion(lines: List<String>): RustcVersion? {
    // We want to parse following
    //
    //  ```
    //  rustc 1.8.0-beta.1 (facbfdd71 2016-03-02)
    //  binary: rustc
    //  commit-hash: facbfdd71cb6ed0aeaeb06b6b8428f333de4072b
    //  commit-date: 2016-03-02
    //  host: x86_64-unknown-linux-gnu
    //  release: 1.8.0-beta.1
    //  ```
    val releaseRe = """release: (\d+\.\d+\.\d+)(.*)""".toRegex()
    val hostRe = "host: (.*)".toRegex()
    val commitHashRe = "commit-hash: ([A-Fa-f0-9]{40})".toRegex()
    val commitDateRe = """commit-date: (\d{4}-\d{2}-\d{2})""".toRegex()
    val find = { re: Regex -> lines.mapNotNull { re.matchEntire(it) }.firstOrNull() }

    val releaseMatch = find(releaseRe) ?: return null
    val hostText = find(hostRe)?.groups?.get(1)?.value ?: return null
    val versionText = releaseMatch.groups[1]?.value ?: return null
    val commitHash = find(commitHashRe)?.groups?.get(1)?.value
    val commitDate = try {
        find(commitDateRe)?.groups?.get(1)?.value?.let(LocalDate::parse)
    } catch (e: DateTimeParseException) {
        null
    }

    val semVer = SemVer.parseFromText(versionText) ?: return null
    val releaseSuffix = releaseMatch.groups[2]?.value.orEmpty()
    val channel = when {
        releaseSuffix.isEmpty() -> RustChannel.STABLE
        releaseSuffix.startsWith("-beta") -> RustChannel.BETA
        releaseSuffix.startsWith("-nightly") -> RustChannel.NIGHTLY
        releaseSuffix.startsWith("-dev") -> RustChannel.DEV
        else -> RustChannel.DEFAULT
    }
    return RustcVersion(semVer, hostText, channel, commitHash, commitDate)
}
