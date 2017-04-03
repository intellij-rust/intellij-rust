package org.rust.lang.core.symbols

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

    companion object {
        val SELF = "self"
        val SUPER = "super"
    }
}


data class RustPathSegment(
    val name: String
) {
    init {
        check(name != RustPath.SUPER)
    }

    companion object {
        fun withoutGenerics(name: String) = RustPathSegment(name)
    }
}
