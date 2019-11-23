/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.completion

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.notification.NotificationType
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapiext.isUnitTestMode
import com.intellij.psi.PsiElement
import com.intellij.util.io.HttpRequests
import org.jetbrains.annotations.TestOnly
import org.rust.ide.notifications.showBalloon
import org.rust.openapiext.runWithCheckCanceled
import org.toml.lang.psi.TomlKey
import java.io.IOException

data class SearchResult(val crates: List<CrateDescription>)

data class CrateInfoResult(val crate: CrateDescription)

data class CrateDescription(
    val name: String,
    @SerializedName("max_version")
    val maxVersion: String
)

val CrateDescription.dependencyLine: String
    get() = "$name = \"$maxVersion\""

fun searchCrate(key: TomlKey): Collection<CrateDescription> {
    if (isUnitTestMode) return MOCK!!

    val name = CompletionUtil.getOriginalElement(key)?.text ?: ""
    if (name.isEmpty()) return emptyList()

    val response = requestCratesIo<SearchResult>(key, "crates?page=1&per_page=20&q=$name&sort=") ?: return emptyList()
    return response.crates
}

fun getCrateLastVersion(key: TomlKey): String? {
    if (isUnitTestMode) return MOCK!!.first().maxVersion

    val name = CompletionUtil.getOriginalElement(key)?.text ?: ""
    if (name.isEmpty()) return null

    val response = requestCratesIo<CrateInfoResult>(key, "crates/$name") ?: return null
    return response.crate.maxVersion
}

private inline fun <reified T> requestCratesIo(context: PsiElement, path: String): T? {
    return requestCratesIo(context, path, T::class.java)
}

private fun <T> requestCratesIo(context: PsiElement, path: String, cls: Class<T>): T? {
    return try {
        runWithCheckCanceled {
            val response = HttpRequests.request("https://crates.io/api/v1/$path")
                .userAgent("IntelliJ Rust Plugin (https://github.com/intellij-rust/intellij-rust)")
                .readString(ProgressManager.getInstance().progressIndicator)
            Gson().fromJson(response, cls)
        }
    } catch (e: IOException) {
        context.project.showBalloon("Could not reach crates.io", NotificationType.WARNING)
        null
    } catch (e: JsonSyntaxException) {
        context.project.showBalloon("Bad answer from crates.io", NotificationType.WARNING)
        null
    }
}

private var MOCK: List<CrateDescription>? = null

@TestOnly
fun withMockedCrateSearch(mock: List<CrateDescription>, action: () -> Unit) {
    MOCK = mock
    try {
        action()
    } finally {
        MOCK = null
    }
}
