foo!()

fn f() {
    foo!()
    let a = 0;
}

foo! { (a }
foo! { [ (a ] }
foo! { [ a ) }
foo! ( { a ] );

// Should be the last in the file
foo! { (
