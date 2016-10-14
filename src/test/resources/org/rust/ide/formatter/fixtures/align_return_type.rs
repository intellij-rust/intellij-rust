fn foo(x: i32, y: String)
    -> String {
    y + x.to_string()
}

fn foo(x: i32,
       y: String)
   -> String {
    y + x.to_string()
}

fn foo(
    x: i32,
    y: String)
        -> String {
    y + x.to_string()
}

fn foo(
    x: i32,
    y: String
)             -> String {
    y + x.to_string()
}

fn foo<T>(t: T)
    -> T
          where T: Clone { }
