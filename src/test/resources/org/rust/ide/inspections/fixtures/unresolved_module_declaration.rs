<error descr="Cannot declare a new module at this location">mod foo;</error>

mod inner {
    <error descr="Unresolved module">mod bar;</error>
}

fn main() {}
