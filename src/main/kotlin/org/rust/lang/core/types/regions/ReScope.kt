/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.regions

/**
 * A concrete region naming some statically determined scope (e.g. an expression or sequence of statements) within the
 * current function.
 */
data class ReScope(val scope: Scope) : Region()
