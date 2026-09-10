import React from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  FiBriefcase,
  FiCreditCard,
  FiCalendar,
  FiTrendingUp,
  FiBell,
  FiUser,
} from 'react-icons/fi';
import { useAppContext } from '../../contexts/AppContext';
import { useAppSelector } from '../../app/hooks';
import { selectCurrentUser } from '../auth/authSlice';

const MemberDashboard = () => {
  const { t } = useAppContext();
  const user = useAppSelector(selectCurrentUser);
  const navigate = useNavigate();

  // Mock data for the member
  const memberStats = {
    totalSavings: 1250000, // in RWF
    currentLoan: 500000, // in RWF
    monthlyContribution: 100000, // in RWF
    savingsScore: 85, // percentage
    nextPaymentDue: '2024-01-15',
    loanBalance: 350000, // remaining loan balance
    totalContributions: 12, // number of contributions made
  };

  const recentTransactions = [
    {
      id: 1,
      type: 'deposit',
      amount: 100000,
      date: '2024-01-01',
      description: 'Monthly Savings - Ubwizigame',
    },
    {
      id: 2,
      type: 'deposit',
      amount: 50000,
      date: '2024-01-01',
      description: 'Monthly Savings - Ingaboka',
    },
    {
      id: 3,
      type: 'withdrawal',
      amount: 200000,
      date: '2023-12-15',
      description: 'Loan Disbursement',
    },
    { id: 4, type: 'payment', amount: 50000, date: '2023-12-01', description: 'Loan Repayment' },
  ];

  const upcomingActivities = [
    {
      id: 1,
      type: 'payment',
      title: 'Monthly Savings Contribution',
      dueDate: '2024-01-15',
      amount: 150000,
    },
    {
      id: 2,
      type: 'meeting',
      title: 'Monthly Group Meeting',
      dueDate: '2024-01-20',
      time: '10:00 AM',
    },
    { id: 3, type: 'payment', title: 'Loan Payment Due', dueDate: '2024-01-25', amount: 75000 },
  ];

  const formatCurrency = amount => {
    return new Intl.NumberFormat('rw-RW', {
      style: 'currency',
      currency: 'RWF',
      minimumFractionDigits: 0,
    }).format(amount);
  };

  const formatDate = dateString => {
    const options = { year: 'numeric', month: 'short', day: 'numeric' };
    return new Date(dateString).toLocaleDateString(undefined, options);
  };

  return (
    <div className='space-y-6'>
      {/* Welcome Header */}
      <div className='bg-gradient-to-r from-indigo-500 to-purple-600 rounded-lg shadow-lg p-6 text-white'>
        <h1 className='text-3xl font-bold mb-2'>
          {t('welcome')}, {user?.firstName || 'Member'}!
        </h1>
        <p className='text-sidebar-fg'>
          {t('dashboardWelcomeMessage') || "Here's an overview of your savings and loan status"}
        </p>
      </div>

      {/* Stats Grid */}
      <div className='grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6'>
        {/* Total Savings */}
        <div className='bg-surface dark:bg-surface rounded-lg shadow p-6'>
          <div className='flex items-center'>
            <div className='flex-shrink-0 bg-green-100 dark:bg-green-900 rounded-md p-3'>
              <FiBriefcase className='h-6 w-6 text-green-600 dark:text-green-400' />
            </div>
            <div className='ml-4'>
              <p className='text-sm font-medium text-fg-muted dark:text-fg-subtle'>
                {t('totalSavings')}
              </p>
              <p className='text-2xl font-semibold text-fg dark:text-fg'>
                {formatCurrency(memberStats.totalSavings)}
              </p>
              <p className='text-sm text-green-600 dark:text-green-400'>
                <FiTrendingUp className='inline h-3 w-3 mr-1' />+{memberStats.savingsScore}%{' '}
                {t('thisYear')}
              </p>
            </div>
          </div>
        </div>

        {/* Current Loan */}
        <div className='bg-surface dark:bg-surface rounded-lg shadow p-6'>
          <div className='flex items-center'>
            <div className='flex-shrink-0 bg-blue-100 dark:bg-blue-900 rounded-md p-3'>
              <FiCreditCard className='h-6 w-6 text-blue-600 dark:text-blue-400' />
            </div>
            <div className='ml-4'>
              <p className='text-sm font-medium text-fg-muted dark:text-fg-subtle'>
                {t('currentLoan')}
              </p>
              <p className='text-2xl font-semibold text-fg dark:text-fg'>
                {formatCurrency(memberStats.loanBalance)}
              </p>
              <p className='text-sm text-fg-muted dark:text-fg-subtle'>
                {t('of')} {formatCurrency(memberStats.currentLoan)}
              </p>
            </div>
          </div>
        </div>

        {/* Monthly Contribution */}
        <div className='bg-surface dark:bg-surface rounded-lg shadow p-6'>
          <div className='flex items-center'>
            <div className='flex-shrink-0 bg-purple-100 dark:bg-purple-900 rounded-md p-3'>
              <FiCalendar className='h-6 w-6 text-purple-600 dark:text-purple-400' />
            </div>
            <div className='ml-4'>
              <p className='text-sm font-medium text-fg-muted dark:text-fg-subtle'>
                {t('monthlyContribution')}
              </p>
              <p className='text-2xl font-semibold text-fg dark:text-fg'>
                {formatCurrency(memberStats.monthlyContribution)}
              </p>
              <p className='text-sm text-fg-muted dark:text-fg-subtle'>
                {t('nextDue')}: {formatDate(memberStats.nextPaymentDue)}
              </p>
            </div>
          </div>
        </div>

        {/* Contributions Count */}
        <div className='bg-surface dark:bg-surface rounded-lg shadow p-6'>
          <div className='flex items-center'>
            <div className='flex-shrink-0 bg-yellow-100 dark:bg-yellow-900 rounded-md p-3'>
              <FiTrendingUp className='h-6 w-6 text-yellow-600 dark:text-yellow-400' />
            </div>
            <div className='ml-4'>
              <p className='text-sm font-medium text-fg-muted dark:text-fg-subtle'>
                {t('totalContributions')}
              </p>
              <p className='text-2xl font-semibold text-fg dark:text-fg'>
                {memberStats.totalContributions}
              </p>
              <p className='text-sm text-fg-muted dark:text-fg-subtle'>{t('thisYear')}</p>
            </div>
          </div>
        </div>
      </div>

      {/* Quick Actions */}
      <div className='bg-surface dark:bg-surface rounded-lg shadow'>
        <div className='px-6 py-4 border-b border-border dark:border-border'>
          <h2 className='text-lg font-medium text-fg dark:text-fg'>{t('quickActions')}</h2>
        </div>
        <div className='p-6'>
          <div className='grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4'>
            <button
              onClick={() => navigate('/dashboard/savings')}
              className='flex items-center justify-center px-4 py-3 bg-green-600 hover:bg-green-700 text-white rounded-md transition-colors'
            >
              <FiBriefcase className='mr-2 h-5 w-5' />
              {t('makeDeposit')}
            </button>
            <button
              onClick={() => navigate('/dashboard/loans')}
              className='flex items-center justify-center px-4 py-3 bg-blue-600 hover:bg-blue-700 text-white rounded-md transition-colors'
            >
              <FiCreditCard className='mr-2 h-5 w-5' />
              {t('applyLoan')}
            </button>
            <Link
              to='/dashboard/savings'
              className='flex items-center justify-center px-4 py-3 bg-purple-600 hover:bg-purple-700 text-white rounded-md transition-colors'
            >
              <FiTrendingUp className='mr-2 h-5 w-5' />
              {t('viewSavings')}
            </Link>
            <button
              onClick={() => navigate('/dashboard/member')}
              className='flex items-center justify-center px-4 py-3 bg-surface-3 hover:bg-surface-2 text-white rounded-md transition-colors'
            >
              <FiUser className='mr-2 h-5 w-5' />
              {t('updateProfile')}
            </button>
          </div>
        </div>
      </div>

      <div className='grid grid-cols-1 lg:grid-cols-2 gap-6'>
        {/* Recent Transactions */}
        <div className='bg-surface dark:bg-surface rounded-lg shadow'>
          <div className='px-6 py-4 border-b border-border dark:border-border'>
            <h2 className='text-lg font-medium text-fg dark:text-fg'>{t('recentTransactions')}</h2>
          </div>
          <div className='divide-y divide-border dark:divide-border'>
            {recentTransactions.map(transaction => (
              <div key={transaction.id} className='px-6 py-4'>
                <div className='flex items-center justify-between'>
                  <div>
                    <p className='text-sm font-medium text-fg dark:text-fg'>
                      {transaction.description}
                    </p>
                    <p className='text-sm text-fg-muted dark:text-fg-subtle'>
                      {formatDate(transaction.date)}
                    </p>
                  </div>
                  <div className='text-right'>
                    <p
                      className={`text-sm font-medium ${
                        transaction.type === 'deposit'
                          ? 'text-green-600 dark:text-green-400'
                          : transaction.type === 'withdrawal'
                            ? 'text-blue-600 dark:text-blue-400'
                            : 'text-orange-600 dark:text-orange-400'
                      }`}
                    >
                      {transaction.type === 'deposit'
                        ? '+'
                        : transaction.type === 'withdrawal'
                          ? '-'
                          : '-'}
                      {formatCurrency(transaction.amount)}
                    </p>
                  </div>
                </div>
              </div>
            ))}
          </div>
          <div className='px-6 py-3 border-t border-border dark:border-border'>
            <Link
              to='/dashboard/savings'
              className='text-sm text-primary dark:text-sidebar-fg-muted hover:text-primary-hover dark:hover:text-sidebar-fg-muted'
            >
              {t('viewAllTransactions')} →
            </Link>
          </div>
        </div>

        {/* Upcoming Activities */}
        <div className='bg-surface dark:bg-surface rounded-lg shadow'>
          <div className='px-6 py-4 border-b border-border dark:border-border'>
            <h2 className='text-lg font-medium text-fg dark:text-fg'>{t('upcomingActivities')}</h2>
          </div>
          <div className='divide-y divide-border dark:divide-border'>
            {upcomingActivities.map(activity => (
              <div key={activity.id} className='px-6 py-4'>
                <div className='flex items-start'>
                  <div
                    className={`flex-shrink-0 rounded-md p-2 ${
                      activity.type === 'payment'
                        ? 'bg-red-100 dark:bg-red-900'
                        : 'bg-primary-subtle dark:bg-sidebar'
                    }`}
                  >
                    {activity.type === 'payment' ? (
                      <FiCalendar className='h-5 w-5 text-red-600 dark:text-red-400' />
                    ) : (
                      <FiBell className='h-5 w-5 text-primary dark:text-sidebar-fg-muted' />
                    )}
                  </div>
                  <div className='ml-4'>
                    <p className='text-sm font-medium text-fg dark:text-fg'>{activity.title}</p>
                    <p className='text-sm text-fg-muted dark:text-fg-subtle'>
                      {t('due')}: {formatDate(activity.dueDate)}
                      {activity.time && ` at ${activity.time}`}
                    </p>
                    {activity.amount && (
                      <p className='text-sm font-medium text-red-600 dark:text-red-400'>
                        {formatCurrency(activity.amount)}
                      </p>
                    )}
                  </div>
                </div>
              </div>
            ))}
          </div>
          <div className='px-6 py-3 border-t border-border dark:border-border'>
            <button
              onClick={() => navigate('/dashboard/savings')}
              className='text-sm text-primary dark:text-sidebar-fg-muted hover:text-primary-hover dark:hover:text-sidebar-fg-muted'
            >
              {t('viewAllActivities')} →
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default MemberDashboard;
