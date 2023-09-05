/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package ijrust.utils

import org.gradle.api.provider.Provider

operator fun Provider<Boolean>.not() = map { !it }
