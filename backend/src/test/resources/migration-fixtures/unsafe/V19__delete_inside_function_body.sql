-- expect: DELETE FROM
CREATE FUNCTION purge() RETURNS void LANGUAGE plpgsql AS $$
BEGIN
    -- hidden inside a function body
    DELETE FROM ledger_lines;
END;
$$;
