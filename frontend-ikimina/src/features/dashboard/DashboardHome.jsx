import React from 'react';
import { FiArrowUp, FiArrowDown, FiDollarSign, FiUsers, FiCreditCard, FiPieChart } from 'react-icons/fi';

const stats = [
  { 
    name: 'Total Savings', 
    value: '$24,780', 
    change: '+12.5%', 
    changeType: 'increase',
    icon: FiDollarSign 
  },
  { 
    name: 'Active Members', 
    value: '48', 
    change: '+4', 
    changeType: 'increase',
    icon: FiUsers 
  },
  { 
    name: 'Active Loans', 
    value: '12', 
    change: '2.1%', 
    changeType: 'decrease',
    icon: FiCreditCard 
  },
  { 
    name: 'Monthly Contribution', 
    value: '$3,200', 
    change: '+8.2%', 
    changeType: 'increase',
    icon: FiPieChart 
  },
];

const recentActivities = [
  { id: 1, user: 'John Doe', action: 'made a savings deposit of $500', time: '2 minutes ago' },
  { id: 2, user: 'Jane Smith', action: 'applied for a loan of $2,000', time: '1 hour ago' },
  { id: 3, user: 'Robert Johnson', action: 'completed loan repayment of $1,200', time: '3 hours ago' },
  { id: 4, user: 'Sarah Williams', action: 'updated profile information', time: '5 hours ago' },
  { id: 5, user: 'Michael Brown', action: 'made a savings deposit of $1,000', time: '1 day ago' },
];

const upcomingPayments = [
  { id: 1, member: 'Alice Johnson', type: 'Loan Repayment', amount: '$350', dueDate: 'Tomorrow' },
  { id: 2, member: 'David Wilson', type: 'Monthly Savings', amount: '$200', dueDate: 'In 2 days' },
  { id: 3, member: 'Emma Davis', type: 'Loan Repayment', amount: '$450', dueDate: 'In 3 days' },
];

const DashboardHome = () => {
  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Dashboard</h1>
        <p className="mt-1 text-sm text-gray-500">Welcome back! Here's what's happening with your Ikimina group.</p>
      </div>

      {/* Stats Grid */}
      <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-4">
        {stats.map((stat) => (
          <div key={stat.name} className="bg-white overflow-hidden shadow rounded-lg">
            <div className="p-5">
              <div className="flex items-center">
                <div className="flex-shrink-0">
                  <div className="p-3 rounded-md bg-indigo-500 bg-opacity-10">
                    <stat.icon className="h-6 w-6 text-indigo-600" aria-hidden="true" />
                  </div>
                </div>
                <div className="ml-5 w-0 flex-1">
                  <dl>
                    <dt className="text-sm font-medium text-gray-500 truncate">{stat.name}</dt>
                    <dd>
                      <div className="text-lg font-medium text-gray-900">{stat.value}</div>
                    </dd>
                  </dl>
                </div>
              </div>
            </div>
            <div className="bg-gray-50 px-5 py-3">
              <div className="text-sm">
                <span className={`font-medium ${stat.changeType === 'increase' ? 'text-green-600' : 'text-red-600'} flex items-center`}>
                  {stat.changeType === 'increase' ? (
                    <FiArrowUp className="h-4 w-4 mr-1" />
                  ) : (
                    <FiArrowDown className="h-4 w-4 mr-1" />
                  )}
                  {stat.change}
                </span>
                <span className="text-gray-500 ml-2">vs last month</span>
              </div>
            </div>
          </div>
        ))}
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        {/* Recent Activity */}
        <div className="lg:col-span-2">
          <div className="bg-white shadow overflow-hidden sm:rounded-lg">
            <div className="px-4 py-5 sm:px-6 border-b border-gray-200">
              <h3 className="text-lg leading-6 font-medium text-gray-900">Recent Activity</h3>
              <p className="mt-1 max-w-2xl text-sm text-gray-500">Latest actions from group members</p>
            </div>
            <div className="divide-y divide-gray-200">
              {recentActivities.map((activity) => (
                <div key={activity.id} className="px-4 py-4 sm:px-6 hover:bg-gray-50">
                  <div className="flex items-center">
                    <div className="min-w-0 flex-1 flex items-center">
                      <div className="min-w-0 flex-1">
                        <p className="text-sm font-medium text-indigo-600 truncate">
                          {activity.user}
                        </p>
                        <p className="text-sm text-gray-500 truncate">
                          {activity.action}
                        </p>
                      </div>
                    </div>
                    <div className="ml-5 flex-shrink-0">
                      <p className="text-sm text-gray-500">{activity.time}</p>
                    </div>
                  </div>
                </div>
              ))}
            </div>
            <div className="bg-gray-50 px-4 py-4 sm:px-6">
              <a href="#" className="text-sm font-medium text-indigo-600 hover:text-indigo-500">
                View all activity
              </a>
            </div>
          </div>
        </div>

        {/* Upcoming Payments */}
        <div>
          <div className="bg-white shadow overflow-hidden sm:rounded-lg">
            <div className="px-4 py-5 sm:px-6 border-b border-gray-200">
              <h3 className="text-lg leading-6 font-medium text-gray-900">Upcoming Payments</h3>
              <p className="mt-1 max-w-2xl text-sm text-gray-500">Scheduled payments for the next 7 days</p>
            </div>
            <div className="divide-y divide-gray-200">
              {upcomingPayments.map((payment) => (
                <div key={payment.id} className="px-4 py-4 sm:px-6 hover:bg-gray-50">
                  <div className="flex items-center justify-between">
                    <div className="flex-1">
                      <p className="text-sm font-medium text-gray-900">{payment.member}</p>
                      <p className="text-sm text-gray-500">{payment.type}</p>
                    </div>
                    <div className="ml-2 flex-shrink-0 flex">
                      <p className="text-sm font-medium text-gray-900">{payment.amount}</p>
                    </div>
                  </div>
                  <div className="mt-2">
                    <span className="px-2 py-1 text-xs rounded-full bg-yellow-100 text-yellow-800">
                      {payment.dueDate}
                    </span>
                  </div>
                </div>
              ))}
            </div>
            <div className="bg-gray-50 px-4 py-4 sm:px-6">
              <a href="#" className="text-sm font-medium text-indigo-600 hover:text-indigo-500">
                View all payments
              </a>
            </div>
          </div>

          {/* Quick Actions */}
          <div className="mt-6 bg-white shadow overflow-hidden sm:rounded-lg">
            <div className="px-4 py-5 sm:px-6">
              <h3 className="text-lg leading-6 font-medium text-gray-900">Quick Actions</h3>
            </div>
            <div className="px-4 pb-5 sm:px-6">
              <div className="grid grid-cols-2 gap-4">
                <a
                  href="#"
                  className="inline-flex items-center justify-center px-4 py-3 border border-transparent text-sm font-medium rounded-md shadow-sm text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500"
                >
                  Record Payment
                </a>
                <a
                  href="#"
                  className="inline-flex items-center justify-center px-4 py-3 border border-gray-300 text-sm font-medium rounded-md shadow-sm text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500"
                >
                  New Loan
                </a>
                <a
                  href="#"
                  className="inline-flex items-center justify-center px-4 py-3 border border-transparent text-sm font-medium rounded-md text-indigo-700 bg-indigo-100 hover:bg-indigo-200 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500"
                >
                  Add Member
                </a>
                <a
                  href="#"
                  className="inline-flex items-center justify-center px-4 py-3 border border-transparent text-sm font-medium rounded-md text-indigo-700 bg-indigo-100 hover:bg-indigo-200 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500"
                >
                  Generate Report
                </a>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default DashboardHome;
