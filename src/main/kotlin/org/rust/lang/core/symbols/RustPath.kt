package org.rust.lang.core.symbols

import org.rust.utils.RustDataExternalizer
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

    companion object : RustDataExternalizer<RustPath> {
        override fun save(output: DataOutput, value: RustPath) {
            RustPathHead.save(output, value.head)

            output.writeInt(value.segments.size)
            for (segment in value.segments) {
                RustPathSegment.save(output, segment)
            }
        }

        override fun read(input: DataInput): RustPath {
            val head = RustPathHead.read(input)

            val parts = input.readInt()
            val segments = (0 until parts).map { RustPathSegment.read(input) }.toList()
            return RustPath(head, segments)
        }

        fun identifier(name: String) = RustPath(RustPathHead.Named(RustPathSegment(name)), emptyList())

        val SELF = "self"
        val SUPER = "super"
        val CSELF = "Self"
    }
}

data class RustPathSegment(
    val name: String
) {
    init {
        check(name != RustPath.SUPER)
    }

    companion object : RustDataExternalizer<RustPathSegment> {
        override fun save(output: DataOutput, value: RustPathSegment) {
            output.writeUTF(value.name)
        }

        override fun read(input: DataInput): RustPathSegment {
            return RustPathSegment(input.readUTF())
        }
    }
}

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

    companion object : RustDataExternalizer<RustPathHead> {
        override fun save(output: DataOutput, value: RustPathHead) {
            when (value) {
                is RustPathHead.Absolute -> output.writeInt(-1)
                is RustPathHead.Relative -> output.writeInt(value.level)
                is RustPathHead.Named -> {
                    output.writeInt(-2)
                    RustPathSegment.save(output, value.segment)
                }
            }
        }

        override fun read(input: DataInput): RustPathHead {
            val tag = input.readInt()
            return when {
                tag == -1 -> RustPathHead.Absolute
                tag == -2 -> RustPathHead.Named(RustPathSegment.read(input))
                tag >= 0 -> RustPathHead.Relative(tag)
                else -> error("Corrupted DataInput, bad RustPath")
            }
        }
    }
}
