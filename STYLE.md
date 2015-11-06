---
title: Coding Conventions
---

# Coding Conventions

This page contains the current coding style for the Kotlin language.

## Naming Style
If in doubt default to the Java Coding Conventions such as:

* use of camelCase for names (and avoid underscore in names)
* types start with upper case
* methods and properties start with lower case
* use 4 space indentation
* public functions should have documentation such that it appears in Kotlin Doc

## Colon

There is a space before colon where colon separates type and supertype and there's no space where colon separates instance and type:

``` kotlin
interface Foo<out T : Any> : Bar {
    fun foo(a: Int): T
}
```

Though if your type extends not just single supertype, but many of them place them one on top the other in an aligned fashion:

``` kotlin
interface Foo<out T : Any>  : Bar
                            , Baz {
  /* ... */
}
```

Applying spacing accordingly therefore `:` is aligned with a `,`.

## When

Align case-branches in columns:

``` kotlin
        when (element) {
            is RustNamedElement -> 1
            else                -> 0
        }
```

## Lambdas

In lambda expressions, spaces should be used around the curly braces, as well as around the arrow which separates the parameters
from the body. Whenever possible, a lambda should be passed outside of parentheses.

``` kotlin
list.filter { it > 10 }.map { element -> element * 2 }
```

In lambdas which are short and not nested, it's recommended to use the `it` convention instead of declaring the parameter
explicitly. In nested lambdas with parameters, parameters should be always declared explicitly.

## Unit

If a function returns Unit, the return type should be omitted:

``` kotlin
fun foo() { // ": Unit" is omitted here

}
```


# Commit Conventions

## Content

Try to maintain granularity of your commits to facilitate the review process. 

## Message

Commit message is a primary mean of providing brief excerpt of piece of work you're (no doubt, particularly dedicated) contributing to the project.
Therefore, please, be concise but reasonable. Do not abuse commit's message with: `++`, `minor`, `fix`, etc. as those would be relentlessly rejected as completely useless,
 failing to shed the light on the brilliant piece of code you're trying to contribute.
  
  We're not enforcing the format we're particularly sticked too, but hopefully ask you to not abuse it. At least too much.
   
   Typical commit's message contains parenthesized acronym of the system it's contents are (primarily) refer to: `(PAR)` (from parsing), `(PSI)` (from PSI), etc.
   Do not scratch your head trying to come up with the best acronym of all the worlds, just be reasonable enough -- we (all the contributors) are smart enough to get what you're trying to say.
   
   Please, assure that your message shortly ascribe the whole content of your commit. If it touches more than just a single piece, please, let the world know:
   
   ```
    (PAR): Replaced `skip_until_eol_rec` recovery util with hand-crafted strategy;
           Tidied up `GeneratedParserUtilBase`
   ```