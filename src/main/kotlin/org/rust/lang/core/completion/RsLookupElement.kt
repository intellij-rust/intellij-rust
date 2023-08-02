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
     * `true` if the lookup element is created by [RsImplTraitMemberCompletionProvider] or [RsFnMainCompletionProvider].
     * This kind of completion suggests an entire item (e.g. trait member).
     */
    val isFullLineCompletion: Boolean = false,

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

    /**
     * `true` if the lookup element is a method that implements an overloaded operator like `add`,
     * `eq`, `partial_cmp`, etc.
     *
     * ```
     * use std::ops::Add;
     * use std::convert::AsRef;
     *
     * struct S(i32);
     *
     * impl Add for S {
     *     type Output = S;
     *     fn add(self, rhs: Self) -> S {       // isOperatorMethod = true
     *         S(self.0 + rhs.0)
     *     }
     * }
     * impl AsRef<i32> for S {
     *     fn as_ref(&self) -> &i32 { &self.0 } // isOperatorMethod = false
     * }
     *
     * fn main() {
     *     let a = S(0);
     *     a.  // <-- complete here
     *     S:: // <-- complete here
     * }
     * ```
     */
    val isOperatorMethod: Boolean = false,

    /**
     * `true` if the lookup element is a member of a blanket impl, i.e. an impl for a type parameter.
     *
     * ```
     * impl<T: Bound> Trait for T {
     *     fn foo(&self) {}    // isBlanketImplMember = true
     *     fn bar() {}         // isBlanketImplMember = true
     *     const BAZ: i32 = 1; // isBlanketImplMember = true
     * }
     */
    val isBlanketImplMember: Boolean = false,

    /**
     * `true` if the lookup element is a function that requires an `unsafe` block to be called.
     *
     * ```
     * unsafe fn foo() {}     // isUnsafeFn = true
     * extern "C" {
     *     fn bar();          // isUnsafeFn = true
     * }
     * fn baz() {}            // isUnsafeFn = false
     * extern "C" fn qux() {} // isUnsafeFn = false
     * ```
     */
    val isUnsafeFn: Boolean = false,

    /**
     * `true` if the lookup element is a function with `async` modifier.
     *
     * ```
     * async fn foo() {} // isAsyncFn = true
     * fn bar() {}       // isAsyncFn = false
     * ```
     */
    val isAsyncFn: Boolean = false,

    /**
     * `true` if the lookup element is a function with `const` modifier or a `const`.
     *
     * ```
     * const fn foo() {}  // isConstFnOrConst = true
     * const C: i32 = 1;  // isConstFnOrConst = true
     * fn bar() {}        // isConstFnOrConst = false
     * static S: i32 = 1; // isConstFnOrConst = false
     * ```
     */
    val isConstFnOrConst: Boolean = false,

    /**
     * `true` if the lookup element is a function with `extern` keyword.
     * Don't confuse with a function inside an `extern` block.
     *
     * ```
     * extern "C" fn foo() {} // isExternFn = true
     * fn bar() {}            // isExternFn = false
     * extern "C" {
     *     fn baz();          // isExternFn = false
     * }
     * ```
     */
    val isExternFn: Boolean = false,
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
        FROM_UNRESOLVED_IMPORT,
        // Least priority
    }
}
