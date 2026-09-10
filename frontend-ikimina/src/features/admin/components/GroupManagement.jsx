import React, { useState } from 'react';
import {
  useGetAdminGroupsQuery,
  useActivateGroupMutation,
  useSuspendGroupMutation,
} from '../adminApi';
import { FaCheck, FaBan, FaEye, FaSearch } from 'react-icons/fa';
import toast from 'react-hot-toast';

const GroupManagement = () => {
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedGroup, setSelectedGroup] = useState(null);
  const [showActivateModal, setShowActivateModal] = useState(false);
  const [showSuspendModal, setShowSuspendModal] = useState(false);
  const [reason, setReason] = useState('');

  const { data: groups, isLoading } = useGetAdminGroupsQuery();
  const [activateGroup, { isLoading: isActivating }] = useActivateGroupMutation();
  const [suspendGroup, { isLoading: isSuspending }] = useSuspendGroupMutation();

  const filteredGroups =
    groups?.filter(
      group =>
        group.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
        group.admin?.firstName?.toLowerCase().includes(searchTerm.toLowerCase()) ||
        group.admin?.lastName?.toLowerCase().includes(searchTerm.toLowerCase())
    ) || [];

  const handleActivate = async () => {
    if (!selectedGroup || !reason.trim()) {
      toast.error('Please provide a reason');
      return;
    }

    try {
      await activateGroup({ groupId: selectedGroup.id, reason }).unwrap();
      toast.success('Group activated successfully');
      setShowActivateModal(false);
      setReason('');
      setSelectedGroup(null);
    } catch (err) {
      toast.error('Failed to activate group: ' + (err.data?.message || err.message));
    }
  };

  const handleSuspend = async () => {
    if (!selectedGroup || !reason.trim()) {
      toast.error('Please provide a reason');
      return;
    }

    try {
      await suspendGroup({ groupId: selectedGroup.id, reason }).unwrap();
      toast.success('Group suspended successfully');
      setShowSuspendModal(false);
      setReason('');
      setSelectedGroup(null);
    } catch (err) {
      toast.error('Failed to suspend group: ' + (err.data?.message || err.message));
    }
  };

  const getStatusBadge = group => {
    if (group.isSuspended) {
      return (
        <span className='px-2 py-1 text-xs rounded-full bg-red-100 text-red-800'>Suspended</span>
      );
    }
    if (group.isActive) {
      return (
        <span className='px-2 py-1 text-xs rounded-full bg-green-100 text-green-800'>Active</span>
      );
    }
    return (
      <span className='px-2 py-1 text-xs rounded-full bg-gray-100 text-gray-800'>Inactive</span>
    );
  };

  if (isLoading) {
    return <div className='flex justify-center items-center h-64'>Loading groups...</div>;
  }

  return (
    <div className='space-y-6'>
      {/* Header */}
      <div className='flex justify-between items-center'>
        <div>
          <h1 className='text-2xl font-bold text-gray-900'>Group Management</h1>
          <p className='text-gray-500 mt-1'>Manage all Ikimina groups in the system</p>
        </div>
      </div>

      {/* Search */}
      <div className='bg-white p-4 rounded-lg shadow'>
        <div className='relative'>
          <FaSearch className='absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400' />
          <input
            type='text'
            placeholder='Search groups by name or admin...'
            value={searchTerm}
            onChange={e => setSearchTerm(e.target.value)}
            className='w-full pl-10 pr-4 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500'
          />
        </div>
      </div>

      {/* Groups Table */}
      <div className='bg-white shadow rounded-lg overflow-hidden'>
        <div className='overflow-x-auto'>
          <table className='min-w-full divide-y divide-gray-200'>
            <thead className='bg-gray-50'>
              <tr>
                <th className='px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase'>
                  Group
                </th>
                <th className='px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase'>
                  Admin
                </th>
                <th className='px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase'>
                  Members
                </th>
                <th className='px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase'>
                  Created
                </th>
                <th className='px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase'>
                  Status
                </th>
                <th className='px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase'>
                  Actions
                </th>
              </tr>
            </thead>
            <tbody className='bg-white divide-y divide-gray-200'>
              {filteredGroups.map(group => (
                <tr key={group.id} className='hover:bg-gray-50'>
                  <td className='px-4 py-4'>
                    <div>
                      <div className='text-sm font-medium text-gray-900'>{group.name}</div>
                      <div className='text-sm text-gray-500'>{group.description}</div>
                    </div>
                  </td>
                  <td className='px-4 py-4'>
                    <div className='text-sm text-gray-900'>
                      {group.admin?.firstName} {group.admin?.lastName}
                    </div>
                    <div className='text-sm text-gray-500'>{group.admin?.email}</div>
                  </td>
                  <td className='px-4 py-4 text-sm text-gray-900'>{group.members?.length || 0}</td>
                  <td className='px-4 py-4 text-sm text-gray-900'>
                    {new Date(group.createdAt).toLocaleDateString()}
                  </td>
                  <td className='px-4 py-4'>
                    {getStatusBadge(group)}
                    {group.suspensionReason && (
                      <div className='text-xs text-gray-500 mt-1'>{group.suspensionReason}</div>
                    )}
                  </td>
                  <td className='px-4 py-4 text-sm'>
                    <div className='flex space-x-2'>
                      {!group.isActive && !group.isSuspended && (
                        <button
                          onClick={() => {
                            setSelectedGroup(group);
                            setShowActivateModal(true);
                          }}
                          className='text-green-600 hover:text-green-800'
                          title='Activate Group'
                        >
                          <FaCheck />
                        </button>
                      )}
                      {group.isActive && !group.isSuspended && (
                        <button
                          onClick={() => {
                            setSelectedGroup(group);
                            setShowSuspendModal(true);
                          }}
                          className='text-red-600 hover:text-red-800'
                          title='Suspend Group'
                        >
                          <FaBan />
                        </button>
                      )}
                      <button
                        onClick={() => (window.location.href = `/admin/groups/${group.id}/details`)}
                        className='text-blue-600 hover:text-blue-800'
                        title='View Details'
                      >
                        <FaEye />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Activate Modal */}
      {showActivateModal && selectedGroup && (
        <div className='fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50'>
          <div className='bg-white rounded-lg p-6 max-w-md w-full'>
            <h3 className='text-lg font-medium text-gray-900 mb-4'>Activate Group</h3>
            <p className='text-gray-600 mb-4'>
              Are you sure you want to activate &quot;{selectedGroup.name}&quot;?
            </p>
            <textarea
              placeholder='Reason for activation...'
              value={reason}
              onChange={e => setReason(e.target.value)}
              className='w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500'
              rows='3'
            />
            <div className='mt-4 flex justify-end space-x-2'>
              <button
                onClick={() => {
                  setShowActivateModal(false);
                  setReason('');
                  setSelectedGroup(null);
                }}
                className='px-4 py-2 text-gray-700 border border-gray-300 rounded-md hover:bg-gray-50'
              >
                Cancel
              </button>
              <button
                onClick={handleActivate}
                disabled={isActivating}
                className='px-4 py-2 bg-green-600 text-white rounded-md hover:bg-green-700'
              >
                {isActivating ? 'Activating...' : 'Activate'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Suspend Modal */}
      {showSuspendModal && selectedGroup && (
        <div className='fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50'>
          <div className='bg-white rounded-lg p-6 max-w-md w-full'>
            <h3 className='text-lg font-medium text-gray-900 mb-4'>Suspend Group</h3>
            <p className='text-gray-600 mb-4'>
              Are you sure you want to suspend &quot;{selectedGroup.name}&quot;? This will restrict
              all group operations.
            </p>
            <textarea
              placeholder='Reason for suspension...'
              value={reason}
              onChange={e => setReason(e.target.value)}
              className='w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500'
              rows='3'
            />
            <div className='mt-4 flex justify-end space-x-2'>
              <button
                onClick={() => {
                  setShowSuspendModal(false);
                  setReason('');
                  setSelectedGroup(null);
                }}
                className='px-4 py-2 text-gray-700 border border-gray-300 rounded-md hover:bg-gray-50'
              >
                Cancel
              </button>
              <button
                onClick={handleSuspend}
                disabled={isSuspending}
                className='px-4 py-2 bg-red-600 text-white rounded-md hover:bg-red-700'
              >
                {isSuspending ? 'Suspending...' : 'Suspend'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default GroupManagement;
