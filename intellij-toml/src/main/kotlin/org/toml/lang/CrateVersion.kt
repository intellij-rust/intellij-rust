/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.lang

// Based on https://github.com/steveklabnik/semver/blob/master/src/version.rs
sealed class Identifier : Comparable<Identifier> {
    companion object {
        fun parse(input: String): Identifier {
            // Strings such as 0851523 should be parsed as AlphaNumeric because Numeric would lose the 0 in front
            val int = if (input.startsWith("0")) null else input.toIntOrNull()
            return if (int != null) {
                Numeric(int)
            } else {
                AlphaNumeric(input)
            }
        }
    }

    data class Numeric(val num: Int) : Identifier() {
        override fun compareTo(other: Identifier): Int {
            if (other is Numeric) {
                return this.num.compareTo(other.num)
            }

            return 0
        }

        override fun toString() = "$num"
    }

    data class AlphaNumeric(val string: String) : Identifier() {
        init {
            require(string.isNotEmpty()) { "Identifier can't be empty" }
        }

        override fun compareTo(other: Identifier): Int {
            if (other is AlphaNumeric) {
                return this.string.compareTo(other.string)
            }

            return 0
        }

        override fun toString() = string
    }
}

data class IncompleteCrateVersion(val major: Int?, val minor: Int?, val patch: Int?, val pre: List<Identifier> = emptyList(), val build: List<Identifier> = emptyList()) {
    companion object {
        fun parse(string: String): IncompleteCrateVersion {
            val input = string.trim()
            val majorBound = input.indexOf('.').takeUnless { it == -1 }
                ?: return IncompleteCrateVersion(input.toIntOrNull(), null, null)
            val major = input.substring(0, majorBound).toIntOrNull() ?: return IncompleteCrateVersion(null, null, null)

            val minorBound = input.indexOf('.', majorBound + 1).takeUnless { it == -1 }
                ?: return IncompleteCrateVersion(major, input.substring(majorBound + 1).toIntOrNull(), null)
            val minor = input.substring(majorBound + 1, minorBound).toIntOrNull()
                ?: return IncompleteCrateVersion(major, null, null)

            val patchBound = input.indexOfAny(charArrayOf('-', '+'), minorBound + 1).takeUnless { it == -1 }
                ?: return IncompleteCrateVersion(major, minor, input.substring(minorBound + 1).toIntOrNull())
            val patch = input.substring(minorBound + 1, patchBound).toIntOrNull()
                ?: return IncompleteCrateVersion(major, minor, null)

            val preStartBound = if (input.getOrNull(patchBound) == '-') patchBound else -1
            val preBound = input.indexOf('+', preStartBound + 1).takeUnless { it == -1 } ?: input.length
            val pres = if (preStartBound != -1 && preStartBound != preBound) input.substring(preStartBound + 1, preBound).split('.').map { Identifier.parse(it) } else listOf()

            val builds = if (preBound + 1 < input.length) input.substring(preBound + 1).split('.').map { Identifier.parse(it) } else listOf()

            return IncompleteCrateVersion(major, minor, patch, pres, builds)
        }
    }
}

data class CrateVersion(val major: Int, val minor: Int, val patch: Int, val pre: List<Identifier> = emptyList(), val build: List<Identifier> = emptyList()) : Comparable<CrateVersion> {
    companion object {
        /**
         * Requires all 3 components (major, minor, patch) to be present.
         */
        fun parse(string: String): CrateVersion {
            val input = string.trim()
            val majorBound = input.indexOf('.').takeUnless { it == -1 }
                ?: throw IllegalArgumentException("Major not provided")
            val major = input.substring(0, majorBound).toInt()

            val minorBound = input.indexOf('.', majorBound + 1).takeUnless { it == -1 }
                ?: throw IllegalArgumentException("Minor not provided")
            val minor = input.substring(majorBound + 1, minorBound).toInt()

            val patchBound = input.indexOfAny(charArrayOf('-', '+'), minorBound + 1).takeUnless { it == -1 }
                ?: input.length
            val patch = input.substring(minorBound + 1, patchBound).toInt()

            val preStartBound = if (input.getOrNull(patchBound) == '-') patchBound else -1
            val preBound = input.indexOf('+', preStartBound + 1).takeUnless { it == -1 } ?: input.length
            val pres = if (preStartBound != -1 && preStartBound != preBound) input.substring(preStartBound + 1, preBound).split('.').map { Identifier.parse(it) } else listOf()

            val builds = if (preBound + 1 < input.length) input.substring(preBound + 1).split('.').map { Identifier.parse(it) } else listOf()

            return CrateVersion(major, minor, patch, pres, builds)
        }

        fun tryParse(string: String): CrateVersion? = try {
            parse(string)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    fun isPrerelease() = pre.isNotEmpty()

    override fun compareTo(other: CrateVersion): Int {
        val majorCmp = this.major.compareTo(other.major)
        if (majorCmp != 0) return majorCmp

        val minorCmp = this.minor.compareTo(other.minor)
        if (minorCmp != 0) return minorCmp

        val patchCmp = this.patch.compareTo(other.patch)
        if (patchCmp != 0) return patchCmp

        val preCmp = this.pre.withIndex().firstOrNull { (i, own) -> other.pre.getOrNull(i)?.compareTo(own) != 0 }?.let {
            val (preI, preOwn) = it
            // 1. times(-1) because this expr compares the other way (other > this ? instead of this > other ?)
            // 2. if other.pre[preI] == null, it means that this.pre.size > other.pre.size which means this > other
            other.pre.getOrNull(preI)?.compareTo(preOwn)?.times(-1) ?: -1
        } ?: if (this.pre.isEmpty()) {
            if (other.pre.isEmpty()) 0
            else 1
        } else {
            if (other.pre.isEmpty()) -1
            else this.pre.size.compareTo(other.pre.size)
        }
        if (preCmp != 0) return preCmp


        val buildCmp = this.build.withIndex().firstOrNull { (i, own) -> other.build.getOrNull(i)?.compareTo(own) != 0 }?.let {
            val (buildI, buildOwn) = it
            // 1. times(-1) because this expr compares the other way (other > this ? instead of this > other ?)
            // 2. if other.build[buildI] == null, it means that this.build.size > other.build.size which means this > other
            other.build.getOrNull(buildI)?.compareTo(buildOwn)?.times(-1) ?: -1
        } ?: if (this.build.isEmpty()) {
            if (other.build.isEmpty()) 0
            else 1
        } else {
            if (other.build.isEmpty()) -1
            else this.build.size.compareTo(other.build.size)
        }
        return buildCmp
    }

    override fun toString() =
        "$major.$minor.$patch" +
            (if (pre.isNotEmpty()) pre.joinToString(".", prefix = "-") else "") +
            (if (build.isNotEmpty()) build.joinToString(".", prefix = "+") else "")
}
