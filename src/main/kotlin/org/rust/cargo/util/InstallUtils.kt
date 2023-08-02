/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.util

import com.intellij.openapi.util.NlsContexts

@Suppress("unused")
sealed class DownloadResult<out T> {
    class Ok<T>(val value: T) : DownloadResult<T>()
    class Err(@NlsContexts.NotificationContent val error: String) : DownloadResult<Nothing>()
}
