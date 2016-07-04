# 2016-07-4

## New Features

* Richer lookup info in completions (by @Furyhunter).
* `--explain` hyperlinks links in console output (by @tov).
* Backtrace option in run configurations (by @tov).
* Better typification for primitives, self, structs and enums.
* Highlighting of markdown doc comments (by @jajakobyly).
* Expand modules is now a quick fix instead of always applicable and annoying intention.
* Better highlighting of references (by @mfarrugi).


## Fixes

* Don't fail if `cargo --version` does not explicitly include `stable` (by @oistein).
* Parse inclusive range `1 ... 3`.


## Refactorings

* Stubs for impl items.
* Initial implementation of impl index.



# 2016-06-27

## New Features

* First alpha is published.
* Completion contributor for `#[derive()]` caluse (contributed by @bgourlie).
* Stubs and GotoSymbol for fields.
* Resolve and completion for static methods.
* Better notifications about Cargo version in use.
* Type inference for enums, primitives, references and type aliases.
* Formatter option to set a minimum number of blanks between items.


## Fixes

* Language ID is changed from RUST to Rust.
* Literals are properly highlighted inside attributes (contributed by @jajakobyly)
* Better parser recovery in if, while and match.
* More tests and fixes for type inference.


## Refactorings
* `RustModuleIndex` is now based on stubs.
* Tests to make sure that certain operations work on stubs and don't access the
  AST (`getIcon`, `itemPresentation` and `getReference` are checked ).
* Resolve caching.
* `RustComputingVisitor` is used in type inference.
* Overhaul of pattern matching code.



# 2016-06-19

## New features

* GoTo symbol(`Ctrl+Alt+Shift+N`) works for methods.
* Completion works for prelude symbols.
* More formatter options with new code samples in settings (contributed by @jajakobyly).
* It is possible to run all tests inside a single module.
* Functions and statics inside `extern "C" { ... }` blocks are properly resolved.


## Fixes

* Proper lexing of && as one or two tokens
* A ton of fixes for funny bugs in resolve and types:
  - proper scoping for "downwards" resolve: generic parameters are not resolved
    outside the item,
  - `RustPathElement` is not a `NamedElement`,
  - `RustRegPat` is really a RustRefPat (reference pattern) and it is actually
    extends Pattern,
* Couple of NPEs and `IllegalStateExpcetions` during type inference,

* A bunch of tests for type resolving and expression typing.


## Refactorings

* Refreshing RRE: new scopes API, old Resolver class is killed,
enumerateScopesFor is moved inside RRE.

* `RustItemElement` is now an interface.
`RustFnElement` is now a suppertype for a bunch of function-like PSI nodes

* Stubs improvements:
  - methods now have stubs
  - `isPublic` is stored in stubs

* `getIcon` still depends on some AST information, so goto symbol unfortunately
parses the world at the moment.



# 2016-06-13

## New features

* Context aware run configurations
* Working formatter (contributed by @jajakobyly)
* Basic completion for fields and methods


## Fixes

* Better parser recovery (contributed by himikof)
* Cyclic package dependencies in cargo metadata supported
* Module index supports #[path] attribute


## Refactorings

* Fleshing out type inference infra
* Moving cargo related Module extensions to methods of CargoProjectDescription
* Moving Module.cargoProject extension to Project.cargoProject
* Moving all scoping related functions to RustResolveScope (PR in flight)
* Changing RustItem into an interface (in flight, depends on the previous point)


## Misc:

* Better handling of "attaching stdlib sources" workflow (error reporting and
  ability to change stdlib via project configurable)
