fn foo1<U: impl T>() {}
fn foo2<U: impl T + T>() {}
fn foo3<T: impl Fn() -> i32>() {}
fn bar1<U: dyn T>() {}
fn bar2<U: dyn T + T>() {}
fn bar3<T: dyn Fn() -> i32>() {}
