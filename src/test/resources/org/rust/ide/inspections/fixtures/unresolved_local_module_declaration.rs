fn main() {
    mod foo {
        <error descr="Cannot declare a non-inline module inside a block unless it has a path attribute">mod bar;</error>
    }
}
