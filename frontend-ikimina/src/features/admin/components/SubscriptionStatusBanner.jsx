import React from 'react';
import { FaExclamationTriangle, FaCreditCard, FaClock } from 'react-icons/fa';
import { formatCurrency } from '../../../i18n';

const SubscriptionStatusBanner = ({ subscription }) => {
  if (!subscription) return null;

  const renderBanner = () => {
    // Hoisted out of the case blocks: a const inside a bare case is scoped to
    // the whole switch and leaks into sibling cases (ESLint no-case-declarations).
    const graceDaysLeft = subscription.daysInGracePeriod || 0;
    const daysLeft = subscription.daysUntilExpiry || 0;

    switch (subscription.status) {
      case 'GRACE_PERIOD':
        return (
          <div className='bg-yellow-50 border-l-4 border-yellow-400 p-4 mb-6'>
            <div className='flex'>
              <div className='flex-shrink-0'>
                <FaExclamationTriangle className='h-5 w-5 text-yellow-400' />
              </div>
              <div className='ml-3'>
                <h3 className='text-sm font-medium text-yellow-800'>
                  Payment Overdue - Grace Period Active
                </h3>
                <div className='mt-2 text-sm text-yellow-700'>
                  <p>
                    Your subscription payment is overdue. You have {graceDaysLeft} days left in the
                    grace period.
                  </p>
                  <p className='mt-1'>
                    Please make a payment of{' '}
                    <strong>{formatCurrency(subscription.monthlyPrice)} RWF</strong> to avoid
                    service suspension.
                  </p>
                  <button className='mt-2 px-4 py-2 bg-yellow-600 text-white rounded-md hover:bg-yellow-700'>
                    <FaCreditCard className='inline mr-2' />
                    Make Payment
                  </button>
                </div>
              </div>
            </div>
          </div>
        );

      case 'SUSPENDED':
        return (
          <div className='bg-red-50 border-l-4 border-red-400 p-4 mb-6'>
            <div className='flex'>
              <div className='flex-shrink-0'>
                <FaExclamationTriangle className='h-5 w-5 text-red-400' />
              </div>
              <div className='ml-3'>
                <h3 className='text-sm font-medium text-red-800'>Subscription Suspended</h3>
                <div className='mt-2 text-sm text-red-700'>
                  <p>Your group has been suspended due to non-payment.</p>
                  <p className='mt-1'>
                    Reason: {subscription.suspendedReason || 'Payment not received'}
                  </p>
                  <p className='mt-2'>
                    To reactivate your group, please contact support or make a payment of
                    <strong> {formatCurrency(subscription.monthlyPrice)} RWF</strong>.
                  </p>
                </div>
              </div>
            </div>
          </div>
        );

      case 'ACTIVE':
        if (daysLeft <= 7) {
          return (
            <div className='bg-blue-50 border-l-4 border-blue-400 p-4 mb-6'>
              <div className='flex'>
                <div className='flex-shrink-0'>
                  <FaClock className='h-5 w-5 text-blue-400' />
                </div>
                <div className='ml-3'>
                  <h3 className='text-sm font-medium text-blue-800'>Subscription Renewal Soon</h3>
                  <div className='mt-2 text-sm text-blue-700'>
                    <p>Your subscription will expire in {daysLeft} days.</p>
                    <p className='mt-1'>
                      Next payment: <strong>{formatCurrency(subscription.monthlyPrice)} RWF</strong>{' '}
                      due on {new Date(subscription.endDate).toLocaleDateString()}
                    </p>
                  </div>
                </div>
              </div>
            </div>
          );
        }
        return null;

      default:
        return null;
    }
  };

  return renderBanner();
};

export default SubscriptionStatusBanner;
