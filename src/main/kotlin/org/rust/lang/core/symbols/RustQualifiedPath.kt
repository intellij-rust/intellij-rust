package org.rust.lang.core.symbols

import org.rust.lang.core.psi.RelativeModulePrefix
import org.rust.lang.core.symbols.impl.RustNamedQualifiedPathPart
import org.rust.lang.core.symbols.impl.RustSelfQualifiedPathPart
import org.rust.lang.core.symbols.impl.RustSuperQualifiedPathPart
import java.io.DataInput
import java.io.DataOutput


/**
 * Abstract qualified-name representation serving purposes of
 * unifying PSI interface with PSI-independent IR
 *
 * Serves primarily as an URI for items inside the Rust's crates
 *
 * @name        Non-qualified path-part
 * @qualifier   Qualified path-part
 */
interface RustQualifiedPath {

    companion object {
        fun create(part: RustQualifiedPathPart, qualifier: RustQualifiedPath? = null, fullyQualified: Boolean = false): RustQualifiedPath =
            org.rust.lang.core.symbols.impl.RustQualifiedPath(qualifier, part, qualifier == null && fullyQualified)

        fun serialize(ref: RustQualifiedPath?, output: DataOutput) {
            if (ref != null) {
                val unfolded = ref.unfold().toList()

                output.writeInt(unfolded.size)
                output.writeBoolean(ref.fullyQualified)

                unfolded.forEach {
                    output.writeUTF(it.name)
                }
            } else {
                output.writeInt(-1)
            }
        }

        fun deserialize(input: DataInput): RustQualifiedPath? {
            val parts = input.readInt()
            if (parts < 0)
                return null

            val fullyQualified = input.readBoolean()

            return (0 until parts)
                        .fold(null as RustQualifiedPath?, {
                            qual, i ->
                            input.readUTF().let {
                                RustQualifiedPath.create(
                                    RustQualifiedPathPart.from(it),
                                    qualifier = qual,
                                    fullyQualified = fullyQualified
                                )
                            }
                        })
        }

    }

    val relativeModulePrefix: RelativeModulePrefix
        get() = seekRelativeModulePrefixInternal(false)

    fun seekRelativeModulePrefixInternal(hasSuffix: Boolean): RelativeModulePrefix {
        val qual = qualifier
        val isSelf  = part === RustSelfQualifiedPathPart
        val isSuper = part === RustSuperQualifiedPathPart

        check(!isSelf || !isSuper)

        if (qual != null) {
            if (isSelf) return RelativeModulePrefix.Invalid

            val parent = qual.seekRelativeModulePrefixInternal(hasSuffix = true)
            return when (parent) {
                is RelativeModulePrefix.Invalid        -> RelativeModulePrefix.Invalid
                is RelativeModulePrefix.NotRelative    -> when {
                    isSuper -> RelativeModulePrefix.Invalid
                    else    -> RelativeModulePrefix.NotRelative
                }
                is RelativeModulePrefix.AncestorModule -> when {
                    isSuper -> RelativeModulePrefix.AncestorModule(parent.level + 1)
                    else    -> RelativeModulePrefix.NotRelative
                }
            }
        }
        else if (fullyQualified) {
            return if (isSelf || isSuper)
                RelativeModulePrefix.Invalid
            else
                RelativeModulePrefix.NotRelative
        }
        else {
            return when {
                // `self` by itself is not a module prefix, it's an identifier.
                // So for `self` we need to check that it's not the only segment of path.
                isSelf && hasSuffix -> RelativeModulePrefix.AncestorModule(0)
                isSuper -> RelativeModulePrefix.AncestorModule(1)
                else -> RelativeModulePrefix.NotRelative
            }
        }
    }

    val qualifier: RustQualifiedPath?
    val part: RustQualifiedPathPart

    /**
     *  Returns `true` if this is a path starting at the crate root.
     *
     *  That is, if this path starts with `::` or if this path is from a use item
     *
     *  Example:
     *
     *    ```Rust
     *    use ::foo::bar;   // relative to root
     *    use foo::bar;     // relative to root, the same as the above
     *
     *    fn main() {
     *        ::foo::bar;   // relative to root
     *        foo::bar;     // relative to current module
     *    }
     *    ```
     *
     *  Reference:
     *    https://doc.rust-lang.org/reference.html#paths
     *    https://doc.rust-lang.org/reference.html#use-declarations
     */
    val fullyQualified: Boolean

    override fun equals(other: Any?): Boolean

    override fun hashCode(): Int
}

fun RustQualifiedPath.unfold(): Sequence<RustQualifiedPathPart> =
    (qualifier?.unfold() ?: emptySequence()) + sequenceOf(part)
