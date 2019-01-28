/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.dfa

import org.rust.RsTestBase
import org.rust.lang.core.dfa.LongRangeSet.Companion.all
import org.rust.lang.core.dfa.LongRangeSet.Companion.empty
import org.rust.lang.core.dfa.LongRangeSet.Companion.fromConstant
import org.rust.lang.core.dfa.LongRangeSet.Companion.point
import org.rust.lang.core.dfa.LongRangeSet.Companion.range
import org.rust.lang.core.dfa.LongRangeSet.Companion.unknown
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyFloat
import org.rust.lang.core.types.ty.TyInteger
import org.rust.lang.core.types.ty.TyStr
import java.util.*
import kotlin.collections.set
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf
import kotlin.streams.asSequence
import kotlin.streams.toList
import kotlin.test.assertFails

class LongRangeSetTest : RsTestBase() {
    fun `test long range set from string`() {
        checkSet("{}", setFromString(""))
        checkSet("{!}", setFromString("!"))
        checkSet("{?}", setFromString("?"))
        checkSet("{z}", setFromString("z"))
        checkSet("{42}", setFromString("42"))
        checkSet("{42}", setFromString("42, 42"))
        checkSet("{42}", setFromString("42..42"))
        checkSet("{0..42}", setFromString("0..42"))
        checkSet("{-10..42}", setFromString("-10..42"))
        checkSet("{-10..-5}", setFromString("-10..-5"))
        checkSet("{-10..-1, 1..10}", setFromString("-10..-1, 1..10"))
        checkSet("{-10..-1, 1..10, 42}", setFromString("-10..-1, 1..10, 42"))
        assertFails { setFromString("a") }
        assertFails { setFromString(",1,42") }
    }

    fun `test to string`() {
        checkSet("{}", setFromString(""))
        checkSet("{!}", empty(true))
        checkSet("{z}", Empty.DivisionByZero)
        TyInteger.VALUES.forEach {
            checkSet("{10}", point(10, it))
            checkSet("{10}", range(10, 10, it))
            checkSet("{10, 11}", range(10, 11, it))
            checkSet("{10..100}", range(10, 100, it))
            checkSet("{0..10, 12..15}", setFromString("0..10, 12..15", it))
        }
    }

    fun `test from type`() {
        assertNull(LongRangeSet.fromType(TyFloat.DEFAULT))
        assertNull(LongRangeSet.fromType(TyStr))
        checkSet("{-128..127}", fromType(TyInteger.I8))
        checkSet("{0..255}", fromType(TyInteger.U8))
        checkSet("{-32768..32767}", fromType(TyInteger.I16))
        checkSet("{0..65535}", fromType(TyInteger.U16))
        checkSet("{-2147483648..2147483647}", fromType(TyInteger.I32))
        checkSet("{0..4294967295}", fromType(TyInteger.U32))
        checkSet("{-9223372036854775808..9223372036854775807}", fromType(TyInteger.I64))
        checkSet("{0..9223372036854775807}", fromType(TyInteger.U64))
        checkSet("{-9223372036854775808..9223372036854775807}", fromType(TyInteger.I128))
        checkSet("{0..9223372036854775807}", fromType(TyInteger.U128))
        checkSet("{-9223372036854775808..9223372036854775807}", fromType(TyInteger.ISize))
        checkSet("{0..9223372036854775807}", fromType(TyInteger.USize))
    }

    fun `test equals empty`() {
        val empty = empty()
        checkMethodWithBooleanResult(
            listOf(empty()) to { it -> empty == it && it == empty },
            listOf(
                unknown(),
                point(42),
                range(-5, 50),
                setFromString("-100..0, 2..50")
            ) to { it -> empty != it && it != empty }
        )
    }

    fun `test equals unknown`() {
        val unknown = unknown()
        checkMethodWithBooleanResult(
            listOf(unknown()) to { it -> unknown == it && it == unknown },
            listOf(
                empty(),
                point(42),
                range(-5, 50),
                setFromString("-100..0, 2..50")
            ) to { it -> unknown != it && it != unknown }
        )
    }

    fun `test equals point`() {
        val point = point(42)
        checkMethodWithBooleanResult(
            listOf(
                point(42),
                range(42, 42)
            ) to { it -> point == it && it == point },
            listOf(
                empty(),
                unknown(),
                point(43),
                range(-5, 50),
                setFromString("-100..0, 2..50")
            ) to { it -> point != it && it != point }
        )
    }

    fun `test equals range`() {
        val range = range(-5, 15)
        checkMethodWithBooleanResult(
            listOf(
                range(-5, 15)
            ) to { it -> range == it && it == range },
            listOf(
                empty(),
                unknown(),
                point(3),
                range(-5, 50),
                range(-6, 15),
                range(-5, 16),
                setFromString("-100..0, 3, 5..50")
            ) to { it -> range != it && it != range }
        )
    }

    fun `test equals set`() {
        val set = setFromString("-5..1, 10, 30..44")
        checkMethodWithBooleanResult(
            listOf(
                setFromString("-5..1, 10, 30..44")
            ) to { it -> set == it && it == set },
            listOf(
                empty(),
                unknown(),
                point(10),
                point(100),
                range(-5, 1),
                range(9, 11),
                setFromString("-5..1, 30..44")
            ) to { it -> set != it && it != set }
        )
    }

    fun `test subtract empty`() {
        val empty = empty()
        checkMethodWithBooleanResult(
            listOf(
                empty(),
                unknown(),
                all(),
                point(42),
                range(-5, 50),
                setFromString("-100..0, 2..50")
            ) to { it -> empty == empty.subtract(it) }
        )
    }

    fun `test subtract unknown`() {
        val unknown = unknown()
        checkMethodWithBooleanResult(
            listOf(
                empty(),
                unknown(),
                all(),
                point(42),
                range(-5, 50),
                setFromString("-100..0, 2..50")
            ) to { it -> unknown == unknown.subtract(it) }
        )
    }

    fun `test subtract point`() {
        val point = point(42)
        checkMethodWithBooleanResult(
            listOf(
                empty(),
                point(43),
                range(-5, 41),
                range(43, 55),
                setFromString("-100..0, 2..41, 43..66")
            ) to { it -> point == point.subtract(it) },
            listOf(
                all(),
                unknown(),
                point(42),
                range(4, 43),
                setFromString("2..40, 42, 44..66"),
                setFromString("-50..100, 777")
            ) to { it -> empty() == point.subtract(it) }
        )
    }

    fun `test subtract range`() {
        val range = range(42, 50)
        checkMethodWithBooleanResult(
            listOf(
                empty(),
                point(41),
                range(-5, 41),
                range(51, 100),
                setFromString("-100..0, 2..41, 51..60")
            ) to { it -> range == range.subtract(it) },
            listOf(
                unknown(),
                all(),
                range(4, 50),
                range(-4, 100),
                setFromString("40, 42..66"),
                setFromString("-50..100, 777")
            ) to { it -> empty() == range.subtract(it) }
        )

        checkSet("{43..50}", range.subtract(point(42)))
        checkSet("{42..49}", range.subtract(point(50)))
        checkSet("{42, 43, 45..50}", range.subtract(point(44)))

        checkSet("{45..50}", range.subtract(range(0, 44)))
        checkSet("{42..46}", range.subtract(range(47, 50)))
        checkSet("{42..44, 47..50}", range.subtract(range(45, 46)))

        checkSet("{44, 46}", range.subtract(setFromString("42..43, 45, 47..50")))
    }

    fun `test subtract set`() {
        val set = setFromString("-2..60, 77, 555")
        checkMethodWithBooleanResult(
            listOf(
                empty(),
                point(61),
                range(61, 76),
                range(556, 1000),
                setFromString("-100..-3, 61..66")
            ) to { it -> set == set.subtract(it) },
            listOf(
                unknown(),
                range(-2, 555),
                range(-40, 1000),
                setFromString("-2..60, 77, 555"),
                setFromString("-50..60, 70..600")
            ) to { it -> empty() == set.subtract(it) }
        )

        checkSet("{-2..41, 43..60, 77, 555}", set.subtract(point(42)))
        checkSet("{-2..49, 51..60, 77, 555}", set.subtract(point(50)))
        checkSet("{-2..60, 555}", set.subtract(point(77)))

        checkSet("{77, 555}", set.subtract(range(-50, 61)))
        checkSet("{-2..54}", set.subtract(range(55, 555)))
        checkSet("{-2, -1, 4..60, 77, 555}", set.subtract(range(0, 3)))

        checkSet("{-2..2, 4..13, 19..60, 77}", set.subtract(setFromString("3, 14..18, 555")))
    }

    fun `test diff`() {
        assertEquals(empty(), empty().subtract(point(10)))
        assertEquals(point(10), point(10).subtract(empty()))
        assertEquals(point(10), point(10).subtract(point(11)))
        assertEquals(empty(), point(10).subtract(point(10)))
        assertEquals(point(10), point(10).subtract(range(15, 20)))
        assertEquals(point(10), point(10).subtract(range(-10, -5)))
        assertTrue(point(10).subtract(range(10, 20)).isEmpty)
        assertTrue(point(10).subtract(range(-10, 20)).isEmpty)
        assertTrue(point(10).subtract(range(-10, 10)).isEmpty)

        checkSet("{0..20}", range(0, 20).subtract(range(30, Long.MAX_VALUE)))
        checkSet("{0..19}", range(0, 20).subtract(range(20, Long.MAX_VALUE)))
        checkSet("{0..18}", range(0, 20).subtract(range(19, Long.MAX_VALUE)))
        checkSet("{0}", range(0, 20).subtract(range(1, Long.MAX_VALUE)))
        assertTrue(range(0, 20).subtract(range(0, Long.MAX_VALUE)).isEmpty)

        checkSet(
            "{${Long.MIN_VALUE}}",
            all().subtract(range(Long.MIN_VALUE + 1, Long.MAX_VALUE))
        )
        checkSet(
            "{${Long.MAX_VALUE}}",
            all().subtract(range(Long.MIN_VALUE, Long.MAX_VALUE - 1))
        )
        assertTrue(all().subtract(range(Long.MIN_VALUE, Long.MAX_VALUE)).isEmpty)
        assertTrue(all().subtract(all()).isEmpty)
    }

    fun `test sets`() {
        checkSet("{0..9, 11..20}", range(0, 20).without(10))
        checkSet("{0, 20}", range(0, 20).subtract(range(1, 19)))
        checkSet("{0, 1, 19, 20}", range(0, 20).subtract(range(2, 18)))

        checkSet("{0..9, 12..20}", range(0, 20).without(10).without(11))
        checkSet("{0..9, 12..14, 16..20}", range(0, 20).without(10).without(11).without(15))
        checkSet("{0, 4..20}", range(0, 20).without(3).without(2).without(1))
        checkSet("{4..20}", range(0, 20).without(3).without(2).without(1).without(0))

        checkSet("{0..2, 5..15, 19, 20}", range(0, 20).subtract(range(3, 18).subtract(range(5, 15))))

        val first = fromType(TyInteger.U8).without(45)
        val second = fromType(TyInteger.U8).without(32).without(40).without(44).without(45).without(46).without(58).without(59).without(61)
        checkSet("{0..44, 46..255}", first)
        checkSet("{0..31, 33..39, 41..43, 47..57, 60, 62..255}", second)
        checkSet("{32, 40, 44, 46, 58, 59, 61}", first.subtract(second))
    }

    fun `test hash`() {
        val map = hashMapOf<LongRangeSet, String>()
        map[empty()] = "empty"
        map[empty(overflow = true)] = "empty_o"
        map[unknown()] = "unknown"
        map[point(10)] = "10"
        map[range(10, 10)] = "10-10"
        map[range(10, 11)] = "10-11"
        map[range(10, 12)] = "10-12"
        map[setFromString("0..5, 8..10")] = "0-5,8-10"
        map[fromType(TyInteger.I64).subtract(fromType(TyInteger.U16))] = "I64NotU16"

        assertEquals("empty", map[empty()])
        assertEquals("empty_o", map[empty(overflow = true)])
        assertEquals("unknown", map[unknown()])
        assertEquals("10-10", map[point(10)])
        assertEquals("10-10", map[range(10, 10)])
        assertEquals("10-11", map[range(10, 11)])
        assertEquals("10-12", map[range(10, 12)])
        assertEquals("0-5,8-10", map[setFromString("0..5, 8..10")])
        assertNull(map[range(11, 11)])
        assertEquals("I64NotU16", map[fromType(TyInteger.I64).subtract(fromType(TyInteger.U16))])
    }

    fun `test intersects empty`() {
        val empty = empty()
        checkMethodWithBooleanResult(
            listOf(
                empty(),
                all(),
                unknown(),
                setFromString("5, 55..60"),
                point(42)
            ) to { it -> !it.intersects(empty) && !empty.intersects(it) }
        )
    }

    fun `test intersects unknown`() {
        val unknown = unknown()
        checkMethodWithBooleanResult(
            listOf(
                all(),
                unknown(),
                setFromString("5, 55..60"),
                point(42)
            ) to { it -> it.intersects(unknown) && unknown.intersects(it) },
            listOf(
                empty()
            ) to { it -> !it.intersects(unknown) && !unknown.intersects(it) }
        )
    }

    fun `test intersects point`() {
        val point = point(42)
        checkMethodWithBooleanResult(
            listOf(
                all(),
                unknown(),
                setFromString("5, 42..60"),
                setFromString("42, 55..60"),
                point(42)
            ) to { it -> it.intersects(point) && point.intersects(it) },
            listOf(
                empty(),
                point(0),
                range(0, 40),
                setFromString("41, 55..60")
            ) to { it -> !it.intersects(point) && !point.intersects(it) }
        )
    }

    fun `test intersects range`() {
        val range = range(-7, 7)
        checkMethodWithBooleanResult(
            listOf(
                all(),
                unknown(),
                setFromString("-7, 42..60"),
                setFromString("-3..4, 55..60"),
                point(-0),
                range(-1, 1)
            ) to { it -> it.intersects(range) && range.intersects(it) },
            listOf(
                empty(),
                point(8),
                range(-10, -8),
                setFromString("-55, 55..60")
            ) to { it -> !it.intersects(range) && !range.intersects(it) }
        )
    }

    fun `test intersects set`() {
        val set = setFromString("-5, 10..15")
        checkMethodWithBooleanResult(
            listOf(
                all(),
                unknown(),
                setFromString("-5, 42..60"),
                setFromString("-5, 10..15"),
                point(-5),
                range(-1, 11),
                range(10, 17)
            ) to { it -> it.intersects(set) && set.intersects(it) },
            listOf(
                empty(),
                point(8),
                range(-4, 9),
                setFromString("-55, 9, 55..60")
            ) to { it -> !it.intersects(set) && !set.intersects(it) }
        )
    }

    fun `test intersect empty`() {
        val empty = empty()
        checkMethodWithBooleanResult(
            listOf(
                empty(),
                all(),
                unknown(),
                setFromString("5, 55..60"),
                point(42)
            ) to { it -> it.intersect(empty).isEmpty && empty.intersect(it).isEmpty }
        )
    }

    fun `test intersect unknown`() {
        val unknown = unknown()
        checkMethodWithBooleanResult(
            listOf(
                all(),
                unknown(),
                setFromString("5, 55..60"),
                point(42)
            ) to { it -> it.intersect(unknown).isUnknown && unknown.intersect(it).isUnknown },
            listOf(
                empty()
            ) to { it -> it.intersect(unknown).isEmpty && unknown.intersect(it).isEmpty }
        )
    }

    fun `test intersect set`() = checkSet("{-54, 60..63, 77}", setFromString("-55..-54, 59..66, 77").intersect(setFromString("-54, 60..63, 76..78")))

    fun `test intersect subtract randomized`() {
        val r = Random(1)
        val data = r.ints(1000, 0, 1000).mapToObj { range(it.toLong(), (it + r.nextInt(it % 20 * 100 + 1)).toLong()) }.asSequence().toMutableList()
        for (i in 0..2000) {
            val idx = r.nextInt(data.size)
            val left = data[idx]
            val right = data[r.nextInt(data.size)]
            val lDiff = left.subtract(right)
            val rDiff = right.subtract(left)
            val intersection = left.intersect(right)
            val message = "$left & $right = $intersection"
            assertEquals(message, intersection, right.intersect(left))
            if (!intersection.isEmpty) {
                assertTrue(message, intersection.min >= Math.max(left.min, right.min))
                assertTrue(message, intersection.max <= Math.min(left.max, right.max))
            }
            assertEquals(message, intersection, right.subtract(fromType(TyInteger.I64).subtract(left)))
            assertEquals(message, intersection, left.subtract(fromType(TyInteger.I64).subtract(right)))
            intersection.stream.limit(1000).forEach { e ->
                assertTrue(left.contains(e))
                assertTrue(right.contains(e))
            }
            lDiff.stream.limit(1000).forEach { e ->
                assertTrue(left.contains(e))
                assertFalse(right.contains(e))
            }
            rDiff.stream.limit(1000).forEach { e ->
                assertFalse(left.contains(e))
                assertTrue(right.contains(e))
            }
            when (r.nextInt(3)) {
                0 -> data[idx] = lDiff
                1 -> data[idx] = rDiff
                2 -> data[idx] = intersection
            }
        }
    }

    fun `test from constant`() {
        checkSet("{0}", fromConstant(0, TyInteger.I8))
        checkSet("{-20}", fromConstant(-20, TyInteger.I8))
        checkSet("{!}", fromConstant(-20, TyInteger.U8))
        checkSet("{1}", fromConstant(1, TyInteger.I8))
        checkSet("{42}", fromConstant(42, TyInteger.I8))
        checkSet("{?}", fromConstant(null, TyInteger.I128))
        checkSet("{42}", fromConstant(42, TyInteger.I128))
        checkSet("{!}", fromConstant(-10, TyInteger.U128))
    }

    fun `test from relation`() {
        assertEquals(range(101, Long.MAX_VALUE), range(100, 200).fromRelation(ComparisonOp.GT))
        assertEquals(range(100, Long.MAX_VALUE), range(100, 200).fromRelation(ComparisonOp.GTEQ))
        assertEquals(range(Long.MIN_VALUE, 199), range(100, 200).fromRelation(ComparisonOp.LT))
        assertEquals(range(Long.MIN_VALUE, 200), range(100, 200).fromRelation(ComparisonOp.LTEQ))
        assertEquals(range(100, 200), range(100, 200).fromRelation(EqualityOp.EQ))
        checkSet("{0..99, 150, 201..255}", range(100, 200, TyInteger.U8).without(150).fromRelation(EqualityOp.EXCLEQ))
        checkSet("{${Long.MIN_VALUE}..99, 101..${Long.MAX_VALUE}}", point(100).fromRelation(EqualityOp.EXCLEQ))
    }

    fun `test abs`() {
        assertTrue(empty().abs.isEmpty)
        assertEquals(point(Long.MAX_VALUE), point(Long.MIN_VALUE + 1).abs)
        assertEquals(empty(overflow = true), point(Long.MIN_VALUE).abs)
        assertEquals(empty(overflow = true), point(-128L, TyInteger.I8).abs)
        assertEquals(empty(overflow = true), point(Int.MIN_VALUE.toLong(), TyInteger.I32).abs)
        assertEquals(range(100, 200), range(100, 200).abs)
        assertEquals(range(0, 200), range(-1, 200).abs)
        assertEquals(range(0, 200), range(-200, 200).abs)
        assertEquals(range(0, 201), range(-201, 200).abs)
        assertEquals(range(0, Long.MAX_VALUE), all().abs)
        assertEquals(range(100, Int.MAX_VALUE.toLong(), TyInteger.I32), range(Int.MIN_VALUE.toLong(), -100, TyInteger.I32).abs)
        assertEquals(range(100, Int.MAX_VALUE + 1L), range(Int.MIN_VALUE.toLong(), -100).abs)
        assertEquals(point(127, TyInteger.I8), range(-128, -127, TyInteger.I8).abs)

        val set = range(-900, 1000).subtract(range(-800, -600)).subtract(range(-300, 100)).subtract(range(500, 700))
        checkSet("{-900..-801, -599..-301, 101..499, 701..1000}", set)
        checkSet("{101..599, 701..1000}", set.abs)
    }

    fun `test unary minus`() {
        assertTrue(empty().unaryMinus().isEmpty)
        assertEquals(point(Long.MAX_VALUE), -point(Long.MIN_VALUE + 1))
        assertEquals(empty(overflow = true), -point(Int.MIN_VALUE.toLong(), TyInteger.I32))
        assertEquals(empty(overflow = true), -point(-128L, TyInteger.I8))
        assertEquals(point(127, TyInteger.I8), -range(-128, -127, TyInteger.I8))
        assertEquals(point(Int.MAX_VALUE + 1L), -point(Int.MIN_VALUE.toLong()))
        assertEquals(range(-200, -100), -range(100, 200))
        assertEquals(range(-200, 1), -range(-1, 200))
        assertEquals(range(-200, 200), -range(-200, 200))
        assertEquals(range(-200, 201), -range(-201, 200))
        assertEquals(all(), -all())
        assertEquals(range(100, Int.MAX_VALUE.toLong(), TyInteger.I32), -range(Int.MIN_VALUE.toLong(), -100, TyInteger.I32))
        assertEquals(point(Long.MAX_VALUE), -range(Long.MIN_VALUE, Long.MIN_VALUE + 1))
        assertEquals(range(100, Int.MAX_VALUE + 1L), -range(Int.MIN_VALUE.toLong(), -100))
        val set = range(-900, 1000).subtract(range(-800, -600)).subtract(range(-300, 100)).subtract(range(500, 700))
        checkSet("{-900..-801, -599..-301, 101..499, 701..1000}", set)
        checkSet("{-1000..-701, -499..-101, 301..599, 801..900}", -set)
    }

    fun `test contains point`() {
        val point = point(42)
        checkMethodWithBooleanResult(
            listOf(
                point(42),
                range(0, 100),
                range(0, 42),
                range(42, 100),
                setFromString("0, 11, 42"),
                setFromString("0..42, 55..10000"),
                setFromString("42..44, 55..10000"),
                unknown()
            ) to { it -> point in it },
            listOf(
                empty(),
                point(666),
                range(0, 41),
                setFromString("0, 11, 44"),
                setFromString("0..41, 43..10000")
            ) to { it -> point !in it }
        )
    }

    fun `test contains long`() {
        val number = 42L
        checkMethodWithBooleanResult(
            listOf(
                point(42),
                range(0, 100),
                range(0, 42),
                range(42, 100),
                setFromString("0, 11, 42"),
                setFromString("0..42, 55..10000"),
                setFromString("42..44, 55..10000"),
                unknown()
            ) to { it -> number in it },
            listOf(
                empty(),
                point(666),
                range(0, 41),
                setFromString("0, 11, 44"),
                setFromString("0..41, 43..10000")
            ) to { it -> number !in it }
        )
    }

    fun `test contains range`() {
        val range = range(-5, 15)
        checkMethodWithBooleanResult(
            listOf(
                range(-5, 15),
                range(-100, 100500),
                setFromString("-70..55, 99"),
                unknown()
            ) to { it -> range in it },
            listOf(
                point(10),
                range(-4, 15),
                setFromString("-4..15, 20"),
                point(5),
                empty()
            ) to { it -> range !in it }
        )
    }

    fun `test contains set`() {
        val set = setFromString("-5..10, 20..50, 70..120")
        checkMethodWithBooleanResult(
            listOf(
                range(-5, 120),
                setFromString("-5..10, 20..50, 70..120, 200"),
                setFromString("-50..50, 66..200"),
                unknown()
            ) to { it -> set in it },
            listOf(
                point(42),
                range(0, 100),
                empty()
            ) to { it -> set !in it }
        )
    }

    fun `test contains empty`() {
        val empty = empty()
        checkMethodWithBooleanResult(
            listOf(
                point(42),
                range(0, 100),
                setFromString("0, 11, 42"),
                empty(),
                unknown()
            ) to { it -> empty in it }
        )
    }

    fun `test contains unknown`() {
        val unknown = unknown()
        checkMethodWithBooleanResult(
            listOf(
                point(42),
                range(0, 100),
                setFromString("0, 11, 42"),
                unknown()) to { it -> unknown in it },
            listOf(empty()) to { it -> unknown !in it }
        )
    }

    fun `test compare empty`() {
        val empty = empty()
        (ComparisonOp.values() + EqualityOp.EQ).forEach {
            checkMethodWithBooleanResult(
                listOf(
                    point(42),
                    range(0, 100),
                    setFromString("0, 11, 42"),
                    empty(),
                    unknown()
                ) to { other ->
                    (empty.compare(it as BoolOp, other).toList() + other.compare(it, empty).toList()).all { set -> set.isEmpty }
                }
            )
        }

        checkMethodWithBooleanResult(
            listOf(
                point(42),
                range(0, 100),
                setFromString("0, 11, 42"),
                empty(),
                unknown()
            ) to { other ->
                val op = EqualityOp.EXCLEQ
                val (leftEmpty, rightOther) = empty.compare(op, other)
                val (leftOther, rightEmpty) = other.compare(op, empty)
                leftEmpty.isEmpty && rightEmpty.isEmpty && leftOther == other && rightOther == other
            }
        )
    }

    fun `test compare unknown`() {
        val unknown = unknown()
        (ComparisonOp.values() + EqualityOp.EQ).forEach {
            checkMethodWithBooleanResult(
                ignore = Empty::class,
                pair = listOf(
                    point(42),
                    range(0, 100),
                    setFromString("0, 11, 42"),
                    unknown()
                ) to { other ->
                    (unknown.compare(it as BoolOp, other).toList() + other.compare(it, unknown).toList()).all { set -> set.isUnknown }
                }
            )
        }

        checkMethodWithBooleanResult(
            ignore = listOf(Unknown::class),
            pair = listOf(
                point(42),
                range(0, 100),
                setFromString("0, 11, 42"),
                empty()
            ) to { other ->
                val op = EqualityOp.EXCLEQ
                val (leftUnknown, rightOther) = unknown.compare(op, other)
                val (leftOther, rightUnknown) = other.compare(op, unknown)
                leftUnknown.isUnknown && rightUnknown.isUnknown && leftOther.isEmpty && rightOther.isEmpty
            }
        )

        assertTrue(unknown.compare(EqualityOp.EXCLEQ, unknown).toList().all { it.isUnknown })
    }

    private fun checkCompare(lhs: LongRangeSet, rhs: LongRangeSet, op: BoolOp, expectedLeft: String, expectedRight: String) =
        lhs.compare(op, rhs).let {
            checkSet(expectedLeft, it.first)
            checkSet(expectedRight, it.second)
        }

    fun `test compare point to point`() {
        val point = point(42)

        val otherPoint = point(44)
        checkCompare(point, otherPoint, EqualityOp.EQ, "{}", "{}")
        checkCompare(point, otherPoint, EqualityOp.EXCLEQ, "{42}", "{44}")
        checkCompare(point, otherPoint, ComparisonOp.LT, "{42}", "{44}")
        checkCompare(point, otherPoint, ComparisonOp.LTEQ, "{42}", "{44}")
        checkCompare(point, otherPoint, ComparisonOp.GT, "{}", "{}")
        checkCompare(point, otherPoint, ComparisonOp.GTEQ, "{}", "{}")

        val equalPoint = point(42)
        checkCompare(point, equalPoint, EqualityOp.EQ, "{42}", "{42}")
        checkCompare(point, equalPoint, EqualityOp.EXCLEQ, "{}", "{}")
        checkCompare(point, equalPoint, ComparisonOp.LT, "{}", "{}")
        checkCompare(point, equalPoint, ComparisonOp.LTEQ, "{42}", "{42}")
        checkCompare(point, equalPoint, ComparisonOp.GT, "{}", "{}")
        checkCompare(point, equalPoint, ComparisonOp.GTEQ, "{42}", "{42}")
    }

    fun `test compare point to range`() {
        val point = point(42)

        val rangeWithoutIntersect = range(-10, 4)
        checkCompare(point, rangeWithoutIntersect, EqualityOp.EQ, "{}", "{}")
        checkCompare(point, rangeWithoutIntersect, EqualityOp.EXCLEQ, "{42}", "{-10..4}")
        checkCompare(point, rangeWithoutIntersect, ComparisonOp.LT, "{}", "{}")
        checkCompare(point, rangeWithoutIntersect, ComparisonOp.LTEQ, "{}", "{}")
        checkCompare(point, rangeWithoutIntersect, ComparisonOp.GT, "{42}", "{-10..4}")
        checkCompare(point, rangeWithoutIntersect, ComparisonOp.GTEQ, "{42}", "{-10..4}")

        val rangeWithIntersect = range(-10, 100)
        checkCompare(point, rangeWithIntersect, EqualityOp.EQ, "{42}", "{42}")
        checkCompare(point, rangeWithIntersect, EqualityOp.EXCLEQ, "{}", "{-10..41, 43..100}")
        checkCompare(point, rangeWithIntersect, ComparisonOp.LT, "{42}", "{43..100}")
        checkCompare(point, rangeWithIntersect, ComparisonOp.LTEQ, "{42}", "{42..100}")
        checkCompare(point, rangeWithIntersect, ComparisonOp.GT, "{42}", "{-10..41}")
        checkCompare(point, rangeWithIntersect, ComparisonOp.GTEQ, "{42}", "{-10..42}")
    }

    fun `test compare point to set`() {
        val point = point(42)

        val setWithoutIntersect = setFromString("-50..0, 41, 100..102")
        checkCompare(point, setWithoutIntersect, EqualityOp.EQ, "{}", "{}")
        checkCompare(point, setWithoutIntersect, EqualityOp.EXCLEQ, "{42}", "{-50..0, 41, 100..102}")
        checkCompare(point, setWithoutIntersect, ComparisonOp.LT, "{42}", "{100..102}")
        checkCompare(point, setWithoutIntersect, ComparisonOp.LTEQ, "{42}", "{100..102}")
        checkCompare(point, setWithoutIntersect, ComparisonOp.GT, "{42}", "{-50..0, 41}")
        checkCompare(point, setWithoutIntersect, ComparisonOp.GTEQ, "{42}", "{-50..0, 41}")

        val setWithIntersectInPoint = setFromString("-50..0, 42, 100..102")
        checkCompare(point, setWithIntersectInPoint, EqualityOp.EQ, "{42}", "{42}")
        checkCompare(point, setWithIntersectInPoint, EqualityOp.EXCLEQ, "{}", "{-50..0, 100..102}")
        checkCompare(point, setWithIntersectInPoint, ComparisonOp.LT, "{42}", "{100..102}")
        checkCompare(point, setWithIntersectInPoint, ComparisonOp.LTEQ, "{42}", "{42, 100..102}")
        checkCompare(point, setWithIntersectInPoint, ComparisonOp.GT, "{42}", "{-50..0}")
        checkCompare(point, setWithIntersectInPoint, ComparisonOp.GTEQ, "{42}", "{-50..0, 42}")

        val setWithIntersectInRange = setFromString("-50..0, 30..50, 100..102")
        checkCompare(point, setWithIntersectInRange, EqualityOp.EQ, "{42}", "{42}")
        checkCompare(point, setWithIntersectInRange, EqualityOp.EXCLEQ, "{}", "{-50..0, 30..41, 43..50, 100..102}")
        checkCompare(point, setWithIntersectInRange, ComparisonOp.LT, "{42}", "{43..50, 100..102}")
        checkCompare(point, setWithIntersectInRange, ComparisonOp.LTEQ, "{42}", "{42..50, 100..102}")
        checkCompare(point, setWithIntersectInRange, ComparisonOp.GT, "{42}", "{-50..0, 30..41}")
        checkCompare(point, setWithIntersectInRange, ComparisonOp.GTEQ, "{42}", "{-50..0, 30..42}")
    }

    fun `test compare range to range`() {
        val range = range(-1, 77)

        val rangeWithoutIntersect = range(-10, -2)
        checkCompare(range, rangeWithoutIntersect, EqualityOp.EQ, "{}", "{}")
        checkCompare(range, rangeWithoutIntersect, EqualityOp.EXCLEQ, "{-1..77}", "{-10..-2}")
        checkCompare(range, rangeWithoutIntersect, ComparisonOp.LT, "{}", "{}")
        checkCompare(range, rangeWithoutIntersect, ComparisonOp.LTEQ, "{}", "{}")
        checkCompare(range, rangeWithoutIntersect, ComparisonOp.GT, "{-1..77}", "{-10..-2}")
        checkCompare(range, rangeWithoutIntersect, ComparisonOp.GTEQ, "{-1..77}", "{-10..-2}")

        val rangeWithIntersect = range(-9, 11)
        checkCompare(range, rangeWithIntersect, EqualityOp.EQ, "{-1..11}", "{-1..11}")
        checkCompare(range, rangeWithIntersect, EqualityOp.EXCLEQ, "{12..77}", "{-9..-2}")
        checkCompare(range, rangeWithIntersect, ComparisonOp.LT, "{-1..10}", "{0..11}")
        checkCompare(range, rangeWithIntersect, ComparisonOp.LTEQ, "{-1..11}", "{-1..11}")
        checkCompare(range, rangeWithIntersect, ComparisonOp.GT, "{-1..77}", "{-9..11}")
        checkCompare(range, rangeWithIntersect, ComparisonOp.GTEQ, "{-1..77}", "{-9..11}")
    }

    fun `test compare range to set`() {
        val range = range(-1, 77)

        val setWithoutIntersect = setFromString("-50..-2, 78, 100..102")
        checkCompare(range, setWithoutIntersect, EqualityOp.EQ, "{}", "{}")
        checkCompare(range, setWithoutIntersect, EqualityOp.EXCLEQ, "{-1..77}", "{-50..-2, 78, 100..102}")
        checkCompare(range, setWithoutIntersect, ComparisonOp.LT, "{-1..77}", "{78, 100..102}")
        checkCompare(range, setWithoutIntersect, ComparisonOp.LTEQ, "{-1..77}", "{78, 100..102}")
        checkCompare(range, setWithoutIntersect, ComparisonOp.GT, "{-1..77}", "{-50..-2}")
        checkCompare(range, setWithoutIntersect, ComparisonOp.GTEQ, "{-1..77}", "{-50..-2}")

        val setWithIntersectInRange = setFromString("-50..0, 30..50, 100..102")
        checkCompare(range, setWithIntersectInRange, EqualityOp.EQ, "{-1, 0, 30..50}", "{-1, 0, 30..50}")
        checkCompare(range, setWithIntersectInRange, EqualityOp.EXCLEQ, "{1..29, 51..77}", "{-50..-2, 100..102}")
        checkCompare(range, setWithIntersectInRange, ComparisonOp.LT, "{-1..77}", "{0, 30..50, 100..102}")
        checkCompare(range, setWithIntersectInRange, ComparisonOp.LTEQ, "{-1..77}", "{-1, 0, 30..50, 100..102}")
        checkCompare(range, setWithIntersectInRange, ComparisonOp.GT, "{-1..77}", "{-50..0, 30..50}")
        checkCompare(range, setWithIntersectInRange, ComparisonOp.GTEQ, "{-1..77}", "{-50..0, 30..50}")
    }

    fun `test compare symmetric`() {
        val ranges = listOf(
            point(42),
            range(0, 100),
            setFromString("0, 11, 42"),
            empty(),
            unknown()
        )

        val operators = listOf<Pair<BoolOp, BoolOp>>(
            EqualityOp.EQ to EqualityOp.EQ,
            EqualityOp.EXCLEQ to EqualityOp.EXCLEQ,
            ComparisonOp.LT to ComparisonOp.GT,
            ComparisonOp.LTEQ to ComparisonOp.GTEQ
        )

        ranges.forEach { range ->
            operators.forEach { op ->
                checkMethodWithBooleanResult(
                    ranges to { other ->
                        val (leftRange, rightOther) = range.compare(op.first, other)
                        val (leftOther, rightRange) = other.compare(op.second, range)
                        leftRange == rightRange && leftOther == rightOther
                    }
                )
            }
        }
    }

    fun `test check checkHasAllTypes function`() {
        checkHasAllTypes(listOf(empty(), unknown(), point(42), range(5, 55), setFromString("0, 2, 4")))
        assertFails { checkHasAllTypes(emptyList()) }
        assertFails { checkHasAllTypes(listOf(empty(), point(42))) }
    }

    fun `test add empty and unknown`() {
        TyInteger.VALUES.forEach { type ->
            val ranges = listOf(
                point(42, type),
                range(0, 100, type),
                setFromString("0, 11, 42", type),
                unknown()
            )

            checkMethodWithBooleanResult(
                ranges + empty() to { other ->
                    checkAdd(empty(), other, "{}")
                    true
                }
            )

            checkMethodWithBooleanResult(
                ignore = Empty::class,
                pair = ranges to { other ->
                    checkAdd(unknown(), other, "{?}")
                    true
                }
            )
        }
    }

    fun `test add point to point`() {
        val point = point(42)
        checkAdd(point, point(42), "{84}")
        checkAdd(point, point(-42), "{0}")

        val filter = { it: Long -> it in -128L..127L }
        val pointI8 = point(42, TyInteger.I8)
        checkAdd(pointI8, point(85, TyInteger.I8), "{127}", filter)
        checkAdd(pointI8, point(86, TyInteger.I8), "{!}", filter)
        checkAdd(pointI8, point(120, TyInteger.I8), "{!}", filter)
        checkAdd(pointI8, point(-128, TyInteger.I8), "{-86}", filter)

        checkAdd(point(-1, TyInteger.I8), point(-128, TyInteger.I8), "{!}", filter)
        checkAdd(point(-30, TyInteger.I8), point(-100, TyInteger.I8), "{!}", filter)

        listOf(TyInteger.U64, TyInteger.I128, TyInteger.U128, TyInteger.USize).forEach {
            checkAdd(point(Long.MAX_VALUE, it), point(55, it), "{?}")
        }

        checkAdd(point(Long.MIN_VALUE, TyInteger.I128), point(-5, TyInteger.I128), "{?}")
    }

    fun `test add point to range`() {
        val point = point(42)
        checkAdd(point, range(0, 50), "{42..92}")
        checkAdd(point, range(-10, 5), "{32..47}")

        val filter = { it: Long -> it in -128L..127L }
        val pointI8 = point(42, TyInteger.I8)
        checkAdd(pointI8, range(80, 127, TyInteger.I8), "{122..127}", filter)
        checkAdd(pointI8, range(86, 100, TyInteger.I8), "{!}", filter)
        checkAdd(pointI8, range(-128, 100, TyInteger.I8), "{-86..127}", filter)

        checkAdd(point(-1, TyInteger.I8), range(-128, 10, TyInteger.I8), "{-128..9}", filter)
        checkAdd(point(-30, TyInteger.I8), range(-110, -99, TyInteger.I8), "{!}", filter)

        listOf(TyInteger.U64, TyInteger.I128, TyInteger.U128, TyInteger.USize).forEach {
            checkAdd(point(Long.MAX_VALUE - 60, it), range(55, 70, it), "{?}")
        }

        checkAdd(point(Long.MIN_VALUE + 20, TyInteger.I128), range(-30, -10, TyInteger.I128), "{?}")
    }

    fun `test add point to set`() {
        val point = point(42)
        checkAdd(point, setFromString("-5, 11..22"), "{37, 53..64}")
        checkAdd(point, setFromString("1, 11..22, 33..55"), "{43, 53..64, 75..97}")

        val filter = { it: Long -> it in -128L..127L }
        val pointI8 = point(42, TyInteger.I8)
        checkAdd(pointI8, setFromString("-5, 11..100", TyInteger.I8), "{37, 53..127}", filter)
        checkAdd(pointI8, setFromString("89, 100..105", TyInteger.I8), "{!}", filter)
        checkAdd(pointI8, setFromString("-128, 100..105", TyInteger.I8), "{-86}", filter)

        checkAdd(point(-1, TyInteger.I8), setFromString("-128, -100..1", TyInteger.I8), "{-101..0}", filter)
        checkAdd(point(-30, TyInteger.I8), setFromString("-128, -105..-100", TyInteger.I8), "{!}", filter)

        listOf(TyInteger.U64, TyInteger.I128, TyInteger.U128, TyInteger.USize).forEach {
            checkAdd(point(Long.MAX_VALUE - 60, it), setFromString("-128, 100..150", it), "{?}")
        }

        checkAdd(point(Long.MIN_VALUE + 20, TyInteger.I128), setFromString("-33..-10, 0", TyInteger.I128), "{?}")
    }

    fun `test add range to range`() {
        checkAdd(range(Long.MAX_VALUE - 10, Long.MAX_VALUE), range(1, 5), "{${Long.MAX_VALUE - 9}..${Long.MAX_VALUE}}")
        checkAdd(range(Long.MIN_VALUE, Long.MIN_VALUE + 10), range(-1, 5), "{${Long.MIN_VALUE}..${Long.MIN_VALUE + 15}}")

        val filter = { it: Long -> it in -128L..127L }
        checkAdd(range(-128, 127, TyInteger.I8), range(-10, 10, TyInteger.I8), "{-128..127}", filter)
        checkAdd(range(-100, 0, TyInteger.I8), range(100, 120, TyInteger.I8), "{0..120}", filter)

        listOf(TyInteger.U64, TyInteger.I128, TyInteger.U128, TyInteger.USize).forEach {
            checkAdd(range(Long.MAX_VALUE - 60, Long.MAX_VALUE - 50, it), range(10, 66, it), "{?}")
        }

        checkAdd(range(Long.MIN_VALUE + 20, Long.MIN_VALUE + 30, TyInteger.I128), range(-21, 0, TyInteger.I128), "{?}")
    }

    fun `test add range to set`() {
        checkAdd(range(10, 20), setFromString("-5, 11..22"), "{5..15, 21..42}")
        checkAdd(range(-77, 2), setFromString("1, 11..22, 33..55"), "{-76..57}")

        val filter = { it: Long -> it in -128L..127L }
        val rangeI8 = range(42, 100, TyInteger.I8)
        checkAdd(rangeI8, setFromString("-5, 11..100", TyInteger.I8), "{37..127}", filter)
        checkAdd(rangeI8, setFromString("89, 100..105", TyInteger.I8), "{!}", filter)
        checkAdd(rangeI8, setFromString("-128, 100..105", TyInteger.I8), "{-86..-28}", filter)

        checkAdd(range(-1, 5, TyInteger.I8), setFromString("-128, -100..1", TyInteger.I8), "{-128..-123, -101..6}", filter)
        checkAdd(range(-35, -29, TyInteger.I8), setFromString("-128, -105..-100", TyInteger.I8), "{!}", filter)

        listOf(TyInteger.U64, TyInteger.I128, TyInteger.U128, TyInteger.USize).forEach {
            checkAdd(range(Long.MAX_VALUE - 60, Long.MAX_VALUE - 50, it), setFromString("-128, 100..150", it), "{?}")
        }

        checkAdd(range(Long.MIN_VALUE + 20, Long.MIN_VALUE + 30, TyInteger.I128), setFromString("-33..-10, 0", TyInteger.I128), "{?}")
    }

    fun `test add set to set`() {
        checkAdd(setFromString("-5..0, 23..44"), setFromString("-5, 11..22"), "{-10..-5, 6..66}")
        checkAdd(setFromString("-5, 11..22"), setFromString("1, 11..22, 33..55"), "{-4, 6..77}")

        val filter = { it: Long -> it in -128L..127L }
        val setI8 = setFromString("-5, 22, 30..40", TyInteger.I8)
        checkAdd(setI8, setFromString("-5, 11..100", TyInteger.I8), "{-10, 6..127}", filter)
        checkAdd(setI8, setFromString("89, 100..105", TyInteger.I8), "{84, 95..100, 111, 119..127}", filter)
        checkAdd(setI8, setFromString("-128, 127", TyInteger.I8), "{-106, -98..-88, 122}", filter)

        checkAdd(range(-1, 5, TyInteger.I8), setFromString("-128, -100..1", TyInteger.I8), "{-128..-123, -101..6}", filter)
        checkAdd(setFromString("-128..-127, 126..127", TyInteger.I8), setFromString("-128..-127, 126..127", TyInteger.I8), "{-2..0}", filter)

        listOf(TyInteger.U64, TyInteger.I128, TyInteger.U128, TyInteger.USize).forEach {
            checkAdd(setFromString("${Long.MAX_VALUE - 60}..${Long.MAX_VALUE - 50}, 0", it), setFromString("-128, 100..150", it), "{?}")
        }

        checkAdd(setFromString("${Long.MIN_VALUE + 20}..${Long.MIN_VALUE + 30}, 0", TyInteger.I128), setFromString("-33..-10, 0", TyInteger.I128), "{?}")
    }

    fun `test sub`() {
        checkSub(range(10, 15, TyInteger.U8), point(14, TyInteger.U8), "{0, 1}") { it in 0L..255L }

        TyInteger.VALUES.filter { it.name.startsWith("i") }.forEach { type ->
            checkSub(range(10, 15, type), point(16, type), "{-6..-1}") { it in type.MIN_POSSIBLE..type.MAX_POSSIBLE }
        }

        TyInteger.VALUES.filter { it.name.startsWith("u") }.forEach { type ->
            checkSub(range(10, 15, type), point(16, type), "{!}") { it >= 0 }
        }

        (TyInteger.VALUES - TyInteger.I128).forEach { type ->
            checkSub(point(type.MIN_POSSIBLE, type), point(1, type), "{!}") { it in type.MIN_POSSIBLE..type.MAX_POSSIBLE }
        }

        checkSub(point(TyInteger.I128.MIN_POSSIBLE, TyInteger.I128), point(1, TyInteger.I128), "{?}")
    }

    fun `test multiply empty and unknown`() {
        TyInteger.VALUES.forEach { type ->
            val ranges = listOf(
                point(42, type),
                range(0, 100, type),
                setFromString("0, 11, 42", type),
                unknown()
            )

            checkMethodWithBooleanResult(
                ranges + empty() to { other ->
                    checkMultiply(empty(), other, "{}")
                    true
                }
            )

            checkMethodWithBooleanResult(
                ignore = Empty::class,
                pair = ranges to { other ->
                    checkMultiply(unknown(), other, "{?}")
                    true
                }
            )
        }
    }

    fun `test multiply by zero`() {
        TyInteger.VALUES.forEach { type ->
            val ranges = listOf(
                point(42, type),
                range(0, 100, type),
                setFromString("0, 11, 42", type),
                unknown()
            )

            val zeroPoint = point(0, type)
            checkMethodWithBooleanResult(
                ignore = Empty::class,
                pair = ranges to { other ->
                    checkMultiply(zeroPoint, other, "{0}")
                    true
                }
            )
        }
    }

    fun `test multiply by one and minus one`() {
        TyInteger.VALUES.forEach { type ->
            val ranges = listOf(
                point(42, type),
                range(0, 100, type),
                setFromString("0, 11, 42", type),
                unknown(),
                empty()
            )

            val onePoint = point(1, type)
            val minusOnePoint = point(-1, type)
            checkMethodWithBooleanResult(
                ranges to { other ->
                    checkMultiply(onePoint, other, other.toString())
                    if (type.name.startsWith("i")) {
                        checkMultiply(minusOnePoint, other, other.unaryMinus().toString())
                    }
                    true
                }
            )
        }

        (TyInteger.VALUES - TyInteger.I128).filter { it.name.startsWith("i") }.forEach { type ->
            checkMultiply(point(type.MIN_POSSIBLE, type), point(-1, type), "{!}") { it in type.MIN_POSSIBLE..type.MAX_POSSIBLE }
        }

        checkMultiply(point(TyInteger.I128.MIN_POSSIBLE, TyInteger.I128), point(-1, TyInteger.I128), "{?}")
    }

    fun `test multiply point to point`() {
        val point = point(42)
        checkMultiply(point, point(2), "{84}")
        checkMultiply(point, point(-2), "{-84}")

        val filter = { it: Long -> it in -128L..127L }
        val pointI8 = point(42, TyInteger.I8)
        checkMultiply(pointI8, point(2, TyInteger.I8), "{84}", filter)
        checkMultiply(pointI8, point(4, TyInteger.I8), "{!}", filter)
        checkMultiply(pointI8, point(-4, TyInteger.I8), "{!}", filter)

        checkMultiply(point(-1, TyInteger.I8), point(-128, TyInteger.I8), "{!}", filter)
        checkMultiply(point(-30, TyInteger.I8), point(-100, TyInteger.I8), "{!}", filter)

        listOf(TyInteger.U64, TyInteger.I128, TyInteger.U128, TyInteger.USize).forEach {
            checkMultiply(point(Long.MAX_VALUE, it), point(55, it), "{?}")
        }

        checkMultiply(point(Long.MIN_VALUE, TyInteger.I128), point(5, TyInteger.I128), "{?}")
    }

    fun `test multiply point to range`() {
        val point = point(42)
        checkMultiply(point, range(0, 4), "{0, 42..168}")
        checkMultiply(point, range(-1, 5), "{-42, 0, 42..210}")

        val filter = { it: Long -> it in -128L..127L }
        val pointI8 = point(9, TyInteger.I8)
        checkMultiply(pointI8, range(3, 9, TyInteger.I8), "{27..81}", filter)
        checkMultiply(pointI8, range(15, 20, TyInteger.I8), "{!}", filter)
        checkMultiply(pointI8, range(-8, 6, TyInteger.I8), "{-72..-9, 0, 9..54}", filter)

        checkMultiply(point(-2, TyInteger.I8), range(-64, 10, TyInteger.I8), "{-20..-2, 0, 2..127}", filter)
        checkMultiply(point(-30, TyInteger.I8), range(-110, -99, TyInteger.I8), "{!}", filter)

        listOf(TyInteger.U64, TyInteger.I128, TyInteger.U128, TyInteger.USize).forEach {
            checkMultiply(point(Long.MAX_VALUE - 60, it), range(55, 70, it), "{?}")
        }

        checkMultiply(point(Long.MIN_VALUE + 20, TyInteger.I128), range(-4, 2, TyInteger.I128), "{?}")
    }

    fun `test multiply point to set`() {
        val point = point(3)
        checkMultiply(point, setFromString("-5, 11..22"), "{-15, 33..66}")
        checkMultiply(point, setFromString("1, 11..22, 33..55"), "{3, 33..66, 99..165}")

        val filter = { it: Long -> it in -128L..127L }
        val pointI8 = point(5, TyInteger.I8)
        checkMultiply(pointI8, setFromString("-5, 11..100", TyInteger.I8), "{-25, 55..127}", filter)
        checkMultiply(pointI8, setFromString("89, 100..105", TyInteger.I8), "{!}", filter)
        checkMultiply(pointI8, setFromString("-25, 6..8", TyInteger.I8), "{-125, 30..40}", filter)

        checkMultiply(point(-1, TyInteger.I8), setFromString("-128, -100..1", TyInteger.I8), "{-1..100}", filter)
        checkMultiply(point(-30, TyInteger.I8), setFromString("-128, -105..-100", TyInteger.I8), "{!}", filter)

        listOf(TyInteger.U64, TyInteger.I128, TyInteger.U128, TyInteger.USize).forEach {
            checkMultiply(point(Long.MAX_VALUE - 60, it), setFromString("-128, 100..150", it), "{?}")
        }

        checkMultiply(point(Long.MIN_VALUE + 20, TyInteger.I128), setFromString("-33..-10, 2", TyInteger.I128), "{?}")
    }

    fun `test multiply range to range`() {
        checkMultiply(range(Long.MAX_VALUE - 10, Long.MAX_VALUE), range(2, 5), "{!}")
        checkMultiply(range(Long.MAX_VALUE / 5, Long.MAX_VALUE / 5 + 10), range(5, 8), "{${Long.MAX_VALUE - Long.MAX_VALUE % 5}..${Long.MAX_VALUE}}")

        val filter = { it: Long -> it in -128L..127L }
        checkMultiply(range(-128, 127, TyInteger.I8), range(-10, 10, TyInteger.I8), "{-128..127}", filter)
        checkMultiply(range(-100, 0, TyInteger.I8), range(100, 120, TyInteger.I8), "{-128..-100, 0}", filter)

        listOf(TyInteger.U64, TyInteger.I128, TyInteger.U128, TyInteger.USize).forEach {
            checkMultiply(range(Long.MAX_VALUE - 60, Long.MAX_VALUE - 50, it), range(10, 66, it), "{?}")
        }

        checkMultiply(range(Long.MIN_VALUE + 20, Long.MIN_VALUE + 30, TyInteger.I128), range(-1, 0, TyInteger.I128), "{0, ${-(Long.MIN_VALUE + 30)}..${-(Long.MIN_VALUE + 20)}}")
    }

    fun `test multiply range to set`() {
        checkMultiply(range(10, 20), setFromString("-5, 11..22"), "{-100..-50, 110..440}")
        checkMultiply(range(-77, 2), setFromString("1, 11..22, 33..55"), "{-4235..2, 11..110}")

        val filter = { it: Long -> it in -128L..127L }
        val rangeI8 = range(8, 100, TyInteger.I8)
        checkMultiply(rangeI8, setFromString("-5, 11..100", TyInteger.I8), "{-128..-40, 88..127}", filter)
        checkMultiply(rangeI8, setFromString("89, 100..105", TyInteger.I8), "{!}", filter)
        checkMultiply(rangeI8, setFromString("-128, 100..105", TyInteger.I8), "{!}", filter)

        checkMultiply(range(-1, 5, TyInteger.I8), setFromString("-128, -100..1", TyInteger.I8), "{-128..100}", filter)
        checkMultiply(range(-35, -29, TyInteger.I8), setFromString("-128, -105..-100", TyInteger.I8), "{!}", filter)

        listOf(TyInteger.U64, TyInteger.I128, TyInteger.U128, TyInteger.USize).forEach {
            checkMultiply(range(Long.MAX_VALUE - 60, Long.MAX_VALUE - 50, it), setFromString("-128, 100..150", it), "{?}")
        }

        checkMultiply(range(Long.MIN_VALUE + 20, Long.MIN_VALUE + 30, TyInteger.I128), setFromString("-33..-10, 0", TyInteger.I128), "{?}")
    }

    fun `test multiply set to set`() {
        checkMultiply(setFromString("-5..0, 23..44"), setFromString("-5, 11..22"), "{-220..-115, -110..-11, 0, 5..25, 253..968}")
        checkMultiply(setFromString("-5, 11..22"), setFromString("1, 11..22, 33..55"), "{-275..-165, -110..-55, -5, 11..22, 121..1210}")

        val filter = { it: Long -> it in -128L..127L }
        val setI8 = setFromString("-5, 22, 30..40", TyInteger.I8)
        checkMultiply(setI8, setFromString("-5, 11..100", TyInteger.I8), "{-128..-55, 25}", filter)
        checkMultiply(setI8, setFromString("3, 100..105", TyInteger.I8), "{-15, 66, 90..120}", filter)
        checkMultiply(setI8, setFromString("-128, 127", TyInteger.I8), "{!}", filter)

        checkMultiply(range(-1, 5, TyInteger.I8), setFromString("-128, -100..1, 3, 4", TyInteger.I8), "{-128..100}", filter)
        checkMultiply(setFromString("-128..-127, 5..127", TyInteger.I8), setFromString("-128..-6, 126..127", TyInteger.I8), "{-128..-30}", filter)

        listOf(TyInteger.U64, TyInteger.I128, TyInteger.U128, TyInteger.USize).forEach {
            checkMultiply(setFromString("${Long.MAX_VALUE - 60}..${Long.MAX_VALUE - 50}, 0", it), setFromString("-128, 100..150", it), "{?}")
        }

        checkMultiply(setFromString("${Long.MIN_VALUE + 20}..${Long.MIN_VALUE + 30}, 0", TyInteger.I128), setFromString("-33..-10, 0", TyInteger.I128), "{?}")
    }

    fun `test mod by zero`() {
        TyInteger.VALUES.forEach { type ->
            val ranges = listOf(
                point(42, type),
                range(0, 100, type),
                setFromString("0, 11, 42", type),
                unknown(),
                empty()
            )

            val zeroPoint = point(0, type)
            checkMethodWithBooleanResult(
                ranges to { other ->
                    checkMod(other, zeroPoint, "{z}")
                    true
                }
            )
        }
    }

    fun `test mod point to point`() {
        val point = point(42)
        checkMod(point, point(2), "{0}")
        checkMod(point, point(-2), "{0}")

        val filter = { it: Long -> it in -128L..127L }
        val pointI8 = point(42, TyInteger.I8)
        checkMod(pointI8, point(7, TyInteger.I8), "{0}", filter)
        checkMod(pointI8, point(13, TyInteger.I8), "{3}", filter)
        checkMod(pointI8, point(8, TyInteger.I8), "{2}", filter)

        checkMod(point(-1, TyInteger.I8), point(-128, TyInteger.I8), "{-1}", filter)
        checkMod(point(-30, TyInteger.I8), point(-100, TyInteger.I8), "{-30}", filter)

        listOf(TyInteger.U64, TyInteger.I128, TyInteger.U128, TyInteger.USize).forEach {
            checkMod(point(Long.MAX_VALUE, it), point(55, it), "{7}")
        }

        checkMod(point(Long.MIN_VALUE, TyInteger.I128), point(5, TyInteger.I128), "{-3}")
    }

    fun `test mod point to range`() {
        val point = point(42)
        checkMod(point, range(0, 4), "{0..3}")
        checkMod(point, range(-1, 5), "{0..4}")

        val filter = { it: Long -> it in -128L..127L }
        val pointI8 = point(9, TyInteger.I8)
        checkMod(pointI8, range(3, 9, TyInteger.I8), "{0..8}", filter)
        checkMod(pointI8, range(15, 20, TyInteger.I8), "{9}", filter)
        checkMod(pointI8, range(-8, 6, TyInteger.I8), "{0..7}", filter)

        checkMod(point(-2, TyInteger.I8), range(-64, 10, TyInteger.I8), "{-2..0}", filter)
        checkMod(point(-30, TyInteger.I8), range(-110, -99, TyInteger.I8), "{-30}", filter)

        listOf(TyInteger.U64, TyInteger.I128, TyInteger.U128, TyInteger.USize).forEach {
            checkMod(point(Long.MAX_VALUE - 60, it), range(55, 70, it), "{0..69}")
        }

        checkMod(point(Long.MIN_VALUE + 20, TyInteger.I128), range(-4, 2, TyInteger.I128), "{-3..0}")
    }

    fun `test mod point to set`() {
        val point = point(3)
        checkMod(point, setFromString("-5, 11..22"), "{3}")
        checkMod(point, setFromString("1, 11..22, 33..55"), "{0..3}")
        checkMod(point, setFromString("${Long.MIN_VALUE}, 0"), "{3}")

        val filter = { it: Long -> it in -128L..127L }
        val pointI8 = point(5, TyInteger.I8)
        checkMod(pointI8, setFromString("-5, 11..100", TyInteger.I8), "{0..5}", filter)
        checkMod(pointI8, setFromString("89, 100..105", TyInteger.I8), "{5}", filter)
        checkMod(pointI8, setFromString("-25, 6..8", TyInteger.I8), "{5}", filter)

        checkMod(point(-1, TyInteger.I8), setFromString("-128, -100..1", TyInteger.I8), "{-1, 0}", filter)
        checkMod(point(-30, TyInteger.I8), setFromString("-128, -105..-100", TyInteger.I8), "{-30}", filter)

        listOf(TyInteger.U64, TyInteger.I128, TyInteger.U128, TyInteger.USize).forEach {
            checkMod(point(Long.MAX_VALUE - 60, it), setFromString("-128, 100..150", it), "{0..149}")
        }

        checkMod(point(Long.MIN_VALUE + 20, TyInteger.I128), setFromString("-33..-10, 2", TyInteger.I128), "{-32..0}")
    }

    fun `test mod range to range`() {
        checkMod(range(Long.MAX_VALUE - 10, Long.MAX_VALUE), range(2, 5), "{0..4}")
        checkMod(range(Long.MAX_VALUE - 100, Long.MAX_VALUE), range(5, 8), "{0..7}")

        val filter = { it: Long -> it in -128L..127L }
        checkMod(range(-128, 127, TyInteger.I8), range(-10, 10, TyInteger.I8), "{-9..9}", filter)
        checkMod(range(-100, 0, TyInteger.I8), range(100, 120, TyInteger.I8), "{-100..0}", filter)

        listOf(TyInteger.U64, TyInteger.I128, TyInteger.U128, TyInteger.USize).forEach {
            checkMod(range(Long.MAX_VALUE - 60, Long.MAX_VALUE - 50, it), range(10, 66, it), "{0..65}")
        }

        checkMod(range(Long.MIN_VALUE + 20, Long.MIN_VALUE + 30, TyInteger.I128), range(-1, 0, TyInteger.I128), "{0}")
    }

    fun `test mod range to set`() {
        checkMod(range(10, 20), setFromString("-5, 11..22"), "{0..20}")
        checkMod(range(-77, 2), setFromString("1, 11..22, 33..55"), "{-54..2}")

        val filter = { it: Long -> it in -128L..127L }
        val rangeI8 = range(8, 100, TyInteger.I8)
        checkMod(rangeI8, setFromString("-5, 11..100", TyInteger.I8), "{0..99}", filter)
        checkMod(rangeI8, setFromString("89, 100..105", TyInteger.I8), "{0..100}", filter)
        checkMod(rangeI8, setFromString("-128, 102..105", TyInteger.I8), "{0..100}", filter)

        checkMod(range(-1, 5, TyInteger.I8), setFromString("-128, -100..1", TyInteger.I8), "{-1..5}", filter)
        checkMod(range(-35, -29, TyInteger.I8), setFromString("-128, -105..-100", TyInteger.I8), "{-35..0}", filter)

        listOf(TyInteger.U64, TyInteger.I128, TyInteger.U128, TyInteger.USize).forEach {
            checkMod(range(Long.MAX_VALUE - 60, Long.MAX_VALUE - 50, it), setFromString("-128, 100..150", it), "{0..149}")
        }

        checkMod(range(Long.MIN_VALUE + 20, Long.MIN_VALUE + 30, TyInteger.I128), setFromString("-33..-10, 0", TyInteger.I128), "{-32..0}")
    }

    fun `test mod set to set`() {
        checkMod(setFromString("-5..0, 23..44"), setFromString("-5, 11..22"), "{-5..21}")
        checkMod(setFromString("-5, 11..22"), setFromString("1, 11..22, 33..55"), "{-5..22}")

        val filter = { it: Long -> it in -128L..127L }
        val setI8 = setFromString("-5, 22, 30..40", TyInteger.I8)
        checkMod(setI8, setFromString("-5, 11..100", TyInteger.I8), "{-5..40}", filter)
        checkMod(setI8, setFromString("3, 100..105", TyInteger.I8), "{-5..40}", filter)
        checkMod(setI8, setFromString("-128, 127", TyInteger.I8), "{-5, 0..40}", filter)

        checkMod(range(-1, 5, TyInteger.I8), setFromString("-128, -100..1, 3, 4", TyInteger.I8), "{-1..5}", filter)
        checkMod(setFromString("-128..-127, 5..127", TyInteger.I8), setFromString("-128..-6, 126..127", TyInteger.I8), "{-128..127}", filter)

        listOf(TyInteger.U64, TyInteger.I128, TyInteger.U128, TyInteger.USize).forEach {
            checkMod(setFromString("${Long.MAX_VALUE - 60}..${Long.MAX_VALUE - 50}, 0", it), setFromString("-128, 100..150", it), "{0..149}")
        }

        checkMod(setFromString("${Long.MIN_VALUE + 20}..${Long.MIN_VALUE + 30}, 0", TyInteger.I128), setFromString("-33..-10, 0", TyInteger.I128), "{-32..0}")
    }

    fun `test div by zero`() {
        TyInteger.VALUES.forEach { type ->
            val ranges = listOf(
                point(42, type),
                range(0, 100, type),
                setFromString("0, 11, 42", type),
                unknown(),
                empty()
            )

            val zeroPoint = point(0, type)
            checkMethodWithBooleanResult(
                ranges to { other ->
                    checkDiv(other, zeroPoint, "{z}")
                    true
                }
            )
        }
    }

    fun `test div by one and minus one`() {
        TyInteger.VALUES.forEach { type ->
            val ranges = listOf(
                point(42, type),
                range(0, 100, type),
                setFromString("0, 11, 42", type),
                unknown(),
                empty()
            )

            val onePoint = point(1, type)
            val minusOnePoint = point(-1, type)
            checkMethodWithBooleanResult(
                ranges to { other ->
                    checkDiv(other, onePoint, other.toString())
                    if (type.name.startsWith("i")) {
                        checkDiv(other, minusOnePoint, other.unaryMinus().toString())
                    }
                    true
                }
            )
        }

        (TyInteger.VALUES - TyInteger.I128).filter { it.name.startsWith("i") }.forEach { type ->
            checkDiv(point(type.MIN_POSSIBLE, type), point(-1, type), "{!}") { it in type.MIN_POSSIBLE..type.MAX_POSSIBLE }
            checkDiv(point(-1, type), point(type.MIN_POSSIBLE, type), "{0}") { it in type.MIN_POSSIBLE..type.MAX_POSSIBLE }
        }

        checkDiv(point(TyInteger.I128.MIN_POSSIBLE, TyInteger.I128), point(-1, TyInteger.I128), "{?}")
        checkSet(all().subtract(point(Long.MIN_VALUE)).toString(), all() / point(-1L))
    }

    fun `test div point to point`() {
        val point = point(42)
        checkDiv(point, point(2), "{21}")
        checkDiv(point, point(-2), "{-21}")

        val filter = { it: Long -> it in -128L..127L }
        val pointI8 = point(42, TyInteger.I8)
        checkDiv(pointI8, point(7, TyInteger.I8), "{6}", filter)
        checkDiv(pointI8, point(13, TyInteger.I8), "{3}", filter)
        checkDiv(pointI8, point(8, TyInteger.I8), "{5}", filter)

        checkDiv(point(-1, TyInteger.I8), point(-128, TyInteger.I8), "{0}", filter)
        checkDiv(point(-30, TyInteger.I8), point(-100, TyInteger.I8), "{0}", filter)

        listOf(TyInteger.U64, TyInteger.I128, TyInteger.U128, TyInteger.USize).forEach {
            checkDiv(point(Long.MAX_VALUE, it), point(55, it), "{167697673397359560}")
        }

        checkDiv(point(Long.MIN_VALUE, TyInteger.I128), point(5, TyInteger.I128), "{-1844674407370955161}")
    }

    fun `test div point to range`() {
        val point = point(42)
        checkDiv(point, range(0, 4), "{10..42}")
        checkDiv(point, range(-1, 5), "{-42, 8..42}")

        val filter = { it: Long -> it in -128L..127L }
        val pointI8 = point(9, TyInteger.I8)
        checkDiv(pointI8, range(3, 9, TyInteger.I8), "{1..3}", filter)
        checkDiv(pointI8, range(15, 20, TyInteger.I8), "{0}", filter)
        checkDiv(pointI8, range(-8, 6, TyInteger.I8), "{-9..-1, 1..9}", filter)

        checkDiv(point(-2, TyInteger.I8), range(-64, 10, TyInteger.I8), "{-2..2}", filter)
        checkDiv(point(-30, TyInteger.I8), range(-110, -99, TyInteger.I8), "{0}", filter)

        listOf(TyInteger.U64, TyInteger.I128, TyInteger.U128, TyInteger.USize).forEach {
            checkDiv(point(Long.MAX_VALUE - 60, it), range(55, 70, it), "{131762457669353939..167697673397359559}")
        }

        checkDiv(point(Long.MIN_VALUE + 20, TyInteger.I128), range(-4, 2, TyInteger.I128), "{-9223372036854775788..-4611686018427387894, 2305843009213693947..9223372036854775788}")
    }

    fun `test div point to set`() {
        val point = point(3)
        checkDiv(point, setFromString("-5, 11..22"), "{0}")
        checkDiv(point, setFromString("1, 11..22, 33..55"), "{0, 3}")

        val filter = { it: Long -> it in -128L..127L }
        val pointI8 = point(5, TyInteger.I8)
        checkDiv(pointI8, setFromString("-5, 11..100", TyInteger.I8), "{-1, 0}", filter)
        checkDiv(pointI8, setFromString("89, 100..105", TyInteger.I8), "{0}", filter)
        checkDiv(pointI8, setFromString("-25, 6..8", TyInteger.I8), "{0}", filter)

        checkDiv(point(-1, TyInteger.I8), setFromString("-128, -100..1", TyInteger.I8), "{-1..1}", filter)
        checkDiv(point(-30, TyInteger.I8), setFromString("-128, -105..-100", TyInteger.I8), "{0}", filter)

        checkDiv(point(Long.MIN_VALUE + 20, TyInteger.I128), setFromString("-33..-10, 2", TyInteger.I128), "{-4611686018427387894, 279496122328932599..922337203685477578}")
    }

    fun `test div range to range`() {
        checkDiv(range(Long.MAX_VALUE - 10, Long.MAX_VALUE), range(200, 500), "{18446744073709551..46116860184273879}")
        checkDiv(range(Long.MAX_VALUE - 100, Long.MAX_VALUE), range(5436, 8223), "{1121655361407609..1696720389414050}")

        val filter = { it: Long -> it in -128L..127L }
        checkDiv(range(-128, 127, TyInteger.I8), range(-10, 10, TyInteger.I8), "{-128..127}", filter)
        checkDiv(range(-100, 0, TyInteger.I8), range(100, 120, TyInteger.I8), "{-1, 0}", filter)

        checkDiv(range(Long.MIN_VALUE + 20, Long.MIN_VALUE + 30, TyInteger.I128), range(-1, 0, TyInteger.I128), "{9223372036854775778..9223372036854775788}")
    }

    fun `test div range to set`() {
        checkDiv(range(10, 20), setFromString("-5, 11..22"), "{-4..-2, 0, 1}")
        checkDiv(range(-77, 2), setFromString("1, 11..22, 33..55"), "{-77..2}")

        val filter = { it: Long -> it in -128L..127L }
        val rangeI8 = range(8, 100, TyInteger.I8)
        checkDiv(rangeI8, setFromString("-5, 11..100", TyInteger.I8), "{-20..9}", filter)
        checkDiv(rangeI8, setFromString("89, 100..105", TyInteger.I8), "{0, 1}", filter)
        checkDiv(rangeI8, setFromString("-128, 102..105", TyInteger.I8), "{0}", filter)

        checkDiv(range(-1, 5, TyInteger.I8), setFromString("-128, -100..1", TyInteger.I8), "{-5..5}", filter)
        checkDiv(range(-35, -29, TyInteger.I8), setFromString("-128, -105..-100", TyInteger.I8), "{0}", filter)

        checkDiv(range(Long.MIN_VALUE + 20, Long.MIN_VALUE + 30, TyInteger.I128), setFromString("-33..-10, 0", TyInteger.I128), "{279496122328932599..922337203685477578}")
    }

    fun `test div set to set`() {
        checkDiv(setFromString("-5..0, 23..44"), setFromString("-5, 11..22"), "{-8..-4, 0..4}")
        checkDiv(setFromString("-5, 11..22"), setFromString("1, 11..22, 33..55"), "{-5, 0..2, 11..22}")

        val filter = { it: Long -> it in -128L..127L }
        val setI8 = setFromString("-5, 22, 30..40", TyInteger.I8)
        checkDiv(setI8, setFromString("-5, 11..100", TyInteger.I8), "{-8..-6, -4, 0..3}", filter)
        checkDiv(setI8, setFromString("3, 100..105", TyInteger.I8), "{-1, 0, 7, 10..13}", filter)
        checkDiv(setI8, setFromString("-128, 127", TyInteger.I8), "{0}", filter)

        checkDiv(range(-1, 5, TyInteger.I8), setFromString("-128, -100..1, 3, 4", TyInteger.I8), "{-5..5}", filter)
        checkDiv(setFromString("-128..-127, 5..127", TyInteger.I8), setFromString("-128..-6, 126..127", TyInteger.I8), "{-21..21}", filter)

        checkDiv(setFromString("${Long.MIN_VALUE + 20}..${Long.MIN_VALUE + 30}, 0", TyInteger.I128), setFromString("-33..-10, 0", TyInteger.I128), "{0, 279496122328932599..922337203685477578}")
    }

    private fun checkAdd(left: LongRangeSet, right: LongRangeSet, expected: String, filter: (Long) -> Boolean = { true }) {
        checkBinOp(left, right, expected, ArithmeticOp.ADD, ::checkedAddOrNull, filter)
        checkBinOp(right, left, expected, ArithmeticOp.ADD, ::checkedAddOrNull, filter)
        checkBinOp(left, right, expected, ArithmeticAssignmentOp.PLUSEQ, ::checkedAddOrNull, filter)
    }

    private fun checkMultiply(left: LongRangeSet, right: LongRangeSet, expected: String, filter: (Long) -> Boolean = { true }) {
        checkBinOp(left, right, expected, ArithmeticOp.MUL, ::checkedMultiplyOrNull, filter)
        checkBinOp(right, left, expected, ArithmeticOp.MUL, ::checkedMultiplyOrNull, filter)
        checkBinOp(left, right, expected, ArithmeticAssignmentOp.MULEQ, ::checkedMultiplyOrNull, filter)
    }

    private fun checkMod(left: LongRangeSet, right: LongRangeSet, expected: String, filter: (Long) -> Boolean = { true }) {
        checkBinOp(left, right, expected, ArithmeticOp.REM, ::checkedModOrNull, filter)
        checkBinOp(left, right, expected, ArithmeticAssignmentOp.REMEQ, ::checkedModOrNull, filter)
    }

    private fun checkDiv(left: LongRangeSet, right: LongRangeSet, expected: String, filter: (Long) -> Boolean = { true }) {
        checkBinOp(left, right, expected, ArithmeticOp.DIV, ::checkedDivOrNull, filter)
        checkBinOp(left, right, expected, ArithmeticAssignmentOp.DIVEQ, ::checkedDivOrNull, filter)
    }

    private fun checkSub(left: LongRangeSet, right: LongRangeSet, expected: String, filter: (Long) -> Boolean = { true }) {
        checkBinOp(left, right, expected, ArithmeticOp.SUB, ::checkedSubOrNull, filter)
        checkBinOp(left, right, expected, ArithmeticAssignmentOp.MINUSEQ, ::checkedSubOrNull, filter)
    }

    private fun checkSet(expected: String, actual: LongRangeSet?) = assertEquals(expected, actual.toString())

    private fun checkPredicate(collection: Collection<LongRangeSet>, predicate: (LongRangeSet) -> Boolean): Unit = collection.filterNot(predicate).let {
        if (it.isNotEmpty()) error("False predicate in ${it.joinToString(", ")}")
    }

    private fun checkType(collection: Collection<LongRangeSet>, type: KClass<out LongRangeSet>) {
        if (!collection.any { set -> set::class.isSubclassOf(type) }) error("Couldn't find ${type.simpleName}")
    }

    private fun checkHasAllTypes(collection: Collection<LongRangeSet>, ignore: Collection<KClass<out LongRangeSet>> = emptyList()) {
        listOf(Empty::class, Unknown::class, Point::class, Range::class, RangeSet::class)
            .filter { it !in ignore }
            .forEach { checkType(collection, it) }
    }

    private fun checkMethodWithBooleanResult(
        vararg pairs: Pair<Collection<LongRangeSet>, (LongRangeSet) -> Boolean>) {
        checkMethodWithBooleanResult(ignore = emptyList(), pairs = pairs.toList())
    }

    private fun checkMethodWithBooleanResult(
        ignore: Collection<KClass<out LongRangeSet>>,
        pairs: Collection<Pair<Collection<LongRangeSet>, (LongRangeSet) -> Boolean>>) {
        checkHasAllTypes(pairs.flatMap { it.first }, ignore)
        pairs.forEach {
            checkPredicate(it.first, it.second)
        }
    }

    private fun checkMethodWithBooleanResult(
        ignore: Collection<KClass<out LongRangeSet>>,
        pair: Pair<Collection<LongRangeSet>, (LongRangeSet) -> Boolean>) =
        checkMethodWithBooleanResult(ignore, listOf(pair))

    private fun checkMethodWithBooleanResult(
        ignore: KClass<out LongRangeSet>,
        pair: Pair<Collection<LongRangeSet>, (LongRangeSet) -> Boolean>) =
        checkMethodWithBooleanResult(listOf(ignore), listOf(pair))

    private fun checkBinOp(
        left: LongRangeSet,
        right: LongRangeSet,
        expected: String,
        operation: OverloadableBinaryOperator,
        operator: (Long, Long) -> Long?,
        filter: (Long) -> Boolean = { true }
    ) {
        val result = left.binOpFromToken(operation, right)
        assertEquals(expected, result.toString())
        if (left.isUnknown || right.isUnknown) return

        val errors = left.stream
            .mapToObj { a ->
                right.stream
                    .filter {
                        val res = operator(a, it)
                        res != null && filter(res) && !result.contains(res)
                    }
                    .mapToObj { b -> "$a ${operation.sign} $b = ${operator(a, b)}" }
            }
            .flatMap { it }
            .toList()
            .joinToString(separator = "\n")

        if (!errors.isEmpty()) {
            fail("Expected range $expected is not satisfied:\n$errors")
        }
    }
}

private val numberRegex: Regex = Regex("-?[\\d]+")
private fun setFromString(set: String, type: TyInteger = TyInteger.I64): LongRangeSet = when (set) {
    "" -> empty()
    "!" -> empty(true)
    "?" -> unknown()
    "z" -> Empty.DivisionByZero
    else -> {
        set.splitToSequence(',')
            .map { string ->
                val numbers = numberRegex.findAll(string).map { it.value.toLong() }.toList()
                when (numbers.size) {
                    1 -> point(numbers[0], type)
                    2 -> range(numbers[0], numbers[1], type)
                    else -> error("Invalid set '$set'")
                }
            }
            .reduce(LongRangeSet::unite)
    }
}

private fun fromType(type: Ty?): LongRangeSet = LongRangeSet.fromType(type) ?: error("Bad type '$type'")
