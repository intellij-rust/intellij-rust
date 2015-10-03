mod foo {
    mod bar {
        struct S;

        impl super::T for S {
        }

        fn f() {
            let x = super::super::foo::bar::S;
            <S as super::T>::foo();
        }
    }

    trait T {
        fn foo() {
        }
    }
}
