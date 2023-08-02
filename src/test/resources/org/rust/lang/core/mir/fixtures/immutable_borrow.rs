struct S;

const FOO: () = {
    let s1 = S;
    let s2 = &s1;
    let s3 = &s1;
};
