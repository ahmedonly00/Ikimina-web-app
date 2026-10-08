-- Attaching guards is exactly what migrations should do; the TRUNCATE event here is not a TRUNCATE statement.
CREATE TRIGGER trg_example_immutable BEFORE UPDATE OR DELETE ON example_rows
    FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER trg_example_no_truncate BEFORE TRUNCATE ON example_rows
    FOR EACH STATEMENT EXECUTE FUNCTION forbid_mutation();
CREATE TRIGGER trg_example_mixed BEFORE DELETE OR TRUNCATE ON example_rows
    FOR EACH STATEMENT EXECUTE FUNCTION forbid_mutation();
ALTER TABLE example_rows ENABLE ROW LEVEL SECURITY;
ALTER TABLE example_rows FORCE ROW LEVEL SECURITY;
CREATE ROLE example_role NOLOGIN NOBYPASSRLS;
