import React, { useMemo, useState } from 'react';
import { FiUserPlus, FiSearch, FiUsers } from 'react-icons/fi';
import toast from 'react-hot-toast';
import { useSelector } from 'react-redux';
import { useAppContext } from '../../contexts/AppContext';
import { selectCurrentUser, selectCurrentGroup } from '../auth/authSlice';
import {
  useGetGroupMembersQuery,
  useCreateMemberMutation,
  useSetMemberActiveMutation,
} from '../../app/api/apiSlice';
import QueryError from '../../components/ui/QueryError';
import Modal from '../../components/ui/Modal';
import Button from '../../components/ui/Button';
import roleLabel from '../../utils/roleLabel';

/**
 * The group's member roster.
 *
 * This screen used to be a placeholder that said "Members management
 * functionality will be implemented here" - while carrying a complete Add
 * Member form whose submit handler did `console.log` and closed the dialog.
 * Filling it in appeared to succeed and silently discarded the member, which
 * is worse than having no form at all.
 *
 * It now lists GET /api/users/group/{id} and creates through the real
 * registration endpoint.
 */
const EMPTY_FORM = {
  firstName: '',
  lastName: '',
  email: '',
  phoneNumber: '',
  password: '',
};

const MembersPage = () => {
  const { t } = useAppContext();
  const currentUser = useSelector(selectCurrentUser);
  /*
   * The group in the sidebar selector wins, falling back to the user's own
   * group. A super admin belongs to no group, so without this these screens
   * said "No group selected" while the sidebar displayed a group name - and a
   * super admin had no way to administer a group from here at all.
   */
  const currentGroup = useSelector(selectCurrentGroup);
  const groupId = currentGroup?.id ?? currentUser?.savingsGroupId;

  const [isModalOpen, setIsModalOpen] = useState(false);
  const [form, setForm] = useState(EMPTY_FORM);
  const [errors, setErrors] = useState({});
  const [search, setSearch] = useState('');

  const {
    data: members = [],
    isLoading,
    error,
    refetch,
  } = useGetGroupMembersQuery(groupId, { skip: !groupId });
  const [createMember, { isLoading: isCreating }] = useCreateMemberMutation();
  const [setMemberActive] = useSetMemberActiveMutation();

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return members;
    return members.filter(m =>
      [m.firstName, m.lastName, m.email, m.memberNumber, m.phoneNumber]
        .filter(Boolean)
        .some(v => String(v).toLowerCase().includes(q))
    );
  }, [members, search]);

  const change = e => {
    const { name, value } = e.target;
    setForm(prev => ({ ...prev, [name]: value }));
    setErrors(prev => (Object.keys(prev).length ? {} : prev));
  };

  const validate = () => {
    const next = {};
    if (!form.firstName.trim()) next.firstName = 'Required';
    if (!form.lastName.trim()) next.lastName = 'Required';
    if (!form.email.trim()) next.email = 'Required';
    else if (!/\S+@\S+\.\S+/.test(form.email)) next.email = 'Not a valid email';
    // The backend stores a bcrypt hash of whatever is sent, so an empty
    // password would create an account nobody can sign in to.
    if (!form.password) next.password = 'Required';
    else if (form.password.length < 8) next.password = 'At least 8 characters';
    return next;
  };

  const submit = async e => {
    e.preventDefault();
    const found = validate();
    if (Object.keys(found).length) {
      setErrors(found);
      return;
    }

    try {
      await createMember({
        firstName: form.firstName.trim(),
        lastName: form.lastName.trim(),
        email: form.email.trim(),
        phoneNumber: form.phoneNumber.trim() || null,
        password: form.password,
        savingsGroupId: groupId,
        role: 'ROLE_USER',
        active: true,
      }).unwrap();

      toast.success(`${form.firstName} ${form.lastName} added`);
      setForm(EMPTY_FORM);
      setIsModalOpen(false);
    } catch (err) {
      // Surface the server's reason rather than a generic failure - it
      // distinguishes a duplicate email from a validation problem.
      const message = err?.data?.detail || err?.data?.message || 'Could not add the member';
      toast.error(message);
      setErrors({ submit: message });
    }
  };

  const toggleActive = async member => {
    try {
      await setMemberActive({ userId: member.id, active: !member.active }).unwrap();
      toast.success(`${member.firstName} ${member.active ? 'deactivated' : 'reactivated'}`);
    } catch {
      toast.error('Could not change that member’s status');
    }
  };

  return (
    <div className='h-full w-full overflow-y-auto bg-bg'>
      <div className='mx-auto max-w-7xl px-4 py-6 sm:px-6 lg:px-8'>
        <header className='mb-6 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between'>
          <div>
            <h1>{t('members')}</h1>
            <p className='mt-1 text-sm text-fg-muted'>
              {isLoading
                ? '—'
                : `${members.length} ${members.length === 1 ? 'person' : 'people'} in this group`}
            </p>
          </div>
          <Button onClick={() => setIsModalOpen(true)} disabled={!groupId}>
            <FiUserPlus className='mr-2 h-4 w-4' aria-hidden='true' />
            {t('addMember')}
          </Button>
        </header>

        <QueryError error={error} onRetry={refetch} title='Could not load members' />

        {!groupId && (
          <div className='card p-5'>
            <p className='text-sm text-fg-muted'>No group selected.</p>
          </div>
        )}

        {groupId && (
          <>
            <div className='relative mb-4 max-w-sm'>
              <FiSearch
                className='pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-fg-subtle'
                aria-hidden='true'
              />
              <input
                type='search'
                value={search}
                onChange={e => setSearch(e.target.value)}
                placeholder='Search by name, email or member number'
                aria-label='Search members'
                className='input pl-9'
              />
            </div>

            <div className='card overflow-hidden'>
              <div className='overflow-x-auto'>
                <table className='min-w-full text-sm'>
                  <thead className='table-head'>
                    <tr>
                      <th className='px-4 py-3 text-left'>Member</th>
                      <th className='px-4 py-3 text-left'>Contact</th>
                      <th className='px-4 py-3 text-left'>Role</th>
                      <th className='px-4 py-3 text-left'>Status</th>
                      <th className='px-4 py-3 text-right'>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filtered.map(m => (
                      <tr key={m.id} className='table-row'>
                        <td className='px-4 py-3'>
                          <div className='font-medium text-fg'>
                            {m.firstName} {m.lastName}
                          </div>
                          <div className='tabular text-xs text-fg-muted'>
                            {m.memberNumber || '—'}
                          </div>
                        </td>
                        <td className='px-4 py-3'>
                          <div className='text-fg'>{m.email}</div>
                          <div className='tabular text-xs text-fg-muted'>
                            {m.phoneNumber || '—'}
                          </div>
                        </td>
                        <td className='px-4 py-3'>
                          <span className='badge-neutral'>{roleLabel(m.role)}</span>
                        </td>
                        <td className='px-4 py-3'>
                          {m.active ? (
                            <span className='badge-success'>{t('active')}</span>
                          ) : (
                            <span className='badge-danger'>{t('inactive')}</span>
                          )}
                        </td>
                        <td className='px-4 py-3 text-right'>
                          {/* A group admin must not be able to lock themselves
                              out of their own group. */}
                          {m.id === currentUser?.id ? (
                            <span className='text-xs text-fg-subtle'>You</span>
                          ) : (
                            <Button variant='ghost' size='sm' onClick={() => toggleActive(m)}>
                              {m.active ? t('deactivate') : t('reactivate')}
                            </Button>
                          )}
                        </td>
                      </tr>
                    ))}

                    {!isLoading && filtered.length === 0 && (
                      <tr>
                        <td colSpan={5} className='px-4 py-12 text-center'>
                          <FiUsers
                            className='mx-auto mb-3 h-8 w-8 text-fg-subtle'
                            aria-hidden='true'
                          />
                          <p className='text-sm font-medium text-fg'>
                            {members.length === 0 ? 'No members yet' : 'No matches'}
                          </p>
                          <p className='mt-1 text-sm text-fg-muted'>
                            {members.length === 0
                              ? 'Add the first member of this group.'
                              : 'Try a different search.'}
                          </p>
                        </td>
                      </tr>
                    )}

                    {isLoading && (
                      <tr>
                        <td colSpan={5} className='px-4 py-12 text-center text-sm text-fg-muted'>
                          Loading members…
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          </>
        )}

        <Modal isOpen={isModalOpen} onClose={() => setIsModalOpen(false)} title={t('addNewMember')}>
          <form onSubmit={submit} className='space-y-4' noValidate>
            {errors.submit && (
              <div
                className='rounded-lg border border-danger/40 bg-danger-subtle px-4 py-3 text-sm text-danger'
                role='alert'
              >
                {errors.submit}
              </div>
            )}

            <div className='grid grid-cols-1 gap-4 sm:grid-cols-2'>
              <div>
                <label htmlFor='firstName' className='label'>
                  {t('firstName')}
                </label>
                <input
                  id='firstName'
                  name='firstName'
                  value={form.firstName}
                  onChange={change}
                  className={`input ${errors.firstName ? 'input-error' : ''}`}
                />
                {errors.firstName && <p className='field-error'>{errors.firstName}</p>}
              </div>
              <div>
                <label htmlFor='lastName' className='label'>
                  {t('lastName')}
                </label>
                <input
                  id='lastName'
                  name='lastName'
                  value={form.lastName}
                  onChange={change}
                  className={`input ${errors.lastName ? 'input-error' : ''}`}
                />
                {errors.lastName && <p className='field-error'>{errors.lastName}</p>}
              </div>
            </div>

            <div>
              <label htmlFor='email' className='label'>
                {t('email')}
              </label>
              <input
                id='email'
                name='email'
                type='email'
                value={form.email}
                onChange={change}
                className={`input ${errors.email ? 'input-error' : ''}`}
              />
              {errors.email && <p className='field-error'>{errors.email}</p>}
            </div>

            <div>
              <label htmlFor='phoneNumber' className='label'>
                {t('phone')}
              </label>
              <input
                id='phoneNumber'
                name='phoneNumber'
                type='tel'
                value={form.phoneNumber}
                onChange={change}
                placeholder='+250 7xx xxx xxx'
                className='input'
              />
            </div>

            <div>
              <label htmlFor='password' className='label'>
                Temporary password
              </label>
              <input
                id='password'
                name='password'
                type='text'
                value={form.password}
                onChange={change}
                className={`input ${errors.password ? 'input-error' : ''}`}
              />
              {errors.password ? (
                <p className='field-error'>{errors.password}</p>
              ) : (
                <p className='mt-1 text-xs text-fg-subtle'>
                  Share this with the member out of band. They should change it after signing in.
                </p>
              )}
            </div>

            <div className='flex justify-end gap-2 pt-2'>
              <Button type='button' variant='secondary' onClick={() => setIsModalOpen(false)}>
                {t('cancel')}
              </Button>
              <Button type='submit' disabled={isCreating}>
                {isCreating ? 'Adding…' : t('addMember')}
              </Button>
            </div>
          </form>
        </Modal>
      </div>
    </div>
  );
};

export default MembersPage;
