struct S1;
struct S2 {}
struct S3 { field: f32  }
struct S4 { field: f32, }
struct S5 { #[foo] field: f32 }
struct S6 { #[foo] field: f32, #[foo] field2: f32 }

struct S10();
struct S11(i32);
struct S12(i32,);
struct S13(i32,i32);
struct S14(#[foo] i32);
struct S15(#[foo] i32, #[foo] i32);

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
