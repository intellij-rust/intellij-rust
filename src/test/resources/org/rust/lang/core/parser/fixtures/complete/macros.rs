peg! parser_definition(r#"
"#);

macro_rules! vec {
    ( $( $x:expr ),* ) => {
        {
            let mut temp_vec = Vec::new();
            $(
                temp_vec.push($x);
            )*
            temp_vec
        }
    };
}

macro_rules! default {
    ($ty: ty) => { /* ANYTHING */ };
}

default!(String);

thread_local!(static HANDLE: Handle = Handle(0));

fn foo() {
    println!("{}", 92);
    let v1 = vec![1, 2, 3];
    let v2 = vec![1; 10];
    try!(bar());
    format!("{argument}", argument = "test");  // => "test"
    format_args!("{name} {}", 1, name = 2);    // => "2 1"
    debug!("{a} {c} {b}", a="a", b='b', c=3);  // => "a 3 b"

    try![bar()];
    try! {
        bar()
    }

    format!["hello {}", "world!"];
    format! {
        "x = {}, y = {y}",
        10, y = 30
    }

    error!();

    let a = 42u32;
    let b = 43u32;
    assert!(a == b);
    assert![a == b];
    assert!{a == b};

    assert_eq!(a, b, "Some text");
    assert_ne!(a, b, "Some text");
    assert!(a == b, "Some text");
    assert!(a == b, "Text {} {} syntax", "with", "format");

    assert!(a == b);
    debug_assert!(a == b);
    assert_eq!(a, b);
    debug_assert_eq!(a, b);
    assert_ne!(a, b);
    debug_assert_ne!(a, b);

    let v: Vec<i32> = vec![];
    panic!("division by zero");

    trace!(target: "smbc", "open_with {:?}", options);
}
