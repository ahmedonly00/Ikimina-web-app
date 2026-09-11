import i18n from '../i18n';

/**
 * Human label for a role.
 *
 * The raw enum was rendered directly, so the UI showed "role_group_admin"
 * under the user's name. Falls back to a de-underscored form rather than the
 * enum if a new role appears before it has a translation.
 */
const KEYS = {
  ROLE_SUPER_ADMIN: 'roleSuperAdmin',
  ROLE_GROUP_ADMIN: 'roleGroupAdmin',
  ROLE_USER: 'roleMember',
};

export const roleLabel = role => {
  if (!role) return '';
  const key = KEYS[role];
  if (key) return i18n.t(key);
  return role
    .replace(/^ROLE_/, '')
    .replace(/_/g, ' ')
    .toLowerCase();
};

export default roleLabel;
