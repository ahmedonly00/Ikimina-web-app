import React from 'react';
import { Link } from 'react-router-dom';
import {
  FiArrowUp,
  FiArrowDown,
  FiDollarSign,
  FiUsers,
  FiCreditCard,
  FiPieChart,
} from 'react-icons/fi';
import { useAppContext } from '../../contexts/AppContext';
import { useAppSelector } from '../../app/hooks';
import { selectCurrentUser } from '../auth/authSlice';
import MemberDashboard from './MemberDashboard';

const DashboardHome = () => {
  const user = useAppSelector(selectCurrentUser);
  const { t } = useAppContext();

  // If user is a member, show the member dashboard
  if (user?.role === 'MEMBER') {
    return <MemberDashboard />;
  }

  const stats = [
    {
      name: t('totalSavings'),
      value: '$24,780',
      change: '+12.5%',
      changeType: 'increase',
      icon: FiDollarSign,
    },
    {
      name: t('totalMembers'),
      value: '48',
      change: '+4',
      changeType: 'increase',
      icon: FiUsers,
    },
    {
      name: t('activeLoans'),
      value: '12',
      change: '2.1%',
      changeType: 'decrease',
      icon: FiCreditCard,
    },
    {
      name: t('monthlySavings'),
      value: '$3,200',
      change: '+8.2%',
      changeType: 'increase',
      icon: FiPieChart,
    },
  ];

  const recentActivities = [
    { id: 1, user: 'John Doe', action: t('savingsDeposit') + ' of $500', time: '2 minutes ago' },
    { id: 2, user: 'Jane Smith', action: t('newLoanRequest') + ' of $2,000', time: '1 hour ago' },
    {
      id: 3,
      user: 'Robert Johnson',
      action: t('loanRepayment') + ' of $1,200',
      time: '3 hours ago',
    },
    { id: 4, user: 'Sarah Williams', action: t('updatedProfile'), time: '5 hours ago' },
    { id: 5, user: 'Michael Brown', action: t('savingsDeposit') + ' of $1,000', time: '1 day ago' },
  ];

  const upcomingPayments = [
    {
      id: 1,
      member: 'Alice Johnson',
      type: t('loanRepayment'),
      amount: '$350',
      dueDate: t('tomorrow'),
    },
    {
      id: 2,
      member: 'David Wilson',
      type: t('monthlySavings'),
      amount: '$200',
      dueDate: t('in2Days'),
    },
    {
      id: 3,
      member: 'Emma Davis',
      type: t('loanRepayment'),
      amount: '$450',
      dueDate: t('in3Days'),
    },
  ];

  return (
    <div className='h-full w-full overflow-y-auto bg-gray-50'>
      <div className='px-2 sm:px-4 lg:px-8 py-4 sm:py-6 lg:py-8 space-y-4 sm:space-y-6 max-w-full xl:max-w-7xl mx-auto'>
        <div>
          <h1 className='text-xl sm:text-2xl font-bold text-gray-900'>{t('dashboard')}</h1>
          <p className='mt-1 text-xs sm:text-sm text-gray-500'>{t('dashboardWelcome')}</p>
        </div>

        {/* Stats Grid */}
        <div className='grid grid-cols-1 gap-3 sm:gap-4 lg:gap-5 sm:grid-cols-2 xl:grid-cols-4'>
          {stats.map(stat => (
            <div key={stat.name} className='bg-white overflow-hidden shadow rounded-lg'>
              <div className='p-3 sm:p-5'>
                <div className='flex items-center'>
                  <div className='flex-shrink-0'>
                    <div className='p-2 sm:p-3 rounded-md bg-indigo-500 bg-opacity-10'>
                      <stat.icon
                        className='h-5 w-5 sm:h-6 sm:w-6 text-indigo-600'
                        aria-hidden='true'
                      />
                    </div>
                  </div>
                  <div className='ml-3 sm:ml-5 w-0 flex-1'>
                    <dl>
                      <dt className='text-xs sm:text-sm font-medium text-gray-500 truncate'>
                        {stat.name}
                      </dt>
                      <dd>
                        <div className='text-base sm:text-lg font-medium text-gray-900'>
                          {stat.value}
                        </div>
                      </dd>
                    </dl>
                  </div>
                </div>
              </div>
              <div className='bg-gray-50 px-3 sm:px-5 py-2 sm:py-3'>
                <div className='text-xs sm:text-sm'>
                  <span
                    className={`font-medium ${stat.changeType === 'increase' ? 'text-green-600' : 'text-red-600'} flex items-center`}
                  >
                    {stat.changeType === 'increase' ? (
                      <FiArrowUp className='h-3 w-3 sm:h-4 sm:w-4 mr-1' />
                    ) : (
                      <FiArrowDown className='h-3 w-3 sm:h-4 sm:w-4 mr-1' />
                    )}
                    {stat.change}
                  </span>
                  <span className='text-gray-500 ml-2 hidden sm:inline'>vs last month</span>
                </div>
              </div>
            </div>
          ))}
        </div>

        <div className='grid grid-cols-1 gap-4 sm:gap-6 lg:grid-cols-1 xl:grid-cols-3'>
          {/* Recent Activity */}
          <div className='xl:col-span-2 lg:col-span-1'>
            <div className='bg-white shadow overflow-hidden sm:rounded-lg'>
              <div className='px-3 sm:px-4 py-3 sm:py-5 border-b border-gray-200'>
                <h3 className='text-base sm:text-lg leading-6 font-medium text-gray-900'>
                  {t('recentActivity')}
                </h3>
                <p className='mt-1 max-w-2xl text-xs sm:text-sm text-gray-500'>
                  {t('latestActivities')}
                </p>
              </div>
              <div className='divide-y divide-gray-200'>
                {recentActivities.map(activity => (
                  <div key={activity.id} className='px-3 sm:px-4 py-3 sm:py-4 hover:bg-gray-50'>
                    <div className='flex flex-col sm:flex-row sm:items-start sm:items-center'>
                      <div className='min-w-0 flex-1 mb-2 sm:mb-0'>
                        <p className='text-xs sm:text-sm font-medium text-indigo-600 truncate'>
                          {activity.user}
                        </p>
                        <p className='text-xs sm:text-sm text-gray-500 truncate mt-1'>
                          {activity.action}
                        </p>
                      </div>
                      <div className='sm:ml-5 flex-shrink-0'>
                        <p className='text-xs sm:text-sm text-gray-500'>{activity.time}</p>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
              <div className='bg-gray-50 px-3 sm:px-4 py-3 sm:py-4'>
                <Link
                  to='/dashboard/loans'
                  className='text-xs sm:text-sm font-medium text-indigo-600 hover:text-indigo-500'
                >
                  {t('viewAll')}
                </Link>
              </div>
            </div>
          </div>

          {/* Upcoming Payments */}
          <div>
            <div className='bg-white shadow overflow-hidden sm:rounded-lg'>
              <div className='px-3 sm:px-4 py-3 sm:py-5 border-b border-gray-200'>
                <h3 className='text-base sm:text-lg leading-6 font-medium text-gray-900'>
                  {t('upcomingPayments')}
                </h3>
                <p className='mt-1 max-w-2xl text-xs sm:text-sm text-gray-500'>
                  {t('scheduledPayments')}
                </p>
              </div>
              <div className='divide-y divide-gray-200'>
                {upcomingPayments.map(payment => (
                  <div key={payment.id} className='px-3 sm:px-4 py-3 sm:py-4 hover:bg-gray-50'>
                    <div className='flex flex-col sm:flex-row sm:items-start sm:items-center'>
                      <div className='flex-1 mb-2 sm:mb-0'>
                        <p className='text-xs sm:text-sm font-medium text-gray-900'>
                          {payment.member}
                        </p>
                        <p className='text-xs sm:text-sm text-gray-500'>{payment.type}</p>
                      </div>
                      <div className='sm:ml-2 flex-shrink-0'>
                        <p className='text-xs sm:text-sm font-medium text-gray-900'>
                          {payment.amount}
                        </p>
                      </div>
                    </div>
                    <div className='mt-2'>
                      <span className='px-2 py-1 text-xs rounded-full bg-yellow-100 text-yellow-800'>
                        {payment.dueDate}
                      </span>
                    </div>
                  </div>
                ))}
              </div>
              <div className='bg-gray-50 px-3 sm:px-4 py-3 sm:py-4'>
                <Link
                  to='/dashboard/loans'
                  className='text-xs sm:text-sm font-medium text-indigo-600 hover:text-indigo-500'
                >
                  {t('viewAll')}
                </Link>
              </div>
            </div>

            {/* Quick Actions */}
            <div className='mt-4 sm:mt-6 bg-white shadow overflow-hidden sm:rounded-lg'>
              <div className='px-3 sm:px-4 py-3 sm:py-5'>
                <h3 className='text-base sm:text-lg leading-6 font-medium text-gray-900'>
                  {t('quickActions')}
                </h3>
              </div>
              <div className='px-3 sm:px-4 pb-4 sm:pb-5'>
                <div className='grid grid-cols-1 sm:grid-cols-2 gap-3 sm:gap-4'>
                  <Link
                    to='/dashboard/loans'
                    className='inline-flex items-center justify-center px-3 sm:px-4 py-2 sm:py-3 border border-transparent text-xs sm:text-sm font-medium rounded-md shadow-sm text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500'
                  >
                    {t('recordPayment')}
                  </Link>
                  <Link
                    to='/dashboard/loans'
                    className='inline-flex items-center justify-center px-3 sm:px-4 py-2 sm:py-3 border border-gray-300 text-xs sm:text-sm font-medium rounded-md shadow-sm text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500'
                  >
                    {t('newLoan')}
                  </Link>
                  <Link
                    to='/dashboard/members'
                    className='inline-flex items-center justify-center px-3 sm:px-4 py-2 sm:py-3 border border-transparent text-xs sm:text-sm font-medium rounded-md text-indigo-700 bg-indigo-100 hover:bg-indigo-200 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500'
                  >
                    {t('addMember')}
                  </Link>
                  <Link
                    to='/dashboard/reports'
                    className='inline-flex items-center justify-center px-3 sm:px-4 py-2 sm:py-3 border border-transparent text-xs sm:text-sm font-medium rounded-md text-indigo-700 bg-indigo-100 hover:bg-indigo-200 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500'
                  >
                    {t('generateReport')}
                  </Link>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default DashboardHome;
