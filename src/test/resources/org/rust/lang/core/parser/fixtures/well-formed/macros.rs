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

thread_local!(static HANDLE: Handle = Handle(0));

fn foo() {
    println!("{}", 92);
    let v1 = vec![1, 2, 3];
    let v2 = vec![1; 10];
    try!(bar());
}