/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.lang

// Based on https://github.com/steveklabnik/semver/blob/master/src/version.rs
sealed class Identifier : Comparable<Identifier> {
    data class Numeric(val num: Int) : Identifier() {
        override fun compareTo(other: Identifier): Int = when (other) {
            is Numeric -> this.num.compareTo(other.num)
            is AlphaNumeric -> this.num.toString().compareTo(other.string)
        }

        override fun toString() = "$num"
    }

    data class AlphaNumeric(val string: String) : Identifier() {
        init {
            require(string.isNotEmpty()) { "Identifier can't be empty" }
        }

        override fun compareTo(other: Identifier): Int = when (other) {
            is AlphaNumeric -> this.string.compareTo(other.string)
            is Numeric -> this.string.compareTo(other.num.toString())
        }

        override fun toString() = string
    }

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
}

data class PartialCrateVersion(
    val major: Int?,
    val minor: Int?,
    val patch: Int?,
    val pre: List<Identifier> = emptyList(),
    val build: List<Identifier> = emptyList()
) {
    fun tryToCrateVersion(): CrateVersion? {
        return if (major != null && minor != null && patch != null) {
            CrateVersion(major, minor, patch, pre, build)
        } else {
            null
        }
    }

    fun toCrateVersion(): CrateVersion {
        require(major != null) { "Major can't be null" }
        require(minor != null) { "Minor can't be null" }
        require(patch != null) { "Patch can't be null" }
        return CrateVersion(major, minor, patch, pre, build)
    }
}

data class CrateVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val pre: List<Identifier> = emptyList(),
    val build: List<Identifier> = emptyList()
) : Comparable<CrateVersion> {

    companion object {
        /**
         * Requires all 3 components (major, minor, patch) to be present.
         */
        fun parse(string: String): CrateVersion =
            tryParse(string).toCrateVersion()

        fun tryParse(string: String): PartialCrateVersion {
            val input = string.trim()
            val majorBound = input.indexOf('.').takeUnless { it == -1 }
                ?: return PartialCrateVersion(input.toIntOrNull(), null, null)
            val major = input.substring(0, majorBound).toIntOrNull() ?: return PartialCrateVersion(null, null, null)

            val minorBound = input.indexOf('.', majorBound + 1).takeUnless { it == -1 }
                ?: return PartialCrateVersion(major, input.substring(majorBound + 1).toIntOrNull(), null)
            val minor = input.substring(majorBound + 1, minorBound).toIntOrNull()
                ?: return PartialCrateVersion(major, null, null)

            val patchBound = input.indexOfAny(charArrayOf('-', '+'), minorBound + 1).takeUnless { it == -1 }
                ?: return PartialCrateVersion(major, minor, input.substring(minorBound + 1).toIntOrNull())
            val patch = input.substring(minorBound + 1, patchBound).toIntOrNull()
                ?: return PartialCrateVersion(major, minor, null)

            val preStartBound = if (input.getOrNull(patchBound) == '-') patchBound else -1
            val preBound = input.indexOf('+', preStartBound + 1).takeUnless { it == -1 } ?: input.length
            val pres = if (preStartBound != -1 && preStartBound != preBound) input.substring(preStartBound + 1, preBound).split('.').map { Identifier.parse(it) } else listOf()

            val builds = if (preBound + 1 < input.length) input.substring(preBound + 1).split('.').map { Identifier.parse(it) } else listOf()

            return PartialCrateVersion(major, minor, patch, pres, builds)
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


        val pres = this.pre.zipAll(other.pre)
        for ((thisPre, otherPre) in pres) {
            if (thisPre != null && otherPre != null) {
                val cmp = thisPre.compareTo(otherPre)
                if (cmp != 0) return cmp
            } else if (thisPre == null) {
                if (otherPre == null) {
                    break
                } else {
                    return 1
                }
            } else if (otherPre == null) {
                // thisPre == null at this point
                return -1
            }
        }

        val builds = this.build.zipAll(other.build)
        for ((thisBuild, otherBuild) in builds) {
            if (thisBuild != null && otherBuild != null) {
                val cmp = thisBuild.compareTo(otherBuild)
                if (cmp != 0) return cmp
            } else if (thisBuild == null) {
                if (otherBuild == null) {
                    break
                } else {
                    return 1
                }
            } else if (otherBuild == null) {
                // thisBuild == null at this point
                return -1
            }
        }

        return 0
    }

    override fun toString() =
        "$major.$minor.$patch" +
            (if (pre.isNotEmpty()) pre.joinToString(".", prefix = "-") else "") +
            (if (build.isNotEmpty()) build.joinToString(".", prefix = "+") else "")
}


// Based on https://github.com/JetBrains/kotlin/blob/master/libraries/stdlib/common/src/generated/_Collections.kt#L2219
// Zips all elements; doesn't stop when one of them is null
private inline fun <T, R, V> List<T>.zipAll(other: List<R>, transform: (a: T?, b: R?) -> V): List<V> {
    val arraySize = maxOf(this.size, other.size)
    val list = ArrayList<V>(arraySize)
    for (i in 0 until arraySize) {
        list.add(transform(this.getOrNull(i), other.getOrNull(i)))
    }
    return list
}

private fun <T, R> List<T>.zipAll(other: List<R>): List<Pair<T?, R?>> {
    return zipAll(other) { t1, t2 -> t1 to t2 }
}
