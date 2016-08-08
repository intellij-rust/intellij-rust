package org.rust.lang.core.resolve.indexes

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.AbstractStubIndex
import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexKey
import com.intellij.util.io.KeyDescriptor
import org.rust.lang.core.RustFileElementType
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.util.trait
import org.rust.lang.core.types.*
import org.rust.lang.core.types.unresolved.RustUnresolvedPathType
import org.rust.lang.core.types.unresolved.RustUnresolvedType
import org.rust.lang.core.types.util.bounds
import org.rust.lang.core.types.util.decay
import org.rust.lang.core.types.util.resolvedType
import org.rust.lang.core.types.visitors.impl.RustEqualityUnresolvedTypeVisitor
import org.rust.lang.core.types.visitors.impl.RustHashCodeComputingUnresolvedTypeVisitor
import java.io.DataInput
import java.io.DataOutput


object RustImplIndex  {

    fun findNonStaticMethodsFor(target: RustType, project: Project): Sequence<RustFnElement> =
        findMethodsFor(target, project)
            .filter { !it.isStatic }

    fun findStaticMethodsFor(target: RustType, project: Project): Sequence<RustFnElement> =
        findMethodsFor(target, project)
            .filter { it.isStatic }

    fun findMethodsFor(target: RustType, project: Project): Sequence<RustFnElement> =
        findImplsFor(target, project)
            .flatMap { it.implBody?.implMethodMemberList.orEmpty().asSequence() }

    fun findImplsFor(target: RustType, project: Project): Sequence<RustImplItemElement> {
        var inherentImpls = emptySequence<RustImplItemElement>()
        if (target is RustStructOrEnumTypeBase)
            inherentImpls = findInherentImplsForInternal(target.item)

        return findNonInherentImplsForInternal(target.decay, project)
                    .filter {
                        impl -> impl.type?.let { it.resolvedType == target } ?: false
                    } + inherentImpls
    }

    private fun findInherentImplsForInternal(target: RustStructOrEnumItemElement): Sequence<RustImplItemElement> {
        val found = arrayListOf<RustImplItemElement>()

        val aliases = arrayListOf(target.name!!)

        StubIndex
            .getInstance()
            .processElements(
                RustAliasIndex.KEY,
                target.name!!,
                target.project,
                GlobalSearchScope.allScope(target.project),
                RustUseItemElement::class.java,
                {
                    it.name?.let {
                        aliases.add(it)
                    }

                    true /* continue */
                })


        aliases.forEach { alias ->
            StubIndex
                .getInstance()
                .processElements(
                    ByName.KEY,
                    alias,
                    target.project,
                    GlobalSearchScope.allScope(target.project),
                    RustImplItemElement::class.java,
                    {
                        found.add(it)
                        true /* continue */
                    })
        }

        return found.asSequence()
                    .filter { impl ->
                        impl.type?.let {
                            it.resolvedType.let { ty ->
                                ty is RustStructOrEnumTypeBase &&
                                ty.item == target
                            }
                        } ?: false
                    }
    }

    private fun findNonInherentImplsForInternal(target: RustUnresolvedType, project: Project): Sequence<RustImplItemElement> {
        if (target is RustUnknownType)
            return emptySequence()

        val found = arrayListOf<RustImplItemElement>()

        StubIndex
            .getInstance()
            .processElements(
                ByType.KEY,
                ByType.Key(target),
                project,
                GlobalSearchScope.allScope(project),
                RustImplItemElement::class.java,
                {
                    found.add(it)
                    true /* continue */
                })

        return found.asSequence()

    }


    class ByType : AbstractStubIndex<ByType.Key, RustImplItemElement>() {
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
                            override fun visitPathType(type: RustUnresolvedPathType): Boolean {
                                val lop = lop
                                return lop is RustUnresolvedPathType && lop.path.part == type.path.part
                            }
                        }
                    ) && (hashCode() == other.hashCode() || throw Exception("WTF"))

            override fun hashCode(): Int =
                type.accept(
                    object : RustHashCodeComputingUnresolvedTypeVisitor() {
                        override fun visitPathType(type: RustUnresolvedPathType): Int = type.path.part.hashCode()
                    }
                )

        }

        companion object {

            val KEY: StubIndexKey<Key, RustImplItemElement> =
                StubIndexKey.createIndexKey("org.rust.lang.core.stubs.index.RustImplIndex.ByType")

        }

        override fun getVersion(): Int = RustFileElementType.stubVersion

        override fun getKey(): StubIndexKey<Key, RustImplItemElement> = KEY

        override fun getKeyDescriptor(): KeyDescriptor<Key> =
            object : KeyDescriptor<Key> {

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


    class ByName : StringStubIndexExtension<RustImplItemElement>() {

        companion object {
            val KEY: StubIndexKey<String, RustImplItemElement> =
                StubIndexKey.createIndexKey("org.rust.lang.core.stubs.index.RustImplIndex.ByName")
        }

        override fun getKey(): StubIndexKey<String, RustImplItemElement> = KEY

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
