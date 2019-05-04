/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.lang

// Based on https://github.com/steveklabnik/semver/blob/master/src/version_req.rs
class CrateVersionReq private constructor(private val predicates: List<Predicate>) {
    companion object {
        fun any(): CrateVersionReq =
            CrateVersionReq(emptyList())

        fun exact(version: CrateVersion) =
            CrateVersionReq(listOf(Predicate.exact(version)))

        fun parse(input: String): CrateVersionReq {
            val requirements = input.split(",")
            val predicates = mutableListOf<Predicate>()
            for (requirement in requirements) {
                val req = requirement.trim()
                val (op, len) = when {
                    req.startsWith(">=") -> Op.GtEq to 2
                    req.startsWith(">") -> Op.Gt to 1
                    req.startsWith("<=") -> Op.LtEq to 2
                    req.startsWith("<") -> Op.Lt to 1
                    req.startsWith("=") -> Op.Eq to 1
                    req.startsWith("~") -> Op.Tilde to 1
                    req.startsWith("^") -> Op.Compatible to 1
                    req.contains('*') -> {
                        val starPos = req.indexOf('*')
                        val dotCount = req.dropLast(req.length - starPos).count { it == '.' }
                        when (dotCount) {
                            0 -> Op.WildcardMajor
                            1 -> Op.WildcardMinor
                            2 -> Op.WildcardPatch
                            else -> throw IllegalArgumentException("Couldn't parse wildcard. Unrecognizable position")
                        } to 0
                    }
                    else -> null to 0
                }

                val (major, minor, patch, pre, _) = IncompleteCrateVersion.parse(req.substring(len))
                predicates += Predicate(op ?: Op.Compatible, major, minor, patch, pre)
            }

            return CrateVersionReq(predicates)
        }
    }

    /**
     * Is this a simple requirement that specifies a single version, such as `1.0.0` or `1.3.2`
     */
    val isSimple: Boolean = this.predicates.size == 1 &&
        (this.predicates[0].op == Op.Eq ||
            this.predicates[0].op == Op.Compatible)

    fun matches(version: CrateVersion): Boolean =
        this.predicates.isEmpty() ||
            (this.predicates.all { it.matches(version) } &&
                this.predicates.any { it.preTagIsCompatible(version) })

    override fun toString(): String =
        if (predicates.isEmpty()) "any"
        else predicates.joinToString()
}

private enum class Op(val literal: String) {
    Eq("="),
    Gt(">"),
    GtEq(">="),
    Lt("<"),
    LtEq("<="),
    Tilde("~"),
    Compatible("^"),
    WildcardMajor("*"),
    WildcardMinor(""),
    WildcardPatch("")
}

private data class Predicate(val op: Op, private val major: Int?, private val minor: Int?, private val patch: Int?, private val pre: List<Identifier>) {
    companion object {
        fun exact(version: CrateVersion) = Predicate(
            Op.Eq,
            version.major,
            version.minor,
            version.patch,
            ArrayList(version.pre)
        )
    }

    fun matches(version: CrateVersion): Boolean = when (this.op) {
        Op.Eq -> this.isExact(version)
        Op.Gt -> this.isGreater(version)
        Op.GtEq -> this.isGreater(version) || this.isExact(version)
        Op.Lt -> !this.isExact(version) && !this.isGreater(version)
        Op.LtEq -> !this.isGreater(version)
        Op.Tilde -> this.matchesTilde(version)
        Op.Compatible -> this.isCompatible(version)
        Op.WildcardMajor, Op.WildcardMinor, Op.WildcardPatch -> this.matchesWildcard(version)
    }

    private fun isExact(version: CrateVersion): Boolean {
        if (this.major != version.major) return false
        if (this.minor != null) {
            if (this.minor != version.minor) {
                return false
            }
        } else {
            return true
        }

        if (this.patch != null) {
            if (this.patch != version.patch) {
                return false
            }
        } else {
            return true
        }

        if (this.pre != version.pre) return false

        return true
    }

    fun preTagIsCompatible(version: CrateVersion): Boolean =
        !version.isPrerelease()
            || (this.major == version.major
            && this.minor == version.minor
            && this.patch == version.patch
            && this.pre.isNotEmpty())

    private fun isGreater(version: CrateVersion): Boolean {
        if (this.major == null) return true
        if (this.major != version.major) return version.major > this.major
        if (this.minor != null) {
            if (this.minor != version.minor) {
                return version.minor > this.minor
            }
        } else {
            return false
        }

        if (this.patch != null) {
            if (this.patch != version.patch) {
                return version.patch > this.patch
            }
        }

        if (this.pre.isNotEmpty()) {
            return version.pre.isEmpty() || version.pre.zip(this.pre).all { (a, b) -> a > b }
        }

        return false
    }

    private fun matchesTilde(version: CrateVersion): Boolean {
        this.minor ?: return this.major == version.major

        return if (this.patch != null) {
            this.major == version.major &&
                this.minor == version.minor &&
                (version.patch > patch || (version.patch == patch && this.preIsCompatible(version)))
        } else {
            this.major == version.major && this.minor == version.minor
        }
    }

    private fun isCompatible(version: CrateVersion): Boolean {
        if (version.major != this.major) return this.major == null

        val minor = this.minor ?: return version.major == this.major

        if (this.patch != null) {
            return if (this.major == 0) {
                if (minor == 0) {
                    version.minor == minor && version.patch == patch && this.preIsCompatible(version)
                } else {
                    version.minor == minor && (version.patch > patch || (version.patch == patch && this.preIsCompatible(version)))
                }
            } else {
                version.minor > minor || (version.minor == minor && (version.patch > patch || (version.patch == patch && this.preIsCompatible(version))))
            }
        } else {
            return if (this.major == 0) {
                // When in pre 1.0 stage, the minor version acts as major
                this.minor == version.minor
            } else {
                version.minor >= minor
            }
        }

    }

    private fun preIsCompatible(version: CrateVersion) =
        version.pre.isEmpty() || version.pre.zip(this.pre).all { (a, b) -> a >= b }

    private fun matchesWildcard(version: CrateVersion) = when (op) {
        Op.WildcardMajor -> true
        Op.WildcardMinor -> this.major == version.major
        Op.WildcardPatch -> this.minor == version.minor
        else -> throw IllegalStateException("Tried to check if matches wildcard but there's no wildcard")
    }

    override fun toString() =
        "CrateVersionReq(" + when (op) {
            Op.WildcardMajor -> "*"
            Op.WildcardMinor -> "$major.*"
            Op.WildcardPatch -> "$major.$minor.*"
            else -> op.literal +
                "$major" +
                (if (minor != null) ".$minor" else "") +
                (if (patch != null) ".$patch" else "") +
                (if (pre.isNotEmpty()) "-" + pre.joinToString() else "")
        } + ")"
}
