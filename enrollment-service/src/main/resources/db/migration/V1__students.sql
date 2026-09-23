CREATE TABLE students (
    id uuid PRIMARY KEY,
    student_number varchar(32) NOT NULL,
    name varchar(160) NOT NULL,
    email varchar(254) NOT NULL,
    CONSTRAINT uq_students_number UNIQUE (student_number),
    CONSTRAINT uq_students_email UNIQUE (email),
    CONSTRAINT ck_students_number CHECK (student_number = upper(btrim(student_number)) AND length(student_number) > 0),
    CONSTRAINT ck_students_email CHECK (email = lower(btrim(email)) AND length(email) > 0),
    CONSTRAINT ck_students_name CHECK (length(btrim(name)) > 0)
);
