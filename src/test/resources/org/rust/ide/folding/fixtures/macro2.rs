macro n<fold text='{...}'>($foo:ident, $S:ident, $i:ident, $m:ident)</fold> <fold text='{...}'>{
    mod $foo <fold text='{...}'>{
        #<fold text='{...}'>[derive<fold text='{...}'>(Default)</fold>]</fold>
        pub struct $S <fold text='{...}'>{ $i: u32 }</fold>
        pub macro $m<fold text='{...}'>($e:expr)</fold> <fold text='{...}'>{ $e.$i }</fold>
    }</fold>
}</fold>

pub macro bar <fold text='{...}'>{
    [$e:expr] => <fold text='{...}'>{
        println!<fold text='{...}'>("Hello!, {}", $e)</fold>;
    }</fold>
}</fold>


