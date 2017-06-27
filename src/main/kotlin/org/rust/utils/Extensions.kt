/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.utils

/**
 * Transforms seconds into milliseconds
 */
val Int.seconds: Int
    get() = this * 1000

