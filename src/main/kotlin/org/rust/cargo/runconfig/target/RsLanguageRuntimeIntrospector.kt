/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.target

import com.intellij.execution.target.LanguageRuntimeType
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.text.nullize
import java.util.concurrent.CompletableFuture

class RsLanguageRuntimeIntrospector(val config: RsLanguageRuntimeConfiguration) :
    LanguageRuntimeType.Introspector<RsLanguageRuntimeConfiguration> {

    override fun introspect(
        subject: LanguageRuntimeType.Introspectable
    ): CompletableFuture<RsLanguageRuntimeConfiguration> {
        val rustcPathPromise = if (config.rustcPath.isBlank()) {
            subject.promiseOneLineScript("/usr/bin/which rustc").thenApply { output ->
                output?.trim()?.nullize()?.also { config.rustcPath = it }
            }
        } else {
            CompletableFuture.completedFuture(config.rustcPath)
        }

        val rustcVersionPromise = rustcPathPromise.thenCompose { rustcPath ->
            if (rustcPath == null) return@thenCompose LanguageRuntimeType.Introspector.DONE
            subject.promiseOneLineScript("$rustcPath --version").thenApply { output ->
                output?.removePrefix("rustc")?.trim()?.nullize()?.also {
                    config.rustcVersion = it
                }
            }
        }

        val cargoPathPromise = if (config.cargoPath.isBlank()) {
            subject.promiseOneLineScript("/usr/bin/which cargo").thenApply { output ->
                output?.trim()?.nullize()?.also { config.cargoPath = it }
            }
        } else {
            CompletableFuture.completedFuture(config.cargoPath)
        }

        val cargoVersionPromise = cargoPathPromise.thenCompose { cargoPath ->
            if (cargoPath == null) return@thenCompose LanguageRuntimeType.Introspector.DONE
            subject.promiseOneLineScript("$cargoPath --version").thenApply { output ->
                output?.removePrefix("cargo")?.trim()?.nullize()?.also {
                    config.cargoVersion = it
                }
            }
        }

        return CompletableFuture.allOf(rustcVersionPromise, cargoVersionPromise).thenApply { config }
    }

    companion object {
        private fun LanguageRuntimeType.Introspectable.promiseOneLineScript(
            script: String
        ): CompletableFuture<String?> = promiseExecuteScript(script)
            .thenApply { it?.let { StringUtil.splitByLines(it, true) }?.firstOrNull() }
    }
}
