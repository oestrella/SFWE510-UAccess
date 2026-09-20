CREATE TABLE courses (
    id uuid PRIMARY KEY,
    course_code varchar(32) NOT NULL,
    title varchar(160) NOT NULL,
    description varchar(2000),
    credits integer NOT NULL CHECK (credits BETWEEN 1 AND 6),
    active boolean NOT NULL DEFAULT true,
    CONSTRAINT uq_courses_code UNIQUE (course_code),
    CONSTRAINT ck_courses_code CHECK (course_code = upper(btrim(course_code)) AND length(course_code) > 0),
    CONSTRAINT ck_courses_title CHECK (length(btrim(title)) > 0)
);
CREATE INDEX ix_courses_catalog ON courses (active, course_code, id);
