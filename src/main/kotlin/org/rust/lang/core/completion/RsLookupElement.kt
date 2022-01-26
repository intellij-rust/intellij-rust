/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementDecorator
import org.rust.lang.core.completion.RsLookupElementProperties.KeywordKind

class RsLookupElement(
    delegate: LookupElement,
    val props: RsLookupElementProperties,
) : LookupElementDecorator<LookupElement>(delegate) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as RsLookupElement

        if (props != other.props) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + props.hashCode()
        return result
    }
}

/**
 * Some known [lookup element][RsLookupElement] properties used in
 * [completion sorting][org.rust.lang.core.completion.sort.RS_COMPLETION_WEIGHERS]
 * and [Machine Learning-Assisted Completion sorting][org.rust.ml.RsElementFeatureProvider]
 */
data class RsLookupElementProperties(
    /**
     * `true` if the lookup element is created by [RsImplTraitMemberCompletionProvider].
     * This kind of completion suggests an entire trait member when typing in a trait impl.
     * See tests in `RsImplTraitMemberCompletionProviderTest`.
     */
    val isImplMemberFullLineCompletion: Boolean = false,

    /**
     * [KeywordKind.NOT_A_KEYWORD] if the lookup element is *not* a keyword.
     * Some another [KeywordKind] variant if the lookup element *is* a keyword.
     */
    val keywordKind: KeywordKind = KeywordKind.NOT_A_KEYWORD,

    /**
     * `false` if the lookup element is a method that has a `self` type incompatible with a receiver type,
     * for example when a `self` has `&mut T` type and a receiver has `&T` type.
     * `true` otherwise.
     *
     * ```
     * struct S;
     * impl S {
     *     fn foo(&mut self) {} // isMethodSelfTypeCompatible = false
     *     fn foo(&self) {}     // isMethodSelfTypeCompatible = true
     * }
     * fn foo(a: &S) {
     *     a. // <-- complete here
     * }
     * ```
     */
    val isSelfTypeCompatible: Boolean = false,

    /**
     * `true` if after insertion of the lookup element it will form an expression with a type
     * that conforms to the expected type of that expression.
     *
     * ```
     * fn foo() -> String { ... } // isReturnTypeConformsToExpectedType = true
     * fn bar() -> i32 { ... }    // isReturnTypeConformsToExpectedType = false
     * fn main() {
     *     let a: String = // <-- complete here
     * }
     * ```
     */
    val isReturnTypeConformsToExpectedType: Boolean = false,

    /**
     * `true` if the lookup element refers to a local declaration, e.g. inside a function body or
     * a `static` initializer.
     *
     * ```
     * fn foo() {}      // isLocal = false
     * fn main() {
     *     fn bar()     // isLocal = true
     *     let baz = 1; // isLocal = true
     *     // <-- complete here
     * }
     * ```
     */
    val isLocal: Boolean = false,

    /**
     * `true` if the lookup element is a member of an inherent impl (i.e. an `impl` without a trait)
     *
     * ```
     * struct S
     * impl S {
     *     fn foo();           // isInherentImplMember = true
     *     const BAR: i32 = 1; // isInherentImplMember = true
     * }
     * trait Trait { fn baz(); }
     * impl Trait for S {
     *     fn baz() {}         // isInherentImplMember = false
     * }
     * ```
     */
    val isInherentImplMember: Boolean = false,

    /**
     * Some classification of an element
     */
    val elementKind: ElementKind = ElementKind.DEFAULT,
) {
    enum class KeywordKind {
        // Top Priority
        PUB,
        PUB_CRATE,
        PUB_PARENS,
        LAMBDA_EXPR,
        ELSE_BRANCH,
        AWAIT,
        KEYWORD,
        NOT_A_KEYWORD,
        // Least priority
    }

    enum class ElementKind {
        // Top Priority
        DERIVE_GROUP,
        DERIVE,
        LINT,
        LINT_GROUP,

        VARIABLE,
        ENUM_VARIANT,
        FIELD_DECL,
        ASSOC_FN,
        DEFAULT,
        MACRO,
        DEPRECATED,
        // Least priority
    }
}
