import React, { useState } from 'react';
import { Outlet, Link, useLocation } from 'react-router-dom';
import { 
  FiHome, 
  FiDollarSign, 
  FiCreditCard, 
  FiUsers, 
  FiPieChart, 
  FiSettings, 
  FiLogOut,
  FiMenu,
  FiLayers,
  FiBell
} from 'react-icons/fi';
import { useAppDispatch, useAppSelector } from '../../app/hooks';
import { logout, selectCurrentUser, selectCurrentGroup } from '../auth/authSlice';
import { GroupSelector } from '../groups/GroupSelector';

const DashboardLayout = () => {
  const [isSidebarOpen, setIsSidebarOpen] = useState(true);
  const location = useLocation();
  const dispatch = useAppDispatch();

  const navigation = [
    { name: 'Dashboard', href: '/dashboard', icon: FiHome },
    { name: 'Savings', href: '/dashboard/savings', icon: FiDollarSign },
    { name: 'Loans', href: '/dashboard/loans', icon: FiCreditCard },
    { name: 'Members', href: '/dashboard/members', icon: FiUsers },
    { name: 'Reports', href: '/dashboard/reports', icon: FiPieChart },
    { name: 'Groups', href: '/dashboard/groups', icon: FiLayers },
    { name: 'Settings', href: '/dashboard/settings', icon: FiSettings },
  ];

  const handleLogout = () => {
    dispatch(logout());
  };

  const currentUser = useAppSelector(selectCurrentUser);
  const currentGroup = useAppSelector(selectCurrentGroup);

  return (
    <div className="fixed inset-0 flex bg-gray-50 overflow-hidden m-0 p-0">
      {/* Mobile sidebar backdrop */}
      {!isSidebarOpen && (
        <div 
          className="fixed inset-0 z-20 bg-gray-900 bg-opacity-50 lg:hidden"
          onClick={() => setIsSidebarOpen(true)}
        />
      )}

      {/* Sidebar */}
      <div 
        className={`fixed inset-y-0 left-0 z-30 w-64 transform ${isSidebarOpen ? 'translate-x-0' : '-translate-x-full'} bg-indigo-700 transition duration-300 ease-in-out lg:translate-x-0 lg:static lg:flex-shrink-0 h-screen lg:w-72 xl:w-64`}
      >
        <div className="flex flex-col h-full">
          {/* Mobile menu button */}
          <div className="flex items-center justify-between px-3 sm:px-4 py-3 border-b border-indigo-800">
            <div className="flex items-center">
              <button
                type="button"
                className="text-indigo-200 hover:text-white p-1"
                onClick={() => setIsSidebarOpen(!isSidebarOpen)}
              >
                <span className="sr-only">Open sidebar</span>
                <FiMenu className="h-5 w-5 sm:h-6 sm:w-6" />
              </button>
              <h1 className="ml-2 sm:ml-4 text-sm sm:text-lg font-medium text-white">Ikimina</h1>
            </div>
          </div>
          
          {/* Group Selector */}
          <div className="px-4 py-4 border-b border-indigo-800">
            <GroupSelector />
          </div>
          
          {/* Navigation */}
          <div className="flex-1 flex flex-col overflow-y-auto">
            <nav className="flex-1 px-2 space-y-1 mt-2">
              {navigation.map((item) => (
                <Link
                  key={item.name}
                  to={item.href}
                  className={`flex items-center px-3 sm:px-4 py-2 sm:py-3 text-xs sm:text-sm font-medium rounded-md ${
                    location.pathname === item.href
                      ? 'bg-indigo-800 text-white'
                      : 'text-indigo-100 hover:bg-indigo-600 hover:bg-opacity-75'
                  }`}
                >
                  <item.icon 
                    className={`mr-3 h-5 w-5 ${
                      location.pathname === item.href ? 'text-white' : 'text-indigo-300'
                    }`} 
                  />
                  {item.name}
                </Link>
              ))}
            </nav>
            
            {/* Logout Button */}
            <div className="mt-auto p-4">
              <button
                onClick={handleLogout}
                className="flex items-center w-full px-4 py-3 text-sm font-medium text-left text-indigo-100 rounded-md hover:bg-indigo-600 hover:bg-opacity-75"
              >
                <FiLogOut className="mr-3 h-5 w-5 text-indigo-300" />
                Sign out
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* Main content */}
      <div className="flex flex-col flex-1 min-w-0 overflow-hidden">
        {/* Top navigation */}
        <header className="bg-white shadow-sm flex-shrink-0">
          <div className="flex items-center justify-between px-2 sm:px-4 py-2 sm:py-3 h-full">
            <button
              onClick={() => setIsSidebarOpen(!isSidebarOpen)}
              className="text-gray-500 hover:text-gray-600 lg:hidden p-1"
            >
              <FiMenu className="h-5 w-5 sm:h-6 sm:w-6" />
            </button>
            
            <div className="flex items-center space-x-2 sm:space-x-4">
              <div className="relative">
                <button className="p-1 sm:p-2 text-gray-600 rounded-full hover:text-gray-900">
                  <span className="sr-only">View notifications</span>
                  <div className="w-2 h-2 bg-red-500 rounded-full absolute top-0 right-0"></div>
                  <FiBell className="h-4 w-4 sm:h-6 sm:w-6" />
                </button>
              </div>
              
              <div className="relative hidden sm:block">
                <div className="flex items-center space-x-2">
                  <div className="text-right">
                    <p className="text-xs sm:text-sm font-medium text-gray-700">
                      {currentUser?.firstName} {currentUser?.lastName}
                    </p>
                    <p className="text-xs text-gray-500 capitalize">
                      {currentUser?.role?.toLowerCase()}
                    </p>
                  </div>
                  <div 
                    className="h-6 w-6 sm:h-8 sm:w-8 rounded-full bg-indigo-100 flex items-center justify-center"
                    title={`${currentUser?.firstName} ${currentUser?.lastName}`}
                  >
                    <span className="text-indigo-700 font-medium text-xs sm:text-sm">
                      {currentUser?.firstName?.[0]}{currentUser?.lastName?.[0]}
                    </span>
                  </div>
                </div>
              </div>

              {/* Mobile user avatar only */}
              <div className="sm:hidden">
                <div 
                  className="h-8 w-8 rounded-full bg-indigo-100 flex items-center justify-center"
                  title={`${currentUser?.firstName} ${currentUser?.lastName}`}
                >
                  <span className="text-indigo-700 font-medium text-sm">
                    {currentUser?.firstName?.[0]}{currentUser?.lastName?.[0]}
                  </span>
                </div>
              </div>
            </div>
          </div>
        </header>

        {/* Page content */}
        <main className="flex-1 overflow-y-auto bg-gray-50 min-w-0 w-full h-full">
          <div className="h-full">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  );
};

export default DashboardLayout;
