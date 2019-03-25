/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.util

@Suppress("unused")
sealed class DownloadResult<out T> {
    class Ok<T>(val value: T) : DownloadResult<T>()
    class Err(val error: String) : DownloadResult<Nothing>()
}
