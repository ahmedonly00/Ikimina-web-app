import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useGetAllGroupsQuery, useCreateSavingsGroupMutation } from '../../app/api/apiSlice';
import { useGroup } from '../../contexts/GroupContext';
import toast from 'react-hot-toast';
import { FaPlus, FaUsers, FaTrash, FaEdit, FaSpinner } from 'react-icons/fa';
import PageShell from '../../components/ui/PageShell';

const GroupsPage = () => {
  const navigate = useNavigate();
  const { currentGroup, changeGroup } = useGroup();
  const { data: groups = [], isLoading, refetch } = useGetAllGroupsQuery();
  const [createGroup, { isLoading: isCreating }] = useCreateSavingsGroupMutation();
  const [showCreateModal, setShowCreateModal] = useState(false);
  const emptyGroup = {
    name: '',
    description: '',
    adminUserFirstName: '',
    adminUserLastName: '',
    adminUserEmail: '',
    adminUserPhoneNumber: '',
  };
  const [newGroup, setNewGroup] = useState(emptyGroup);
  // Returned once by the create call and not retrievable afterwards.
  const [createdAdmin, setCreatedAdmin] = useState(null);
  const [errors, setErrors] = useState({});

  const handleCreateGroup = async e => {
    e.preventDefault();

    // Creating a group also provisions its admin account, so the backend
    // requires these fields.
    const newErrors = {};
    if (!newGroup.name.trim()) newErrors.name = 'Group name is required';
    if (!newGroup.adminUserFirstName.trim())
      newErrors.adminUserFirstName = 'Admin first name is required';
    if (!newGroup.adminUserLastName.trim())
      newErrors.adminUserLastName = 'Admin last name is required';
    if (!newGroup.adminUserEmail.trim()) newErrors.adminUserEmail = 'Admin email is required';
    if (!newGroup.adminUserPhoneNumber.trim())
      newErrors.adminUserPhoneNumber = 'Admin phone number is required';
    if (Object.keys(newErrors).length > 0) {
      setErrors(newErrors);
      return;
    }

    try {
      const created = await createGroup(newGroup).unwrap();
      toast.success('Group created successfully');
      setNewGroup(emptyGroup);
      setErrors({});
      setShowCreateModal(false);
      if (created && created.adminTemporaryPassword) {
        setCreatedAdmin({
          email: created.adminEmail,
          password: created.adminTemporaryPassword,
          groupName: created.name,
        });
      }
      refetch();
    } catch (err) {
      const errorMessage =
        (err && err.data && (err.data.detail || err.data.message)) || 'Failed to create group';
      toast.error(errorMessage);
    }
  };

  const handleGroupSelect = groupId => {
    changeGroup(groupId);
    navigate('/dashboard');
  };

  if (isLoading) {
    return (
      <div className='flex items-center justify-center h-64'>
        <FaSpinner className='animate-spin text-2xl text-primary' />
      </div>
    );
  }

  return (
    <PageShell>
      <div className='px-4 py-8 sm:px-6 lg:px-8'>
        {createdAdmin && (
          <div className='mt-6 rounded-md border border-warning/40 bg-warning-subtle p-4'>
            <h3 className='text-sm font-semibold text-warning'>
              Group admin credentials for {createdAdmin.groupName}
            </h3>

            <p className='mt-1 text-sm text-warning'>
              Share these with the group administrator now. The password is not stored in readable
              form and cannot be shown again.
            </p>

            <dl className='mt-3 space-y-1 text-sm'>
              <div className='flex gap-2'>
                <dt className='font-medium text-warning'>Email:</dt>

                <dd className='font-mono text-warning'>{createdAdmin.email}</dd>
              </div>

              <div className='flex gap-2'>
                <dt className='font-medium text-warning'>Temporary password:</dt>

                <dd className='font-mono text-warning'>{createdAdmin.password}</dd>
              </div>
            </dl>

            <button
              type='button'
              onClick={() => setCreatedAdmin(null)}
              className='mt-3 rounded-md border border-amber-400 bg-surface px-3 py-1.5 text-sm font-medium text-warning hover:bg-warning-subtle'
            >
              I have saved these credentials
            </button>
          </div>
        )}

        <div className='sm:flex sm:items-center'>
          <div className='sm:flex-auto'>
            <h1 className='text-2xl font-semibold text-fg'>Savings Groups</h1>

            <p className='mt-2 text-sm text-fg'>
              Manage your savings groups or create a new one to get started.
            </p>
          </div>

          <div className='mt-4 sm:mt-0 sm:ml-16 sm:flex-none'>
            <button
              type='button'
              onClick={() => setShowCreateModal(true)}
              className='inline-flex items-center justify-center rounded-md border border-transparent bg-primary px-4 py-2 text-sm font-medium text-white shadow-sm hover:bg-primary-hover focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2 sm:w-auto'
            >
              <FaPlus className='-ml-1 mr-2 h-4 w-4' />
              New Group
            </button>
          </div>
        </div>

        <div className='mt-8 flex flex-col'>
          <div className='-my-2 -mx-4 overflow-x-auto sm:-mx-6 lg:-mx-8'>
            <div className='inline-block min-w-full py-2 align-middle md:px-6 lg:px-8'>
              <div className='overflow-hidden shadow ring-1 ring-black ring-opacity-5 md:rounded-lg'>
                <table className='min-w-full divide-y divide-border'>
                  <thead className='bg-bg'>
                    <tr>
                      <th
                        scope='col'
                        className='py-3.5 pl-4 pr-3 text-left text-sm font-semibold text-fg sm:pl-6'
                      >
                        Group Name
                      </th>

                      <th
                        scope='col'
                        className='px-3 py-3.5 text-left text-sm font-semibold text-fg'
                      >
                        Description
                      </th>

                      <th
                        scope='col'
                        className='px-3 py-3.5 text-left text-sm font-semibold text-fg'
                      >
                        Members
                      </th>

                      <th scope='col' className='relative py-3.5 pl-3 pr-4 sm:pr-6'>
                        <span className='sr-only'>Actions</span>
                      </th>
                    </tr>
                  </thead>

                  <tbody className='divide-y divide-border bg-surface'>
                    {groups.map(group => (
                      <tr
                        key={group.id}
                        className={`cursor-pointer hover:bg-bg ${
                          currentGroup?.id === group.id ? 'bg-primary-subtle' : ''
                        }`}
                        onClick={() => handleGroupSelect(group.id)}
                      >
                        <td className='whitespace-nowrap py-4 pl-4 pr-3 text-sm font-medium text-fg sm:pl-6'>
                          <div className='flex items-center'>
                            <FaUsers className='h-5 w-5 text-primary mr-2' />

                            {group.name}

                            {currentGroup?.id === group.id && (
                              <span className='ml-2 inline-flex items-center rounded-full bg-success-subtle px-2.5 py-0.5 text-xs font-medium text-success'>
                                Current
                              </span>
                            )}
                          </div>
                        </td>

                        <td className='whitespace-nowrap px-3 py-4 text-sm text-fg-muted'>
                          {group.description || 'No description'}
                        </td>

                        <td className='whitespace-nowrap px-3 py-4 text-sm text-fg-muted'>
                          {group.memberCount || 0} members
                        </td>

                        <td className='relative whitespace-nowrap py-4 pl-3 pr-4 text-right text-sm font-medium sm:pr-6'>
                          <div className='flex justify-end space-x-2'>
                            <button
                              onClick={e => {
                                e.stopPropagation();

                                // Handle edit
                              }}
                              className='text-primary hover:text-primary'
                            >
                              <FaEdit className='h-4 w-4' />
                            </button>

                            <button
                              onClick={e => {
                                e.stopPropagation();

                                // Handle delete
                              }}
                              className='text-danger hover:text-danger'
                            >
                              <FaTrash className='h-4 w-4' />
                            </button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        </div>

        {/* Create Group Modal */}

        {showCreateModal && (
          <div
            className='fixed z-10 inset-0 overflow-y-auto'
            aria-labelledby='modal-title'
            role='dialog'
            aria-modal='true'
          >
            <div className='flex items-end justify-center min-h-screen pt-4 px-4 pb-20 text-center sm:block sm:p-0'>
              <div
                className='fixed inset-0 bg-fg bg-opacity-75 transition-opacity'
                aria-hidden='true'
                onClick={() => setShowCreateModal(false)}
              ></div>

              <span
                className='hidden sm:inline-block sm:align-middle sm:h-screen'
                aria-hidden='true'
              >
                &#8203;
              </span>

              {/* The dialog role lives on the wrapper above; this is the panel.

                role='presentation' says so explicitly - the click handler here

                only stops a click inside the panel from dismissing it. */}

              <div
                role='presentation'
                className='inline-block align-bottom bg-surface rounded-lg px-4 pt-5 pb-4 text-left overflow-hidden shadow-xl transform transition-all sm:my-8 sm:align-middle sm:max-w-lg sm:w-full sm:p-6'
                onClick={e => e.stopPropagation()}
              >
                <div>
                  <div className='mt-3 text-center sm:mt-5'>
                    <h3 className='text-lg leading-6 font-medium text-fg' id='modal-title'>
                      Create New Group
                    </h3>

                    <div className='mt-2'>
                      <form onSubmit={handleCreateGroup}>
                        <div className='space-y-4'>
                          <div>
                            <label
                              htmlFor='name'
                              className='block text-sm font-medium text-fg text-left'
                            >
                              Group Name <span className='text-red-500'>*</span>
                            </label>

                            <input
                              type='text'
                              id='name'
                              value={newGroup.name}
                              onChange={e => setNewGroup({ ...newGroup, name: e.target.value })}
                              className={`mt-1 block w-full rounded-md border ${
                                errors.name ? 'border-danger/40' : 'border-border'
                              } shadow-sm focus:border-primary focus:ring-primary sm:text-sm p-2`}
                              placeholder='Enter group name'
                            />

                            {errors.name && (
                              <p className='mt-1 text-sm text-danger'>{errors.name}</p>
                            )}
                          </div>

                          <div>
                            <label
                              htmlFor='description'
                              className='block text-sm font-medium text-fg text-left'
                            >
                              Description
                            </label>

                            <textarea
                              id='description'
                              rows={3}
                              value={newGroup.description}
                              onChange={e =>
                                setNewGroup({ ...newGroup, description: e.target.value })
                              }
                              className='mt-1 block w-full rounded-md border border-border shadow-sm focus:border-primary focus:ring-primary sm:text-sm p-2'
                              placeholder='Enter a brief description (optional)'
                            />
                          </div>

                          <div className='pt-2 border-t border-border'>
                            <p className='text-sm font-medium text-fg text-left'>
                              Group Administrator
                            </p>

                            <p className='text-xs text-fg-muted text-left'>
                              An admin account is created for this group. A one-time password is
                              shown once after creation.
                            </p>
                          </div>

                          <div className='grid grid-cols-2 gap-3'>
                            <div>
                              <label
                                htmlFor='adminUserFirstName'
                                className='block text-sm font-medium text-fg text-left'
                              >
                                First Name <span className='text-red-500'>*</span>
                              </label>

                              <input
                                type='text'
                                id='adminUserFirstName'
                                value={newGroup.adminUserFirstName}
                                onChange={e =>
                                  setNewGroup({ ...newGroup, adminUserFirstName: e.target.value })
                                }
                                className={`mt-1 block w-full rounded-md border ${
                                  errors.adminUserFirstName ? 'border-danger/40' : 'border-border'
                                } shadow-sm focus:border-primary focus:ring-primary sm:text-sm p-2`}
                                placeholder='Jean'
                              />

                              {errors.adminUserFirstName && (
                                <p className='mt-1 text-sm text-danger'>
                                  {errors.adminUserFirstName}
                                </p>
                              )}
                            </div>

                            <div>
                              <label
                                htmlFor='adminUserLastName'
                                className='block text-sm font-medium text-fg text-left'
                              >
                                Last Name <span className='text-red-500'>*</span>
                              </label>

                              <input
                                type='text'
                                id='adminUserLastName'
                                value={newGroup.adminUserLastName}
                                onChange={e =>
                                  setNewGroup({ ...newGroup, adminUserLastName: e.target.value })
                                }
                                className={`mt-1 block w-full rounded-md border ${
                                  errors.adminUserLastName ? 'border-danger/40' : 'border-border'
                                } shadow-sm focus:border-primary focus:ring-primary sm:text-sm p-2`}
                                placeholder='Uwimana'
                              />

                              {errors.adminUserLastName && (
                                <p className='mt-1 text-sm text-danger'>
                                  {errors.adminUserLastName}
                                </p>
                              )}
                            </div>
                          </div>

                          <div>
                            <label
                              htmlFor='adminUserEmail'
                              className='block text-sm font-medium text-fg text-left'
                            >
                              Admin Email <span className='text-red-500'>*</span>
                            </label>

                            <input
                              type='email'
                              id='adminUserEmail'
                              value={newGroup.adminUserEmail}
                              onChange={e =>
                                setNewGroup({ ...newGroup, adminUserEmail: e.target.value })
                              }
                              className={`mt-1 block w-full rounded-md border ${
                                errors.adminUserEmail ? 'border-danger/40' : 'border-border'
                              } shadow-sm focus:border-primary focus:ring-primary sm:text-sm p-2`}
                              placeholder='admin@example.com'
                            />

                            {errors.adminUserEmail && (
                              <p className='mt-1 text-sm text-danger'>{errors.adminUserEmail}</p>
                            )}
                          </div>

                          <div>
                            <label
                              htmlFor='adminUserPhoneNumber'
                              className='block text-sm font-medium text-fg text-left'
                            >
                              Admin Phone <span className='text-red-500'>*</span>
                            </label>

                            <input
                              type='tel'
                              id='adminUserPhoneNumber'
                              value={newGroup.adminUserPhoneNumber}
                              onChange={e =>
                                setNewGroup({ ...newGroup, adminUserPhoneNumber: e.target.value })
                              }
                              className={`mt-1 block w-full rounded-md border ${
                                errors.adminUserPhoneNumber ? 'border-danger/40' : 'border-border'
                              } shadow-sm focus:border-primary focus:ring-primary sm:text-sm p-2`}
                              placeholder='+250 788 000 000'
                            />

                            {errors.adminUserPhoneNumber && (
                              <p className='mt-1 text-sm text-danger'>
                                {errors.adminUserPhoneNumber}
                              </p>
                            )}
                          </div>
                        </div>

                        <div className='mt-5 sm:mt-6 sm:grid sm:grid-cols-2 sm:gap-3 sm:grid-flow-row-dense'>
                          <button
                            type='submit'
                            disabled={isCreating}
                            className='w-full inline-flex justify-center rounded-md border border-transparent shadow-sm px-4 py-2 bg-primary text-base font-medium text-white hover:bg-primary-hover focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary sm:col-start-2 sm:text-sm disabled:opacity-70 disabled:cursor-not-allowed'
                          >
                            {isCreating ? (
                              <>
                                <FaSpinner className='animate-spin -ml-1 mr-2 h-4 w-4' />
                                Creating...
                              </>
                            ) : (
                              'Create Group'
                            )}
                          </button>

                          <button
                            type='button'
                            onClick={() => setShowCreateModal(false)}
                            className='mt-3 w-full inline-flex justify-center rounded-md border border-border shadow-sm px-4 py-2 bg-surface text-base font-medium text-fg hover:bg-bg focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary sm:mt-0 sm:col-start-1 sm:text-sm'
                          >
                            Cancel
                          </button>
                        </div>
                      </form>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}
      </div>
    </PageShell>
  );
};

export default GroupsPage;
