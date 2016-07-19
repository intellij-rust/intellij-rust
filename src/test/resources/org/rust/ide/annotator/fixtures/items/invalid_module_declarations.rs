<error descr="Cannot declare a new module at this location">mod foo;</error>

mod inner {
    <error descr="Unresolved module">mod bar;</error>
}

fn main() {
    mod foo {
        <error descr="Cannot declare a non-inline module inside a block unless it has a path attribute">mod bar;</error>
    }
}
