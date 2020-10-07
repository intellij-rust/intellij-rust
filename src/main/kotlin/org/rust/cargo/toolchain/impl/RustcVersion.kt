/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain.impl

import com.google.common.annotations.VisibleForTesting
import com.intellij.util.text.SemVer
import org.rust.cargo.toolchain.RustChannel
import java.time.LocalDate
import java.time.format.DateTimeParseException

data class RustcVersion(
    val semver: SemVer,
    val host: String,
    val channel: RustChannel,
    val commitHash: String? = null,
    val commitDate: LocalDate? = null
)

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
