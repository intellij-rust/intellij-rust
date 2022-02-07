/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.openapiext

import com.intellij.util.ExceptionUtil
import junit.framework.TestCase.fail
import org.jetbrains.annotations.TestOnly


/**
 * Testmarks allow easy navigation between code and tests.
 * That is, to find a relevant test for some piece of logic,
 * and to find the precise condition which is supposed to be
 * tested by a particular test. In a sense, testmarks are
 * a hand-made artisanal code-coverage tool.
 *
 * To use a testmark, first define a static [Testmark] value
 *
 * ```
 * object EmptyFrobnicator : Testmark()
 * ```
 *
 * Then, use [hit] function in the "production" code
 *
 * ```
 * if (myFrobnicator.isEmpty()) {
 *     EmptyFrobnicator.hit()
 *     handleEmptyFrobnicator()
 * }
 * ```
 *
 * Finally, in the test case use [org.rust.CheckTestmarkHit] annotation:
 *
 * ```
 * @CheckTestmarkHit(EmptyFrobnicator::class)
 * fun `test don't blow up on empty frobnicator`() {
 *    arrange()
 *    act()
 *    assert()
 * }
 * ```
 *
 * You can `Ctrl+B` on `EmptyFrobnicator` to navigate
 * between code and tests. If after refactoring the
 * test fails to hit the testmark, you'll be notified
 * that the test might not work as it used to.
 *
 * Note that if testmark is used in "production", but
 * not in "test" code, it's useless, but we can't
 * provide a runtime assertion for this case.
 */
abstract class Testmark: TestmarkPred {
    val name: String
        get() = javaClass.canonicalName ?: javaClass.name

    @Volatile
    private var state = TestmarkState.NEW
    @Volatile
    private var hitAt: Throwable? = null

    fun hit() {
        if (state == TestmarkState.NOT_HIT) {
            state = TestmarkState.HIT
            hitAt = Throwable()
        }
    }

    @TestOnly
    override fun <T> checkHit(f: () -> T): T = checkHit(TestmarkState.HIT, f)

    @TestOnly
    override fun <T> checkNotHit(f: () -> T): T = checkHit(TestmarkState.NOT_HIT, f)

    @TestOnly
    private fun <T> checkHit(expected: TestmarkState, f: () -> T): T {
        check(state == TestmarkState.NEW)
        try {
            state = TestmarkState.NOT_HIT
            val result = f()
            if (state != expected) {
                if (expected == TestmarkState.HIT) {
                    fail("Testmark `$name` not hit")
                } else {
                    val attachment = ExceptionUtil.getThrowableText(hitAt!!)
                    fail("Testmark `$name` hit at $attachment;")
                }

            }
            return result
        } finally {
            state = TestmarkState.NEW
        }
    }

    private enum class TestmarkState { NEW, NOT_HIT, HIT }
}

fun Testmark.hitOnFalse(b: Boolean): Boolean {
    if (!b) hit()
    return b
}

interface TestmarkPred {
    @TestOnly
    fun <T> checkHit(f: () -> T): T

    @TestOnly
    fun <T> checkNotHit(f: () -> T): T
}

@TestOnly
operator fun Testmark.not(): TestmarkPred = object : TestmarkPred {
    override fun <T> checkHit(f: () -> T): T = this@not.checkNotHit(f)
    override fun <T> checkNotHit(f: () -> T): T = this@not.checkHit(f)
}

object EmptyTestmark : TestmarkPred {
    override fun <T> checkHit(f: () -> T): T = f()
    override fun <T> checkNotHit(f: () -> T): T = f()
}
