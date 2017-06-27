/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import junit.framework.TestCase

/**
 * Base class for naming notations tests.
 */
abstract class NamingNotationTest : TestCase() {
    protected abstract val inspection: RsNamingInspection

    /**
     * Checks that the given name is acceptable in the notation.
     */
    protected fun testOk(name: String) {
        testResult(name, true, null)
    }

    /**
     * Checks that the given name is not acceptable in the notation and must be suggested
     * to be changed to `expSuggestion`.
     */
    protected fun testSuggestion(name: String, expSuggestion: String) {
        testResult(name, false, expSuggestion)
    }

    private fun testResult(name: String, expOk: Boolean, expSuggestion: String?) {
        val (isOk, suggestion) = inspection.checkName(name)
        assertEquals("Name $name acceptance: expected $expOk, but was $isOk", expOk, isOk)
        assertEquals("Suggestion for name $name: expected $expSuggestion, but was $suggestion", expSuggestion, suggestion)
    }
}

/**
 * CamelCase notation tests.
 */
class CamelCaseNotationTest : NamingNotationTest() {
    override val inspection = RsCamelCaseNamingInspection("Camel")

    fun testAcceptable() {
        testOk("_0")
        testOk("_1234")
        testOk("A")
        testOk("ABC")
        testOk("Camel")
        testOk("Camel2Qamel")
        testOk("CamelCaseName")
        testOk("__CamelCaseName")
        testOk("CamelCaseName__")
        testOk("__Cam1elCa2seN3ame__")
    }

    fun testDefaultSuggestion() {
        testSuggestion("__", "CamelCase")
        testSuggestion("____", "CamelCase")
    }

    fun testSuggestions() {
        testSuggestion("foo", "Foo")
        testSuggestion("fOO", "Foo")
        testSuggestion("foo_bar", "FooBar")
        testSuggestion("FOO_BAR", "FooBar")
        testSuggestion("fooBar", "FooBar")
        testSuggestion("FOo_BAr", "FooBar")
        testSuggestion("FooBar__baz_", "FooBarBaz")
        testSuggestion("Foo_1", "Foo1")
        testSuggestion("___FOo___barBaz__", "FooBarBaz")
        testSuggestion("a1234", "A1234")
    }
}

/**
 * snake_case notation tests.
 */
class SnakeCaseNotationTest : NamingNotationTest() {
    override val inspection = RsSnakeCaseNamingInspection("Snake")

    fun testAcceptable() {
        testOk("_0")
        testOk("_1234")
        testOk("a")
        testOk("abc")
        testOk("snake")
        testOk("snake2cake")
        testOk("snake_case_name")
        testOk("__snake_case_name")
        testOk("snake_case_name__")
        testOk("___snake___ca3e__2__name__")
        testOk("'a")
        testOk("'__")
        testOk("'lifetime")
        testOk("'static_lifetime")
    }

    fun testDefaultSuggestion() {
        testSuggestion("__", "snake_case")
        testSuggestion("____", "snake_case")
    }

    fun testSuggestions() {
        testSuggestion("Snake", "snake")
        testSuggestion("FooBar", "foo_bar")
        testSuggestion("fooBar", "foo_bar")
        testSuggestion("FOoBAr", "foo_bar")
        testSuggestion("FOO_BAR", "foo_bar")
        testSuggestion("___FooBar__", "___foo_bar")
        testSuggestion("fooBar__Baz__", "foo_bar_baz")
        testSuggestion("Foo1", "foo1")
        testSuggestion("Foo_1", "foo_1")
        testSuggestion("fooA", "foo_a")
        testSuggestion("'StaticLifetime", "'static_lifetime")
        testSuggestion("'___StaticLifetime__2", "'___static_lifetime_2")
    }
}

/**
 * UPPER_CASE notation tests.
 */
class UpperCaseNotationTest : NamingNotationTest() {
    override val inspection = RsUpperCaseNamingInspection("Upper")

    fun testAcceptable() {
        testOk("_0")
        testOk("_1234")
        testOk("A")
        testOk("ABC")
        testOk("UPPER")
        testOk("UPPER2SUPPER")
        testOk("UPPER_CASE_NAME")
        testOk("__UPPER_CASE_NAME")
        testOk("UPPER_CASE_NAME__")
        testOk("___UPPER___CA3E__2__NAME__")
    }

    fun testDefaultSuggestion() {
        testSuggestion("__", "UPPER_CASE")
        testSuggestion("____", "UPPER_CASE")
    }

    fun testSuggestions() {
        testSuggestion("Upper", "UPPER")
        testSuggestion("FooBar", "FOO_BAR")
        testSuggestion("fooBar", "FOO_BAR")
        testSuggestion("FOoBAr", "FOO_BAR")
        testSuggestion("foo_bar", "FOO_BAR")
        testSuggestion("___FooBar__", "___FOO_BAR")
        testSuggestion("fooBar__Baz__", "FOO_BAR_BAZ")
        testSuggestion("Foo1", "FOO1")
        testSuggestion("Foo_1", "FOO_1")
        testSuggestion("fooA", "FOO_A")
    }
}
