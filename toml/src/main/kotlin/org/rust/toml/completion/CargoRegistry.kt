/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.completion

import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.openapi.util.io.FileUtil
import org.apache.commons.io.IOUtils
import org.toml.lang.psi.TomlKey
import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.atomic.AtomicReference

private val cache = AtomicReference<Collection<CrateDescription>>()

fun crates(): Collection<CrateDescription>? {
    val cached = cache.get()
    if (cached != null) {
        return cached
    }

    val index = cargoIndex()

    val builder = ProcessBuilder(mutableListOf("git", "ls-tree", "--name-status", "-r", "origin/master"))
    builder.directory(File(index))

    val output = IOUtils.toString(builder.start().inputStream, UTF_8)

    val entries = output.split("\n")
        .filter(String::isNotBlank)
        .map { CrateDescription(it.substringAfterLast("/"), "1.0.0") }
    cache.set(entries)
    return entries
}

fun cargoHome(): String {
    return System.getenv("CARGO_HOME") ?: "~/.cargo"
}

fun cargoIndex(): String {
    return FileUtil.expandUserHome("${cargoHome()}/registry/index/github.com-1ecc6299db9ec823")
}

fun searchCrate(key: TomlKey): Collection<CrateDescription> {
    val name = CompletionUtil.getOriginalElement(key)?.text ?: ""
    if (name.isEmpty()) return emptyList()

    return crates()?.filter { it.name.startsWith(name) } ?: searchCratesIo(key, name)
}

fun getCrateLastVersion(key: TomlKey): String? {
    val name = CompletionUtil.getOriginalElement(key)?.text ?: ""
    if (name.isEmpty()) return null

    return getCratesIoLastVersion(key, name)
}

