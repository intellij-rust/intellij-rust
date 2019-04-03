/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import org.rust.lang.utils.RsDemangler.demangle

class RsDemanglerTest {
    @Test
    fun `test demangle`() {
        checkError("test")
        check("_ZN4testE", "test")
        checkError("_ZN4test")
        check("_ZN4test1a2bcE", "test::a::bc")
    }

    @Test
    fun `test demangle dollars`() {
        check("_ZN4\$RP\$E", ")")
        check("_ZN8\$RF\$testE", "&test")
        check("_ZN8\$BP\$test4foobE", "*test::foob")
        check("_ZN9\$u20\$test4foobE", " test::foob")
        check("_ZN35Bar\$LT\$\$u5b\$u32\$u3b\$\$u20\$4\$u5d\$\$GT\$E", "Bar<[u32; 4]>")
    }

    @Test
    fun `test demangle many dollars`() {
        check("_ZN13test\$u20\$test4foobE", "test test::foob")
        check("_ZN12test\$BP\$test4foobE", "test*test::foob")
    }

    @Test
    fun `test demangle osx`() {
        check("_ZN13test\$u20\$test4foobE", "test test::foob")
        check("_ZN12test\$BP\$test4foobE", "test*test::foob")
    }

    @Test
    fun `test demangle windows`() {
        check(
            "__ZN5alloc9allocator6Layout9for_value17h02a996811f781011E",
            "alloc::allocator::Layout::for_value::h02a996811f781011"
        )
        check(
            "__ZN38_\$LT\$core..option..Option\$LT\$T\$GT\$\$GT\$6unwrap18_MSG_FILE_LINE_COL17haf7cb8d5824ee659E",
            "<core::option::Option<T>>::unwrap::_MSG_FILE_LINE_COL::haf7cb8d5824ee659"
        )
        check(
            "__ZN4core5slice89_\$LT\$impl\$u20\$core..iter..traits..IntoIterator\$u20\$for\$u20\$\$RF\$\$u27\$a\$u20\$\$u5b\$T\$u5d\$\$GT\$9into_iter17h450e234d27262170E",
            "core::slice::<impl core::iter::traits::IntoIterator for &'a [T]>::into_iter::h450e234d27262170"
        )
    }

    @Test
    fun `test demangle elements beginning with underscore`() {
        check("_ZN13_\$LT\$test\$GT\$E", "<test>")
        check("_ZN28_\$u7b\$\$u7b\$closure\$u7d\$\$u7d\$E", "{{closure}}")
        check("_ZN15__STATIC_FMTSTRE", "__STATIC_FMTSTR")
    }

    @Test
    fun `test demangle trait impls`() {
        check(
            "_ZN71_\$LT\$Test\$u20\$\$u2b\$\$u20\$\$u27\$static\$u20\$as\$u20\$foo..Bar\$LT\$Test\$GT\$\$GT\$3barE",
            "<Test + 'static as foo::Bar<Test>>::bar"
        )
    }

    @Test
    fun `test demangle without hash`() {
        val symbol = "_ZN3foo17h05af221e174051e9E"
        check(symbol, "foo::h05af221e174051e9")
        check(symbol, "foo", skipHash = true)
    }

    @Test
    fun `test demangle without hash edgecases`() {
        // One element, no hash.
        check("_ZN3fooE", "foo", skipHash = true)
        // Two elements, no hash.
        check("_ZN3foo3barE", "foo::bar", skipHash = true)
        // Longer-than-normal hash.
        check("_ZN3foo20h05af221e174051e9abcE", "foo", skipHash = true)
        // Shorter-than-normal hash.
        check("_ZN3foo5h05afE", "foo", skipHash = true)
        // Valid hash, but not at the end.
        check("_ZN17h05af221e174051e93fooE", "h05af221e174051e9::foo", skipHash = true)
        // Not a valid hash, missing the 'h'.
        check("_ZN3foo16ffaf221e174051e9E", "foo::ffaf221e174051e9", skipHash = true)
        // Not a valid hash, has a non-hex-digit.
        check("_ZN3foo17hg5af221e174051e9E", "foo::hg5af221e174051e9", skipHash = true)
    }

    @Test
    fun `test demangle thinlto`() {
        // One element, no hash.
        check("_ZN3fooE.llvm.9D1C9369", "foo")
        check("_ZN3fooE.llvm.9D1C9369@@16", "foo")
        check("_ZN9backtrace3foo17hbb467fcdaea5d79bE.llvm.A5310EB9", "backtrace::foo", skipHash = true)
    }

    @Test
    fun `test demangle llvm ir branch labels`() {
        check(
            "_ZN4core5slice77_\$LT\$impl\$u20\$core..ops..index..IndexMut\$LT\$I\$GT\$\$u20\$for\$u20\$\$u5b\$T\$u5d\$\$GT\$9index_mut17haf9727c2edfbc47bE.exit.i.i",
            "core::slice::<impl core::ops::index::IndexMut<I> for [T]>::index_mut::haf9727c2edfbc47b.exit.i.i"
        )
        check(
            "_ZN4core5slice77_\$LT\$impl\$u20\$core..ops..index..IndexMut\$LT\$I\$GT\$\$u20\$for\$u20\$\$u5b\$T\$u5d\$\$GT\$9index_mut17haf9727c2edfbc47bE.exit.i.i",
            "core::slice::<impl core::ops::index::IndexMut<I> for [T]>::index_mut.exit.i.i",
            skipHash = true
        )
    }

    @Test
    fun `test demangle ignores suffix that doesn't look like a symbol`() {
        checkError("_ZN3fooE.llvm moocow")
    }

    @Test
    fun `test don't panic`() {
        demangle("_ZN2222222222222222222222EE").toString()
        demangle("_ZN5*70527e27.ll34csaғE").toString()
        demangle("_ZN5*70527a54.ll34_\$b.1E").toString()
        demangle("""
            _ZN5~saäb4e
            2734cOsbE
            5usage20h)3\0\0\0\0\0\0\07e2734cOsbE
        """).toString()
    }

    @Test
    fun `test invalid no chop`() {
        checkError("_ZNfooE")
    }

    @Test
    fun `test handle assoc types`() {
        check(
            "_ZN151_\$LT\$alloc..boxed..Box\$LT\$alloc..boxed..FnBox\$LT\$A\$C\$\$u20\$Output\$u3d\$R\$GT\$\$u20\$\$u2b\$\$u20\$\$u27\$a\$GT\$\$u20\$as\$u20\$core..ops..function..FnOnce\$LT\$A\$GT\$\$GT\$9call_once17h69e8f44b3723e1caE",
            "<alloc::boxed::Box<alloc::boxed::FnBox<A, Output=R> + 'a> as core::ops::function::FnOnce<A>>::call_once::h69e8f44b3723e1ca"
        )
    }

    @Test
    fun `test handle bang`() {
        check(
            "_ZN88_\$LT\$core..result..Result\$LT\$\$u21\$\$C\$\$u20\$E\$GT\$\$u20\$as\$u20\$std..process..Termination\$GT$6report17hfc41d0da4a40b3e8E",
            "<core::result::Result<!, E> as std::process::Termination>::report::hfc41d0da4a40b3e8"
        )
    }

    private fun check(symbol: String, expected: String, skipHash: Boolean = false) {
        val actual = demangle(symbol)
        check(actual.isValid) { "error demangling" }
        assertEquals(expected, actual.format(skipHash))
    }

    private fun checkError(symbol: String) {
        val result = demangle(symbol)
        check(!result.isValid) { "succeeded in demangling" }
        assertEquals(symbol, result.format())
    }
}
