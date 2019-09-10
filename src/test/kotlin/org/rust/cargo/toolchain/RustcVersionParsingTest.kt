/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import com.intellij.util.text.SemVer.parseFromText
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.rust.cargo.toolchain.RustChannel.*
import kotlin.test.assertEquals
import java.time.LocalDate.parse as parseDate

@RunWith(Parameterized::class)
class RustcVersionParsingTest(
    private val input: String,
    private val expectedVersion: RustcVersion?
) {

    @Test
    fun test() {
        val actualVersion = parseRustcVersion(input.trimIndent().lines())
        assertEquals(expectedVersion, actualVersion)
    }

    companion object {
        @Parameterized.Parameters
        @JvmStatic fun data(): Collection<Array<Any>> = listOf(
            arrayOf("""
                rustc 1.37.0 (eae3437df 2019-08-13)
                binary: rustc
                commit-hash: eae3437dfe991621e8afdc82734f4a172d7ddf9b
                commit-date: 2019-08-13
                host: x86_64-apple-darwin
                release: 1.37.0
                LLVM version: 8.0    
            """, RustcVersion(
                parseFromText("1.37.0")!!,
                "x86_64-apple-darwin",
                STABLE,
                "eae3437dfe991621e8afdc82734f4a172d7ddf9b",
                parseDate("2019-08-13")
            )),
            arrayOf("""
                rustc 1.39.0-nightly (9af17757b 2019-09-02)
                binary: rustc
                commit-hash: 9af17757be1cc3f672928ecf06c40a662c5ec26d
                commit-date: 2019-09-02
                host: x86_64-unknown-linux-gnu
                release: 1.39.0-nightly
                LLVM version: 9.0     
            """, RustcVersion(
                parseFromText("1.39.0")!!,
                "x86_64-unknown-linux-gnu",
                NIGHTLY,
                "9af17757be1cc3f672928ecf06c40a662c5ec26d",
                parseDate("2019-09-02")
            )),
            arrayOf("""
                rustc 1.38.0-beta.2 (641586c1a 2019-08-21)
                binary: rustc
                commit-hash: 641586c1a54f1b1740f8dd796d7501e34c044da2
                commit-date: 2019-08-21
                host: x86_64-apple-darwin
                release: 1.38.0-beta.2
                LLVM version: 9.0                
            """, RustcVersion(
                parseFromText("1.38.0")!!,
                "x86_64-apple-darwin",
                BETA,
                "641586c1a54f1b1740f8dd796d7501e34c044da2",
                parseDate("2019-08-21")
            )),
            arrayOf("""
                rustc 1.37.0
                binary: rustc
                commit-hash: unknown
                commit-date: unknown
                host: x86_64-unknown-linux-gnu
                release: 1.37.0
                LLVM version: 8.0                
            """, RustcVersion(
                parseFromText("1.37.0")!!,
                "x86_64-unknown-linux-gnu",
                STABLE,
                null,
                null
            ))
        )
    }
}
