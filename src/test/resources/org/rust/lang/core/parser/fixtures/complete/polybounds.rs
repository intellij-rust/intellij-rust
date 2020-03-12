fn single_bound<T: Bar>() {}

fn parenthesized_bound<T: (Bar)>() {}

struct QuestionBound<T: ?Sized>(Unique<T>);

struct ParenthesizedQuestionBound<T: (?Sized)>(Unique<T>);

fn multiple_bound<T: Bar + Baz>() {}

fn parenthesized_multiple_bound<T: (Bar) + (Baz)>() {}

fn lifetime_bound<'a, T:'a>() {}

// ('a) syntactically invalid
fn parenthesized_lifetime_bound<'a, T: ('a)>() {}

fn for_lifetime_bound<F>(f: F) where F: for<'a> Fn(&'a i32) {}

fn parenthesized_for_lifetime_bound<F>(f: F) where F: (for<'a> Fn(&'a i32)) {}

fn impl_bound() -> impl Bar {}

fn parenthesized_impl_bound() -> impl (Bar) {}

fn impl_multiple_bound() -> impl Bar + Baz {}

fn parenthesized_impl_multiple_bound() -> impl (Bar) + (Baz) {}

fn dyn_bound(b: &mut dyn Bar) {}

fn parenthesized_dyn_bound(b: &mut dyn (Bar)) {}

fn dyn_multiple_bound(b: &mut dyn Bar + Baz) {}

fn parenthesized_dyn_multiple_bound(b: &mut dyn (Bar) + (Baz)) {}

fn lifetime_bound_on_Fn_returning_reference<'b, F, Z: 'b>() where F: Fn() -> &'b Z + 'static {}

fn assoc_type_bounds1<T: Foo<Item: Bar>>(t: T) {}
fn assoc_type_bounds2<T: Foo<Item: Bar+Baz>>(t: T) {}
fn assoc_type_bounds3<T: Foo<Item1: Bar, Item2 = ()>>(t: T) {}
fn assoc_type_bounds4<T: Foo<Item1 = (), Item2: Bar>>(t: T) {}
fn assoc_type_bounds_in_args(t: &dyn Foo<Item: Bar>) {}
