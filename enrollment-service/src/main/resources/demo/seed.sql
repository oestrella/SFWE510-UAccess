-- example.invalid addresses and names are intentionally fictitious.
INSERT INTO students (id, student_number, name, email) VALUES
 ('20000000-0000-0000-0000-000000000001', 'DEMO001', 'Demo Student One', 'demo.one@example.invalid'),
 ('20000000-0000-0000-0000-000000000002', 'DEMO002', 'Demo Student Two', 'demo.two@example.invalid')
-- Repeated dev startup should not duplicate the sample rows.
ON CONFLICT DO NOTHING;
