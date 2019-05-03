/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.crates

import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.openapi.project.Project
import com.intellij.openapiext.isUnitTestMode
import com.intellij.util.text.SemVer
import org.jetbrains.annotations.TestOnly
import org.toml.lang.psi.TomlKey

data class CrateDescription(
    val name: String,
    val maxVersion: SemVer
)

data class Crate(
    val name: String,
    val maxVersion: SemVer,
    val versions: List<CrateVersion>
)

data class CrateVersion(val version: SemVer, val yanked: Boolean)

interface CrateResolver {
    fun searchCrate(name: String): Collection<CrateDescription>
    fun getCrate(name: String): Crate?
}

val CrateDescription.dependencyLine: String
    get() = "$name = \"$maxVersion\""


fun searchCrate(key: TomlKey): Collection<CrateDescription> = getResolver(key.project).searchCrate(getCrateName(key))
fun getCrate(key: TomlKey): Crate? = getResolver(key.project).getCrate(getCrateName(key))
fun getCrateLastVersion(key: TomlKey): SemVer? = getCrate(key)?.maxVersion

fun parseSemver(version: String): SemVer? = SemVer.parseFromText(version)

private fun getCrateName(key: TomlKey): String = CompletionUtil.getOriginalElement(key)?.text ?: ""

private fun getResolver(project: Project): CrateResolver {
    if (isUnitTestMode) {
        return MOCK!!
    }
    return CratesIoResolver(project)
}

private var MOCK: CrateResolver? = null

@TestOnly
fun withMockedCrateResolver(mock: CrateResolver, action: () -> Unit) {
    MOCK = mock
    try {
        action()
    } finally {
        MOCK = null
    }
}
