/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.stdext

sealed class Result<SUCC, ERR> {
    data class Failure<SUCC, ERR>(val error: ERR) : Result<SUCC, ERR>()
    data class Success<SUCC, ERR>(val result: SUCC) : Result<SUCC, ERR>()

    fun <RES> map(map: (SUCC) -> RES): Result<RES, ERR> =
        when (this) {
            is Success -> Success(map(result))
            is Failure -> Failure(error)
        }

    val successOrNull: SUCC? get() = if (this is Success) result else null
}
