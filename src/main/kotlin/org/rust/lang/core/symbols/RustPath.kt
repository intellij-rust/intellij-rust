package org.rust.lang.core.symbols

import java.io.DataInput
import java.io.DataOutput

/**
 * PSI-independent representation of Rust paths.
 */
data class RustPath(
    val head: RustPathHead,
    val segments: List<RustPathSegment>
) {
    fun join(name: String): RustPath =
        RustPath(head, segments + RustPathSegment(name))

    override fun toString(): String {
        val start = when (head) {
            is RustPathHead.Absolute -> ""
            is RustPathHead.Relative -> if (head.level == 0) SELF else SUPER + "::$SUPER".repeat(head.level - 1)
            is RustPathHead.Named -> head.segment.name
        }
        return start + segments.map { "::${it.name}" }.joinToString(separator = "")
    }

    companion object {

        fun identifier(name: String) = RustPath(RustPathHead.Named(RustPathSegment(name)), emptyList())

        val SELF = "self"
        val SUPER = "super"
        val CSELF = "Self"
    }
}

fun DataOutput.writeRustPath(value: RustPath) {
    writeRustPathHead(value.head)

    writeInt(value.segments.size)
    for (segment in value.segments) {
        writeRustPathSegment(segment)
    }
}

fun DataInput.readRustPath(): RustPath {
    val head = readRustPathHead()

    val parts = readInt()
    val segments = (0 until parts).map { readRustPathSegment() }.toList()
    return RustPath(head, segments)
}


data class RustPathSegment(
    val name: String
) {
    init {
        check(name != RustPath.SUPER)
    }
}

fun DataOutput.writeRustPathSegment(value: RustPathSegment) = writeUTF(value.name)

fun DataInput.readRustPathSegment(): RustPathSegment = RustPathSegment(readUTF())


sealed class RustPathHead {
    object Absolute : RustPathHead()

    class Relative(val level: Int) : RustPathHead() {
        init {
            check(0 <= level)
        }

        // TODO: use data class in Kotlin 1.1
        override fun equals(other: Any?): Boolean = other is Relative && level == other.level

        override fun hashCode(): Int = level.hashCode()
    }

    class Named(val segment: RustPathSegment) : RustPathHead() {
        // TODO: ditto
        override fun equals(other: Any?): Boolean = other is Named && segment == other.segment

        override fun hashCode(): Int = segment.hashCode()
    }
}

fun DataOutput.writeRustPathHead(value: RustPathHead) {
    when (value) {
        is RustPathHead.Absolute -> writeInt(-1)
        is RustPathHead.Relative -> writeInt(value.level)
        is RustPathHead.Named -> {
            writeInt(-2)
            writeRustPathSegment(value.segment)
        }
    }
}

fun DataInput.readRustPathHead(): RustPathHead {
    val tag = readInt()
    return when {
        tag == -1 -> RustPathHead.Absolute
        tag == -2 -> RustPathHead.Named(readRustPathSegment())
        tag >= 0 -> RustPathHead.Relative(tag)
        else -> error("Corrupted DataInput, bad RustPath")
    }
}
