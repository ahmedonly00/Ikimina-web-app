import React, { useState } from 'react';
import {
  useGetAllSubscriptionsQuery,
  useManuallyActivateSubscriptionMutation,
  useGetPaymentHistoryQuery,
} from '../adminApi';
import { FaCheck, FaHistory, FaCreditCard, FaExclamationTriangle, FaBan } from 'react-icons/fa';
import toast from 'react-hot-toast';

const SubscriptionManagement = () => {
  const [selectedSubscription, setSelectedSubscription] = useState(null);
  const [showActivateModal, setShowActivateModal] = useState(false);
  const [showPaymentsModal, setShowPaymentsModal] = useState(false);
  const [reason, setReason] = useState('');

  const { data: subscriptions, isLoading } = useGetAllSubscriptionsQuery();
  const [manuallyActivate, { isLoading: isActivating }] = useManuallyActivateSubscriptionMutation();

  const handleManualActivate = async () => {
    if (!selectedSubscription || !reason.trim()) {
      toast.error('Please provide a reason for activation');
      return;
    }

    try {
      await manuallyActivate({ subscriptionId: selectedSubscription.id, reason }).unwrap();
      toast.success('Subscription activated successfully');
      setShowActivateModal(false);
      setReason('');
      setSelectedSubscription(null);
    } catch (err) {
      toast.error('Failed to activate subscription: ' + (err.data?.message || err.message));
    }
  };

  const getStatusBadge = subscription => {
    switch (subscription.status) {
      case 'ACTIVE':
        return (
          <span className='px-2 py-1 text-xs rounded-full bg-green-100 text-green-800'>Active</span>
        );
      case 'GRACE_PERIOD':
        return (
          <span className='px-2 py-1 text-xs rounded-full bg-yellow-100 text-yellow-800'>
            Grace Period
          </span>
        );
      case 'SUSPENDED':
        return (
          <span className='px-2 py-1 text-xs rounded-full bg-red-100 text-red-800'>Suspended</span>
        );
      default:
        return (
          <span className='px-2 py-1 text-xs rounded-full bg-gray-100 text-gray-800'>
            {subscription.status}
          </span>
        );
    }
  };

  const getDaysUntilExpiry = endDate => {
    const today = new Date();
    const expiry = new Date(endDate);
    const diffTime = expiry - today;
    const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));
    return diffDays;
  };

  if (isLoading) {
    return <div className='flex justify-center items-center h-64'>Loading subscriptions...</div>;
  }

  // Calculate totals
  const totalRevenue = subscriptions?.reduce((sum, s) => sum + (s.monthlyPrice || 0), 0) || 0;
  const activeSubscriptions = subscriptions?.filter(s => s.status === 'ACTIVE').length || 0;
  const gracePeriodSubscriptions =
    subscriptions?.filter(s => s.status === 'GRACE_PERIOD').length || 0;
  const suspendedSubscriptions = subscriptions?.filter(s => s.status === 'SUSPENDED').length || 0;

  return (
    <div className='space-y-6'>
      {/* Header */}
      <div>
        <h1 className='text-2xl font-bold text-gray-900'>Subscription Management</h1>
        <p className='text-gray-500 mt-1'>Manage group subscriptions and payments</p>
      </div>

      {/* Stats Cards */}
      <div className='grid grid-cols-1 md:grid-cols-4 gap-6'>
        <div className='bg-white p-6 rounded-lg shadow'>
          <div className='flex items-center'>
            <div className='flex-shrink-0 bg-green-100 rounded-md p-3'>
              <FaCheck className='h-6 w-6 text-green-600' />
            </div>
            <div className='ml-4'>
              <p className='text-sm font-medium text-gray-500'>Active</p>
              <p className='text-2xl font-semibold text-gray-900'>{activeSubscriptions}</p>
            </div>
          </div>
        </div>

        <div className='bg-white p-6 rounded-lg shadow'>
          <div className='flex items-center'>
            <div className='flex-shrink-0 bg-yellow-100 rounded-md p-3'>
              <FaExclamationTriangle className='h-6 w-6 text-yellow-600' />
            </div>
            <div className='ml-4'>
              <p className='text-sm font-medium text-gray-500'>Grace Period</p>
              <p className='text-2xl font-semibold text-gray-900'>{gracePeriodSubscriptions}</p>
            </div>
          </div>
        </div>

        <div className='bg-white p-6 rounded-lg shadow'>
          <div className='flex items-center'>
            <div className='flex-shrink-0 bg-red-100 rounded-md p-3'>
              <FaBan className='h-6 w-6 text-red-600' />
            </div>
            <div className='ml-4'>
              <p className='text-sm font-medium text-gray-500'>Suspended</p>
              <p className='text-2xl font-semibold text-gray-900'>{suspendedSubscriptions}</p>
            </div>
          </div>
        </div>

        <div className='bg-white p-6 rounded-lg shadow'>
          <div className='flex items-center'>
            <div className='flex-shrink-0 bg-blue-100 rounded-md p-3'>
              <FaCreditCard className='h-6 w-6 text-blue-600' />
            </div>
            <div className='ml-4'>
              <p className='text-sm font-medium text-gray-500'>Monthly Revenue</p>
              <p className='text-2xl font-semibold text-gray-900'>
                ${totalRevenue.toLocaleString()}
              </p>
            </div>
          </div>
        </div>
      </div>

      {/* Subscriptions Table */}
      <div className='bg-white shadow rounded-lg overflow-hidden'>
        <div className='overflow-x-auto'>
          <table className='min-w-full divide-y divide-gray-200'>
            <thead className='bg-gray-50'>
              <tr>
                <th className='px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase'>
                  Group
                </th>
                <th className='px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase'>
                  Plan
                </th>
                <th className='px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase'>
                  Status
                </th>
                <th className='px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase'>
                  Price
                </th>
                <th className='px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase'>
                  End Date
                </th>
                <th className='px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase'>
                  Days Left
                </th>
                <th className='px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase'>
                  Actions
                </th>
              </tr>
            </thead>
            <tbody className='bg-white divide-y divide-gray-200'>
              {subscriptions?.map(subscription => {
                const daysLeft = getDaysUntilExpiry(subscription.endDate);
                return (
                  <tr key={subscription.id} className='hover:bg-gray-50'>
                    <td className='px-4 py-4'>
                      <div className='text-sm font-medium text-gray-900'>
                        {subscription.groupName}
                      </div>
                      <div className='text-sm text-gray-500'>{subscription.groupAdminName}</div>
                    </td>
                    <td className='px-4 py-4 text-sm text-gray-900'>{subscription.planName}</td>
                    <td className='px-4 py-4'>
                      {getStatusBadge(subscription)}
                      {subscription.suspensionReason && (
                        <div className='text-xs text-gray-500 mt-1'>
                          {subscription.suspensionReason}
                        </div>
                      )}
                    </td>
                    <td className='px-4 py-4 text-sm text-gray-900'>
                      ${subscription.monthlyPrice?.toLocaleString() || 0} RWF
                    </td>
                    <td className='px-4 py-4 text-sm text-gray-900'>
                      {new Date(subscription.endDate).toLocaleDateString()}
                    </td>
                    <td className='px-4 py-4 text-sm'>
                      <span
                        className={`font-medium ${
                          daysLeft < 0
                            ? 'text-red-600'
                            : daysLeft <= 7
                              ? 'text-yellow-600'
                              : 'text-green-600'
                        }`}
                      >
                        {daysLeft < 0
                          ? `${Math.abs(daysLeft)} days ago`
                          : daysLeft === 0
                            ? 'Today'
                            : `${daysLeft} days`}
                      </span>
                    </td>
                    <td className='px-4 py-4 text-sm'>
                      <div className='flex space-x-2'>
                        {(subscription.status === 'SUSPENDED' ||
                          subscription.status === 'GRACE_PERIOD') && (
                          <button
                            onClick={() => {
                              setSelectedSubscription(subscription);
                              setShowActivateModal(true);
                            }}
                            className='text-green-600 hover:text-green-800'
                            title='Manually Activate'
                          >
                            <FaCheck />
                          </button>
                        )}
                        <button
                          onClick={() => {
                            setSelectedSubscription(subscription);
                            setShowPaymentsModal(true);
                          }}
                          className='text-blue-600 hover:text-blue-800'
                          title='View Payment History'
                        >
                          <FaHistory />
                        </button>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>

      {/* Manual Activate Modal */}
      {showActivateModal && selectedSubscription && (
        <div className='fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50'>
          <div className='bg-white rounded-lg p-6 max-w-md w-full'>
            <h3 className='text-lg font-medium text-gray-900 mb-4'>
              Manually Activate Subscription
            </h3>
            <p className='text-gray-600 mb-4'>
              Activate subscription for &quot;{selectedSubscription.groupName}&quot; (
              {selectedSubscription.planName})
            </p>
            <textarea
              placeholder='Reason for manual activation...'
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
                  setSelectedSubscription(null);
                }}
                className='px-4 py-2 text-gray-700 border border-gray-300 rounded-md hover:bg-gray-50'
              >
                Cancel
              </button>
              <button
                onClick={handleManualActivate}
                disabled={isActivating}
                className='px-4 py-2 bg-green-600 text-white rounded-md hover:bg-green-700'
              >
                {isActivating ? 'Activating...' : 'Activate'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Payment History Modal */}
      {showPaymentsModal && selectedSubscription && (
        <PaymentHistoryModal
          groupId={selectedSubscription.groupId}
          onClose={() => {
            setShowPaymentsModal(false);
            setSelectedSubscription(null);
          }}
        />
      )}
    </div>
  );
};

// Payment History Modal Component
const PaymentHistoryModal = ({ groupId, onClose }) => {
  const { data: payments, isLoading } = useGetPaymentHistoryQuery(groupId);

  return (
    <div className='fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50'>
      <div className='bg-white rounded-lg p-6 max-w-4xl w-full max-h-[80vh] overflow-hidden'>
        <div className='flex justify-between items-center mb-4'>
          <h3 className='text-lg font-medium text-gray-900'>Payment History</h3>
          <button onClick={onClose} className='text-gray-400 hover:text-gray-500'>
            ×
          </button>
        </div>

        {isLoading ? (
          <div className='flex justify-center items-center h-64'>Loading payments...</div>
        ) : (
          <div className='overflow-x-auto'>
            <table className='min-w-full divide-y divide-gray-200'>
              <thead className='bg-gray-50'>
                <tr>
                  <th className='px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase'>
                    Transaction ID
                  </th>
                  <th className='px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase'>
                    Amount
                  </th>
                  <th className='px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase'>
                    Method
                  </th>
                  <th className='px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase'>
                    Status
                  </th>
                  <th className='px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase'>
                    Date
                  </th>
                </tr>
              </thead>
              <tbody className='bg-white divide-y divide-gray-200'>
                {payments?.map(payment => (
                  <tr key={payment.id}>
                    <td className='px-4 py-4 text-sm text-gray-900'>{payment.transactionId}</td>
                    <td className='px-4 py-4 text-sm text-gray-900'>${payment.amount} RWF</td>
                    <td className='px-4 py-4 text-sm text-gray-900'>{payment.paymentMethod}</td>
                    <td className='px-4 py-4 text-sm'>
                      <span
                        className={`px-2 py-1 text-xs rounded-full ${
                          payment.status === 'COMPLETED'
                            ? 'bg-green-100 text-green-800'
                            : payment.status === 'PENDING'
                              ? 'bg-yellow-100 text-yellow-800'
                              : 'bg-red-100 text-red-800'
                        }`}
                      >
                        {payment.status}
                      </span>
                    </td>
                    <td className='px-4 py-4 text-sm text-gray-900'>
                      {new Date(payment.paymentDate).toLocaleString()}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
};

export default SubscriptionManagement;
