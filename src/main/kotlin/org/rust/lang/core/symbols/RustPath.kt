package org.rust.lang.core.symbols

import org.rust.lang.core.types.unresolved.RustUnresolvedType
import org.rust.lang.core.types.unresolved.readRustUnresolvedType
import org.rust.lang.core.types.unresolved.writeRustUnresolvedType
import org.rust.utils.readList
import org.rust.utils.writeList
import java.io.DataInput
import java.io.DataOutput

sealed class RustPath(val segments: List<RustPathSegment>) {
    abstract fun join(segment: RustPathSegment): RustPath

    abstract protected fun headToString(): String
    override fun toString(): String = headToString() + segments.map { "::${it.name}" }.joinToString(separator = "")

    class CrateRelative(segments: List<RustPathSegment>) : RustPath(segments) {
        override fun headToString(): String = ""

        override fun join(segment: RustPathSegment): CrateRelative = CrateRelative(segments + segment)

        override fun equals(other: Any?): Boolean =
            other is CrateRelative && segments == other.segments

        override fun hashCode(): Int = segments.hashCode()
    }

    class ModRelative(val level: Int, segments: List<RustPathSegment>) : RustPath(segments) {
        init {
            check(level >= 0)
        }

        override fun headToString(): String = if (level == 0) RustPath.SELF else RustPath.SUPER + "::${RustPath.SUPER}".repeat(level - 1)

        override fun join(segment: RustPathSegment): ModRelative = ModRelative(level, segments + segment)

        override fun equals(other: Any?): Boolean =
            other is ModRelative && level == other.level && segments == other.segments

        override fun hashCode(): Int = 31 * level + segments.hashCode()
    }

    class Named(val head: RustPathSegment, segments: List<RustPathSegment> = emptyList()) : RustPath(segments) {
        override fun headToString(): String = head.name

        override fun join(segment: RustPathSegment): Named = Named(head, segments + segment)

        override fun equals(other: Any?): Boolean =
            other is Named && head == other.head && segments == other.segments

        override fun hashCode(): Int = 31 * head.hashCode() + segments.hashCode()
    }

    companion object {
        val SELF = "self"
        val SUPER = "super"
        val CSELF = "Self"
    }
}

fun DataOutput.writeRustPath(value: RustPath) {
    when (value) {
        is RustPath.CrateRelative -> writeInt(-1)
        is RustPath.ModRelative -> writeInt(value.level)
        is RustPath.Named -> {
            writeInt(-2)
            writeRustPathSegment(value.head)
        }
    }

    writeInt(value.segments.size)
    for (segment in value.segments) {
        writeRustPathSegment(segment)
    }
}

fun DataInput.readRustPath(): RustPath {
    fun readSegments(): List<RustPathSegment> {
        val parts = readInt()
        return (0 until parts).map { readRustPathSegment() }
    }

    val tag = readInt()
    return when {
        tag == -1 -> RustPath.CrateRelative(readSegments())
        tag == -2 -> RustPath.Named(readRustPathSegment(), readSegments())
        tag >= 0 -> RustPath.ModRelative(tag, readSegments())
        else -> error("Corrupted DataInput, bad RustPath")
    }
}


data class RustPathSegment(
    val name: String,
    val typeArguments: List<RustUnresolvedType>
) {
    init {
        check(name != RustPath.SUPER)
    }

    companion object {
        fun withoutGenerics(name: String) = RustPathSegment(name, emptyList())
    }
}

fun DataOutput.writeRustPathSegment(value: RustPathSegment) {
    writeUTF(value.name)
    writeList(value.typeArguments) { writeRustUnresolvedType(it) }
}

fun DataInput.readRustPathSegment(): RustPathSegment {
    val name = readUTF()
    val genericArguments = readList { readRustUnresolvedType() }
    return RustPathSegment(name, genericArguments)
}
