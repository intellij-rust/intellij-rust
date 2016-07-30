package org.rust.lang.core.resolve.indexes

import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.AbstractStubIndex
import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexKey
import com.intellij.util.io.KeyDescriptor
import org.rust.lang.core.RustFileElementType
import org.rust.lang.core.psi.*
import org.rust.lang.core.resolve.RustResolveEngine
import org.rust.lang.core.symbols.RustQualifiedPath
import org.rust.lang.core.symbols.RustQualifiedPathPart
import org.rust.lang.core.types.unresolved.RustUnresolvedPathType
import org.rust.lang.core.types.unresolved.RustUnresolvedType
import org.rust.lang.core.types.util.resolvedType
import org.rust.lang.core.types.visitors.impl.RustEqualityUnresolvedTypeVisitor
import org.rust.lang.core.types.visitors.impl.RustHashCodeComputingUnresolvedTypeVisitor
import org.rust.utils.Either
import java.io.DataInput
import java.io.DataOutput


class RustImplIndex : AbstractStubIndex<RustImplIndex.Key, RustImplItemElement>() {

    /**
     * This wrapper is required due to a subtle bug in the [com.intellij.util.indexing.MemoryIndexStorage], involving
     * use of the object's `hashCode`, while [com.intellij.util.indexing.MapIndexStorage] being using the one
     * impose by the [KeyDescriptor]
     */
    data class Key(val type: RustUnresolvedType) {

        override fun equals(other: Any?): Boolean =
            other is Key &&
            other.type.accept(
                object : RustEqualityUnresolvedTypeVisitor(type) {
                    /**
                     * Compare hole-containing types
                     */
                    override fun visitPathType(type: RustUnresolvedPathType): Boolean = true
                }
            )

        override fun hashCode(): Int =
            type.accept(
                object: RustHashCodeComputingUnresolvedTypeVisitor() {
                    override fun visitPathType(type: RustUnresolvedPathType): Int = 0xDEADBAE
                }
            )

    }

    companion object {

        fun findImplsFor(target: RustStructOrEnumItemElement): Sequence<RustImplItemElement> =
            findImplsByRefInternal(Either.left(target))

        fun findImplsFor(target: RustTraitItemElement): Sequence<RustImplItemElement> =
            findImplsByRefInternal(Either.right(target))

        private fun findImplsByRefInternal(target: Either<RustStructOrEnumItemElement, RustTraitItemElement>): Sequence<RustImplItemElement> {
            val item = Either.apply(target) { item: RustItemElement -> item }

            // TODO(XXX): Rollback
            //val type = RustUnresolvedPathType(item.canonicalCratePath!!)
            val type = RustUnresolvedPathType(RustQualifiedPath.create(RustQualifiedPathPart.from(item.name!!)))

            return findImplsForInternal(type, item)
        }

        private fun findImplsForInternal(target: RustUnresolvedType, pivot: RustCompositeElement): Sequence<RustImplItemElement> {
            val found: MutableList<RustImplItemElement> = arrayListOf()

            val project = pivot.project

            StubIndex
                .getInstance()
                .processElements(
                    KEY,
                    Key(target),
                    project,
                    GlobalSearchScope.allScope(project),
                    RustImplItemElement::class.java,
                    {
                        found.add(it)
                        true /* continue */
                    })

            val resolved = lazy { RustResolveEngine.resolve(target, pivot) }

            return found.asSequence()
                        .filter { impl -> impl.type?.let { it.resolvedType == resolved.value } ?: false }
        }

        val KEY: StubIndexKey<Key, RustImplItemElement> =
            StubIndexKey.createIndexKey("org.rust.lang.core.stubs.index.RustImplIndex")

    }

    override fun getVersion(): Int = RustFileElementType.stubVersion

    override fun getKey(): StubIndexKey<Key, RustImplItemElement> = KEY

    override fun getKeyDescriptor(): KeyDescriptor<Key> =
        object: KeyDescriptor<Key> {

            override fun isEqual(lop: Key?, rop: Key?): Boolean =
                lop === rop || lop?.equals(rop) ?: false

            override fun getHashCode(value: Key?): Int =
                value?.hashCode() ?: -1

            override fun read(`in`: DataInput): Key? {
                return RustUnresolvedType.deserialize(`in`)?.let { Key(it) }
            }

            override fun save(out: DataOutput, value: Key?) {
                RustUnresolvedType.serialize(value?.type, out)
            }
        }
}


class RustAliasIndex : StringStubIndexExtension<RustUseItemElement>() {

    companion object {

        val KEY: StubIndexKey<String, RustUseItemElement> =
            StubIndexKey.createIndexKey("org.rust.lang.core.stubs.index.RustAliasIndex")

    }

    override fun getVersion(): Int = RustFileElementType.stubVersion

    override fun getKey(): StubIndexKey<String, RustUseItemElement> = KEY

}
