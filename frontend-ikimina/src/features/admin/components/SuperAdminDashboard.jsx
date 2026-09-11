import React from 'react';
import {
  useGetAdminGroupsQuery,
  useGetAdminUsersQuery,
  useGetAllSubscriptionsQuery,
} from '../adminApi';
import { FaUsers, FaMoneyBillWave, FaExclamationTriangle, FaChartLine } from 'react-icons/fa';
import { formatCurrency } from '../../../i18n';

const SuperAdminDashboard = () => {
  const { data: groups, isLoading: groupsLoading } = useGetAdminGroupsQuery();
  const { data: users, isLoading: usersLoading } = useGetAdminUsersQuery();
  const { data: subscriptions, isLoading: subsLoading } = useGetAllSubscriptionsQuery();

  const calculateStats = () => {
    if (!groups || !users || !subscriptions) return null;

    const activeGroups = groups.filter(g => g.isActive).length;
    const suspendedGroups = groups.filter(g => g.isSuspended).length;
    const activeSubscriptions = subscriptions.filter(s => s.status === 'ACTIVE').length;
    const gracePeriodSubscriptions = subscriptions.filter(s => s.status === 'GRACE_PERIOD').length;
    const suspendedSubscriptions = subscriptions.filter(s => s.status === 'SUSPENDED').length;
    const totalRevenue = subscriptions.reduce((sum, s) => sum + (s.monthlyPrice || 0), 0);

    return {
      totalGroups: groups.length,
      activeGroups,
      suspendedGroups,
      totalUsers: users.length,
      activeSubscriptions,
      gracePeriodSubscriptions,
      suspendedSubscriptions,
      totalRevenue,
      needsAttention: gracePeriodSubscriptions + suspendedSubscriptions,
    };
  };

  const stats = calculateStats();

  if (groupsLoading || usersLoading || subsLoading) {
    return <div className='flex justify-center items-center h-64'>Loading dashboard...</div>;
  }

  return (
    <div className='space-y-6'>
      {/* Page Header */}
      <div>
        <h1 className='text-3xl font-bold text-fg'>Super Admin Dashboard</h1>
        <p className='mt-2 text-fg-muted'>Platform overview and management</p>
      </div>

      {/* Stats Cards */}
      <div className='grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6'>
        <div className='bg-surface p-6 rounded-lg shadow'>
          <div className='flex items-center'>
            <div className='flex-shrink-0 bg-blue-100 rounded-md p-3'>
              <FaUsers className='h-6 w-6 text-blue-600' />
            </div>
            <div className='ml-4'>
              <p className='text-sm font-medium text-fg-muted'>Total Groups</p>
              <p className='text-2xl font-semibold text-fg'>{stats?.totalGroups || 0}</p>
              <p className='text-xs text-fg-muted'>{stats?.activeGroups || 0} active</p>
            </div>
          </div>
        </div>

        <div className='bg-surface p-6 rounded-lg shadow'>
          <div className='flex items-center'>
            <div className='flex-shrink-0 bg-green-100 rounded-md p-3'>
              <FaMoneyBillWave className='h-6 w-6 text-green-600' />
            </div>
            <div className='ml-4'>
              <p className='text-sm font-medium text-fg-muted'>Monthly Revenue</p>
              <p className='text-2xl font-semibold text-fg'>
                {formatCurrency(stats?.totalRevenue)}
              </p>
              <p className='text-xs text-fg-muted'>RWF</p>
            </div>
          </div>
        </div>

        <div className='bg-surface p-6 rounded-lg shadow'>
          <div className='flex items-center'>
            <div className='flex-shrink-0 bg-yellow-100 rounded-md p-3'>
              <FaExclamationTriangle className='h-6 w-6 text-yellow-600' />
            </div>
            <div className='ml-4'>
              <p className='text-sm font-medium text-fg-muted'>Needs Attention</p>
              <p className='text-2xl font-semibold text-fg'>{stats?.needsAttention || 0}</p>
              <p className='text-xs text-fg-muted'>
                {stats?.gracePeriodSubscriptions || 0} grace, {stats?.suspendedSubscriptions || 0}{' '}
                suspended
              </p>
            </div>
          </div>
        </div>

        <div className='bg-surface p-6 rounded-lg shadow'>
          <div className='flex items-center'>
            <div className='flex-shrink-0 bg-purple-100 rounded-md p-3'>
              <FaChartLine className='h-6 w-6 text-purple-600' />
            </div>
            <div className='ml-4'>
              <p className='text-sm font-medium text-fg-muted'>Total Users</p>
              <p className='text-2xl font-semibold text-fg'>{stats?.totalUsers || 0}</p>
              <p className='text-xs text-fg-muted'>Across all groups</p>
            </div>
          </div>
        </div>
      </div>

      {/* Quick Actions */}
      <div className='bg-surface p-6 rounded-lg shadow'>
        <h2 className='text-lg font-medium text-fg mb-4'>Quick Actions</h2>
        <div className='grid grid-cols-1 md:grid-cols-3 gap-4'>
          <button
            onClick={() => (window.location.href = '/admin/groups')}
            className='p-4 border border-border rounded-lg hover:bg-bg text-left'
          >
            <h3 className='font-medium text-fg'>Manage Groups</h3>
            <p className='text-sm text-fg-muted mt-1'>Activate, suspend, or configure groups</p>
          </button>

          <button
            onClick={() => (window.location.href = '/admin/subscriptions')}
            className='p-4 border border-border rounded-lg hover:bg-bg text-left'
          >
            <h3 className='font-medium text-fg'>Manage Subscriptions</h3>
            <p className='text-sm text-fg-muted mt-1'>View and manage group subscriptions</p>
          </button>

          <button
            onClick={() => (window.location.href = '/admin/users')}
            className='p-4 border border-border rounded-lg hover:bg-bg text-left'
          >
            <h3 className='font-medium text-fg'>Manage Users</h3>
            <p className='text-sm text-fg-muted mt-1'>Promote admins or suspend users</p>
          </button>
        </div>
      </div>

      {/* Recent Activity */}
      <div className='bg-surface p-6 rounded-lg shadow'>
        <h2 className='text-lg font-medium text-fg mb-4'>Recent Subscription Activity</h2>
        <div className='overflow-x-auto'>
          <table className='min-w-full divide-y divide-border'>
            <thead className='bg-bg'>
              <tr>
                <th className='px-4 py-3 text-left text-xs font-medium text-fg-muted uppercase'>
                  Group
                </th>
                <th className='px-4 py-3 text-left text-xs font-medium text-fg-muted uppercase'>
                  Status
                </th>
                <th className='px-4 py-3 text-left text-xs font-medium text-fg-muted uppercase'>
                  Plan
                </th>
                <th className='px-4 py-3 text-left text-xs font-medium text-fg-muted uppercase'>
                  Expires
                </th>
                <th className='px-4 py-3 text-left text-xs font-medium text-fg-muted uppercase'>
                  Action
                </th>
              </tr>
            </thead>
            <tbody className='bg-surface divide-y divide-border'>
              {subscriptions?.slice(0, 5).map(sub => (
                <tr key={sub.id}>
                  <td className='px-4 py-3 text-sm text-fg'>{sub.groupName}</td>
                  <td className='px-4 py-3 text-sm'>
                    <span
                      className={`px-2 py-1 text-xs rounded-full ${
                        sub.status === 'ACTIVE'
                          ? 'bg-green-100 text-green-800'
                          : sub.status === 'GRACE_PERIOD'
                            ? 'bg-yellow-100 text-yellow-800'
                            : 'bg-red-100 text-red-800'
                      }`}
                    >
                      {sub.status}
                    </span>
                  </td>
                  <td className='px-4 py-3 text-sm text-fg'>{sub.planName}</td>
                  <td className='px-4 py-3 text-sm text-fg'>
                    {new Date(sub.endDate).toLocaleDateString()}
                  </td>
                  <td className='px-4 py-3 text-sm'>
                    {sub.needsAttention && (
                      <button className='text-blue-600 hover:text-blue-800'>Review</button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};

export default SuperAdminDashboard;
