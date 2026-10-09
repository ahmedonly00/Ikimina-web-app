-- Phase 1: row-level security on every tenant table (spec 5.1, 5.3) - the last line of
-- defence behind GroupAccessGuard and the permission checks.
--
-- The application sets, per transaction (SET LOCAL semantics, so nothing leaks between
-- pooled connections):
--   app.current_group_id  the group the request is acting in (empty outside a group)
--   app.current_user_id   the authenticated user (empty when anonymous)
--
-- Policies:
--   tenant_*   TO the application role: rows of the current group only. Memberships are
--              also visible to their own user, so "my groups" works across groups.
--   owner_all  TO the schema owner (the role running these migrations). FORCE makes the
--              owner subject to RLS too; this restores its access for migrations and for
--              the two narrow SECURITY DEFINER functions below.

CREATE FUNCTION app_current_group_id() RETURNS BIGINT
    LANGUAGE sql STABLE
AS $$ SELECT NULLIF(current_setting('app.current_group_id', true), '')::BIGINT $$;

CREATE FUNCTION app_current_user_id() RETURNS BIGINT
    LANGUAGE sql STABLE
AS $$ SELECT NULLIF(current_setting('app.current_user_id', true), '')::BIGINT $$;

-- groups ---------------------------------------------------------------------------
ALTER TABLE groups ENABLE ROW LEVEL SECURITY;
ALTER TABLE groups FORCE ROW LEVEL SECURITY;
CREATE POLICY owner_all ON groups TO CURRENT_USER USING (true) WITH CHECK (true);
CREATE POLICY tenant_select ON groups FOR SELECT TO ${appRole}
    USING (id = app_current_group_id()
           OR EXISTS (SELECT 1 FROM group_memberships m
                      WHERE m.group_id = groups.id
                        AND m.user_id = app_current_user_id()
                        AND m.status = 'ACTIVE'));
CREATE POLICY tenant_update ON groups FOR UPDATE TO ${appRole}
    USING (id = app_current_group_id())
    WITH CHECK (id = app_current_group_id());

-- group_memberships ------------------------------------------------------------------
ALTER TABLE group_memberships ENABLE ROW LEVEL SECURITY;
ALTER TABLE group_memberships FORCE ROW LEVEL SECURITY;
CREATE POLICY owner_all ON group_memberships TO CURRENT_USER USING (true) WITH CHECK (true);
CREATE POLICY tenant_select ON group_memberships FOR SELECT TO ${appRole}
    USING (group_id = app_current_group_id() OR user_id = app_current_user_id());
CREATE POLICY tenant_insert ON group_memberships FOR INSERT TO ${appRole}
    WITH CHECK (group_id = app_current_group_id());
CREATE POLICY tenant_update ON group_memberships FOR UPDATE TO ${appRole}
    USING (group_id = app_current_group_id())
    WITH CHECK (group_id = app_current_group_id());

-- Tables visible only inside their own group -------------------------------------------
ALTER TABLE group_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE group_settings FORCE ROW LEVEL SECURITY;
CREATE POLICY owner_all ON group_settings TO CURRENT_USER USING (true) WITH CHECK (true);
CREATE POLICY tenant_all ON group_settings TO ${appRole}
    USING (group_id = app_current_group_id()) WITH CHECK (group_id = app_current_group_id());

ALTER TABLE group_invitations ENABLE ROW LEVEL SECURITY;
ALTER TABLE group_invitations FORCE ROW LEVEL SECURITY;
CREATE POLICY owner_all ON group_invitations TO CURRENT_USER USING (true) WITH CHECK (true);
CREATE POLICY tenant_all ON group_invitations TO ${appRole}
    USING (group_id = app_current_group_id()) WITH CHECK (group_id = app_current_group_id());

ALTER TABLE office_transfers ENABLE ROW LEVEL SECURITY;
ALTER TABLE office_transfers FORCE ROW LEVEL SECURITY;
CREATE POLICY owner_all ON office_transfers TO CURRENT_USER USING (true) WITH CHECK (true);
CREATE POLICY tenant_all ON office_transfers TO ${appRole}
    USING (group_id = app_current_group_id()) WITH CHECK (group_id = app_current_group_id());

ALTER TABLE settings_change_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE settings_change_requests FORCE ROW LEVEL SECURITY;
CREATE POLICY owner_all ON settings_change_requests TO CURRENT_USER USING (true) WITH CHECK (true);
CREATE POLICY tenant_all ON settings_change_requests TO ${appRole}
    USING (group_id = app_current_group_id()) WITH CHECK (group_id = app_current_group_id());

-- audit_logs: inside a group, that group's chain; outside any group, the platform chain.
ALTER TABLE audit_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_logs FORCE ROW LEVEL SECURITY;
CREATE POLICY owner_all ON audit_logs TO CURRENT_USER USING (true) WITH CHECK (true);
CREATE POLICY tenant_select ON audit_logs FOR SELECT TO ${appRole}
    USING (group_id IS NOT DISTINCT FROM app_current_group_id());
CREATE POLICY tenant_insert ON audit_logs FOR INSERT TO ${appRole}
    WITH CHECK (group_id IS NOT DISTINCT FROM app_current_group_id());

-- Narrow, audited exits from tenant scope -----------------------------------------------

-- A new group has no id yet, so no tenant scope can admit its insert. This creates the
-- bare row for the calling user and returns its ids; everything else about the group is
-- then written inside that group's scope.
CREATE FUNCTION create_group(p_name TEXT, OUT group_id BIGINT, OUT group_public_id UUID)
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = ikimina, pg_temp
AS $$
DECLARE
    creator BIGINT := app_current_user_id();
BEGIN
    IF creator IS NULL THEN
        RAISE EXCEPTION 'create_group requires an authenticated user' USING ERRCODE = 'insufficient_privilege';
    END IF;
    INSERT INTO groups (name, created_by) VALUES (p_name, creator)
    RETURNING groups.id, groups.public_id INTO group_id, group_public_id;
END;
$$;

-- Someone accepting an invitation is not yet a member, so cannot see the group. Holding
-- the invitation's secret token is what entitles them to learn its internal id.
CREATE FUNCTION find_group_for_invitation(p_group_public_id UUID, p_token_hash TEXT) RETURNS BIGINT
    LANGUAGE sql STABLE SECURITY DEFINER SET search_path = ikimina, pg_temp
AS $$
    SELECT g.id
    FROM groups g
    JOIN group_invitations i ON i.group_id = g.id
    WHERE g.public_id = p_group_public_id
      AND i.token_hash = p_token_hash
$$;

REVOKE ALL ON FUNCTION create_group(TEXT) FROM PUBLIC;
REVOKE ALL ON FUNCTION find_group_for_invitation(UUID, TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION create_group(TEXT) TO ${appRole};
GRANT EXECUTE ON FUNCTION find_group_for_invitation(UUID, TEXT) TO ${appRole};
