/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.crates

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import com.intellij.notification.NotificationType
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.util.io.HttpRequests
import org.rust.ide.notifications.showBalloon
import org.rust.openapiext.runWithCheckCanceled
import java.io.IOException

class CratesIoResolver(private val project: Project) : CrateResolver {
    override fun searchCrate(name: String): Collection<CrateDescription> {
        val response = requestCratesIo<ApiCrateSearchResult>("crates?page=1&per_page=20&q=$name&sort=")
            ?: return emptyList()
        return response.crates.mapNotNull {
            CrateDescription(it.name, parseSemver(it.maxVersion) ?: return@mapNotNull null)
        }
    }

    override fun getCrate(name: String): Crate? {
        val response = requestCratesIo<ApiCrateFetchResult>("crates/$name") ?: return null
        return Crate(response.crate.name, parseSemver(response.crate.maxVersion) ?: return null,
            response.versions.mapNotNull {
                CrateVersion(parseSemver(it.num) ?: return@mapNotNull null, it.yanked)
            }.toList())
    }

    private inline fun <reified T> requestCratesIo(path: String): T? = requestCratesIo(path, T::class.java)
    private fun <T> requestCratesIo(path: String, cls: Class<T>): T? {
        return try {
            runWithCheckCanceled {
                val response = HttpRequests.request("https://crates.io/api/v1/$path")
                    .userAgent("IntelliJ Rust Plugin (https://github.com/intellij-rust/intellij-rust)")
                    .readString(ProgressManager.getInstance().progressIndicator)
                Gson().fromJson(response, cls)
            }
        } catch (e: IOException) {
            project.showBalloon("Could not reach crates.io", NotificationType.WARNING)
            null
        } catch (e: JsonSyntaxException) {
            project.showBalloon("Bad answer from crates.io", NotificationType.WARNING)
            null
        }
    }

    companion object {
        private data class ApiCrateSearchResult(val crates: List<ApiCrateDescription>)
        private data class ApiCrateDescription(
            val name: String,
            @SerializedName("max_version")
            val maxVersion: String
        )

        private data class ApiCrateVersion(val id: Int, val num: String, val yanked: Boolean)
        private data class ApiCrateFetchResult(val crate: ApiCrateDescription, val versions: List<ApiCrateVersion>)
    }
}
