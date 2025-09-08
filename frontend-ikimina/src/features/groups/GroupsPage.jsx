import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useGetAllGroupsQuery, useCreateSavingsGroupMutation } from '../../app/api/apiSlice';
import { useGroup } from '../../contexts/GroupContext';
import { toast } from 'react-toastify';
import { FaPlus, FaUsers, FaTrash, FaEdit, FaSpinner } from 'react-icons/fa';

const GroupsPage = () => {
  const navigate = useNavigate();
  const { currentGroup, changeGroup } = useGroup();
  const { data: groups = [], isLoading, refetch } = useGetAllGroupsQuery();
  const [createGroup, { isLoading: isCreating }] = useCreateSavingsGroupMutation();
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [newGroup, setNewGroup] = useState({ name: '', description: '' });
  const [errors, setErrors] = useState({});

  const handleCreateGroup = async (e) => {
    e.preventDefault();
    
    // Validate form
    const newErrors = {};
    if (!newGroup.name.trim()) newErrors.name = 'Group name is required';
    if (Object.keys(newErrors).length > 0) {
      setErrors(newErrors);
      return;
    }
    
    try {
      await createGroup(newGroup).unwrap();
      toast.success('Group created successfully');
      setNewGroup({ name: '', description: '' });
      setShowCreateModal(false);
      refetch();
    } catch (err) {
      const errorMessage = err?.data?.message || 'Failed to create group';
      toast.error(errorMessage);
    }
  };

  const handleGroupSelect = (groupId) => {
    changeGroup(groupId);
    navigate('/dashboard');
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <FaSpinner className="animate-spin text-2xl text-indigo-600" />
      </div>
    );
  }

  return (
    <div className="px-4 py-8 sm:px-6 lg:px-8">
      <div className="sm:flex sm:items-center">
        <div className="sm:flex-auto">
          <h1 className="text-2xl font-semibold text-gray-900">Savings Groups</h1>
          <p className="mt-2 text-sm text-gray-700">
            Manage your savings groups or create a new one to get started.
          </p>
        </div>
        <div className="mt-4 sm:mt-0 sm:ml-16 sm:flex-none">
          <button
            type="button"
            onClick={() => setShowCreateModal(true)}
            className="inline-flex items-center justify-center rounded-md border border-transparent bg-indigo-600 px-4 py-2 text-sm font-medium text-white shadow-sm hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 sm:w-auto"
          >
            <FaPlus className="-ml-1 mr-2 h-4 w-4" />
            New Group
          </button>
        </div>
      </div>

      <div className="mt-8 flex flex-col">
        <div className="-my-2 -mx-4 overflow-x-auto sm:-mx-6 lg:-mx-8">
          <div className="inline-block min-w-full py-2 align-middle md:px-6 lg:px-8">
            <div className="overflow-hidden shadow ring-1 ring-black ring-opacity-5 md:rounded-lg">
              <table className="min-w-full divide-y divide-gray-300">
                <thead className="bg-gray-50">
                  <tr>
                    <th scope="col" className="py-3.5 pl-4 pr-3 text-left text-sm font-semibold text-gray-900 sm:pl-6">
                      Group Name
                    </th>
                    <th scope="col" className="px-3 py-3.5 text-left text-sm font-semibold text-gray-900">
                      Description
                    </th>
                    <th scope="col" className="px-3 py-3.5 text-left text-sm font-semibold text-gray-900">
                      Members
                    </th>
                    <th scope="col" className="relative py-3.5 pl-3 pr-4 sm:pr-6">
                      <span className="sr-only">Actions</span>
                    </th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-200 bg-white">
                  {groups.map((group) => (
                    <tr 
                      key={group.id} 
                      className={`cursor-pointer hover:bg-gray-50 ${
                        currentGroup?.id === group.id ? 'bg-indigo-50' : ''
                      }`}
                      onClick={() => handleGroupSelect(group.id)}
                    >
                      <td className="whitespace-nowrap py-4 pl-4 pr-3 text-sm font-medium text-gray-900 sm:pl-6">
                        <div className="flex items-center">
                          <FaUsers className="h-5 w-5 text-indigo-500 mr-2" />
                          {group.name}
                          {currentGroup?.id === group.id && (
                            <span className="ml-2 inline-flex items-center rounded-full bg-green-100 px-2.5 py-0.5 text-xs font-medium text-green-800">
                              Current
                            </span>
                          )}
                        </div>
                      </td>
                      <td className="whitespace-nowrap px-3 py-4 text-sm text-gray-500">
                        {group.description || 'No description'}
                      </td>
                      <td className="whitespace-nowrap px-3 py-4 text-sm text-gray-500">
                        {group.memberCount || 0} members
                      </td>
                      <td className="relative whitespace-nowrap py-4 pl-3 pr-4 text-right text-sm font-medium sm:pr-6">
                        <div className="flex justify-end space-x-2">
                          <button
                            onClick={(e) => {
                              e.stopPropagation();
                              // Handle edit
                            }}
                            className="text-indigo-600 hover:text-indigo-900"
                          >
                            <FaEdit className="h-4 w-4" />
                          </button>
                          <button
                            onClick={(e) => {
                              e.stopPropagation();
                              // Handle delete
                            }}
                            className="text-red-600 hover:text-red-900"
                          >
                            <FaTrash className="h-4 w-4" />
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
        <div className="fixed z-10 inset-0 overflow-y-auto" aria-labelledby="modal-title" role="dialog" aria-modal="true">
          <div className="flex items-end justify-center min-h-screen pt-4 px-4 pb-20 text-center sm:block sm:p-0">
            <div className="fixed inset-0 bg-gray-500 bg-opacity-75 transition-opacity" aria-hidden="true" onClick={() => setShowCreateModal(false)}></div>

            <span className="hidden sm:inline-block sm:align-middle sm:h-screen" aria-hidden="true">&#8203;</span>

            <div className="inline-block align-bottom bg-white rounded-lg px-4 pt-5 pb-4 text-left overflow-hidden shadow-xl transform transition-all sm:my-8 sm:align-middle sm:max-w-lg sm:w-full sm:p-6" onClick={e => e.stopPropagation()}>
              <div>
                <div className="mt-3 text-center sm:mt-5">
                  <h3 className="text-lg leading-6 font-medium text-gray-900" id="modal-title">
                    Create New Group
                  </h3>
                  <div className="mt-2">
                    <form onSubmit={handleCreateGroup}>
                      <div className="space-y-4">
                        <div>
                          <label htmlFor="name" className="block text-sm font-medium text-gray-700 text-left">
                            Group Name <span className="text-red-500">*</span>
                          </label>
                          <input
                            type="text"
                            id="name"
                            value={newGroup.name}
                            onChange={(e) => setNewGroup({...newGroup, name: e.target.value})}
                            className={`mt-1 block w-full rounded-md border ${
                              errors.name ? 'border-red-300' : 'border-gray-300'
                            } shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm p-2`}
                            placeholder="Enter group name"
                          />
                          {errors.name && (
                            <p className="mt-1 text-sm text-red-600">{errors.name}</p>
                          )}
                        </div>
                        <div>
                          <label htmlFor="description" className="block text-sm font-medium text-gray-700 text-left">
                            Description
                          </label>
                          <textarea
                            id="description"
                            rows={3}
                            value={newGroup.description}
                            onChange={(e) => setNewGroup({...newGroup, description: e.target.value})}
                            className="mt-1 block w-full rounded-md border border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm p-2"
                            placeholder="Enter a brief description (optional)"
                          />
                        </div>
                      </div>
                      <div className="mt-5 sm:mt-6 sm:grid sm:grid-cols-2 sm:gap-3 sm:grid-flow-row-dense">
                        <button
                          type="submit"
                          disabled={isCreating}
                          className="w-full inline-flex justify-center rounded-md border border-transparent shadow-sm px-4 py-2 bg-indigo-600 text-base font-medium text-white hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 sm:col-start-2 sm:text-sm disabled:opacity-70 disabled:cursor-not-allowed"
                        >
                          {isCreating ? (
                            <>
                              <FaSpinner className="animate-spin -ml-1 mr-2 h-4 w-4" />
                              Creating...
                            </>
                          ) : 'Create Group'}
                        </button>
                        <button
                          type="button"
                          onClick={() => setShowCreateModal(false)}
                          className="mt-3 w-full inline-flex justify-center rounded-md border border-gray-300 shadow-sm px-4 py-2 bg-white text-base font-medium text-gray-700 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 sm:mt-0 sm:col-start-1 sm:text-sm"
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
  );
};

export default GroupsPage;
