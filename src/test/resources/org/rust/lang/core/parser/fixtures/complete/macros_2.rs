pub macro foo($e:expr) {
    println!("Hello!, {}", $e);
}

macro m($($t:tt)*) {
    $($t)*
    use foo::*;
    f();
}

macro n($foo:ident, $S:ident, $i:ident, $m:ident) {
    mod $foo {
        #[derive(Default)]
        pub struct $S { $i: u32 }
        pub macro $m($e:expr) { $e.$i }
    }
}

pub macro bar {
    [$e:expr] => {
        println!("Hello!, {}", $e);
    }
}

macro vec {
    ( $( $x:expr ),* ) => {
        {
            let mut temp_vec = Vec::new();
            $(
                temp_vec.push($x);
            )*
            temp_vec
        }
    },
}

pub macro compile_error {
    ($msg:expr) => ({ /* compiler built-in */ }),
    ($msg:expr,) => ({ /* compiler built-in */ }),
}
