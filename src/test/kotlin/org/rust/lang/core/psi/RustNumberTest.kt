package org.rust.lang.core.psi

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class RustNumberJavaValueTest<T>(private val constructor: (String) -> RustNumber<T>,
                                 private val input: String,
                                 private val expectedOutput: T?) {
    @Test
    fun test() {
        assertEquals(expectedOutput, constructor(input).javaValue)
    }

    companion object {
        @Parameterized.Parameters(name = "{index}: {1}")
        @JvmStatic fun data(): Collection<Array<out Any?>> = listOf(
            arrayOf(i32, "12345", 12345L),
            arrayOf(i32, "-12345", -12345L),
            arrayOf(i32, "1_2_3_4_5", 12345L),
            arrayOf(f32, "1.0", 1.0),
            arrayOf(f32, "1.0_1", 1.01),
            arrayOf(f32, "2.4e8", 2.4e8),
            arrayOf(f32, "10_E-6", 10e-6),
            arrayOf(i32, "0x1F", 31L),
            arrayOf(i32, "0o17", 15L),
            arrayOf(i32, "0b1001", 9L),
            arrayOf(i32, "abrakadabra", null),
            arrayOf(i32, "", null)
        )

        val i32 = { s: String -> RustNumber.Int.I32(s) }
        val f32 = { s: String -> RustNumber.Float.F32(s) }
    }
}
