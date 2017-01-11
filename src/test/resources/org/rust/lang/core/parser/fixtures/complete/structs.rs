struct S1;
struct S2 {}
struct S3 { field: f32  }
struct S4 { field: f32, }

#[repr(C)]
union U {
    i: i32,
    f: f32,
}

fn foo() {
    struct S1;
    struct S2 {}
    struct S3 { field: f32  }
    struct S4 { field: f32, }

    #[repr(C)]
    union U {
        i: i32,
        f: f32,
    }
}
