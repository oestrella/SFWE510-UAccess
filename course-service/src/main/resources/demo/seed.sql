-- Fictitious demo data. Never part of a production migration.
INSERT INTO courses (id, course_code, title, description, credits, active) VALUES
 ('10000000-0000-0000-0000-000000000001', 'DEMO101', 'Demo: Cloud Systems', 'Fictitious teaching catalog entry', 3, true),
 ('10000000-0000-0000-0000-000000000002', 'DEMO102', 'Demo: Distributed Design', 'Fictitious teaching catalog entry', 3, true)
-- Repeated dev startup should not duplicate the sample rows.
ON CONFLICT DO NOTHING;
