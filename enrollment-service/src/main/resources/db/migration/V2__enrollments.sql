CREATE TABLE enrollments (
    id uuid PRIMARY KEY,
    student_id uuid NOT NULL REFERENCES students(id),
    course_id uuid NOT NULL,
    enrolled_at timestamp with time zone NOT NULL,
    CONSTRAINT uq_enrollments_pair UNIQUE (student_id, course_id)
);
CREATE INDEX ix_enrollments_student ON enrollments (student_id, enrolled_at, id);
-- course_id is an external UUID reference: no Course foreign key or shared database.
