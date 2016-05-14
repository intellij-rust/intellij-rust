fn main() {
    let ok = self::super::super::foo;
    let ok = super::foo::bar;

    let _ = <error descr="Invalid path: self and super are allowed only at the beginning">::self</error>::foo;
    let _ = <error>::super</error>::foo;
    let _ = <error>self::self</error>;
    let _ = <error>super::self</error>;
    let _ = <error>foo::self</error>::bar;
    let _ = <error>self::foo::super</error>::bar;
}
