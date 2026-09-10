import React, { useState, useEffect, useRef } from 'react';
import { Outlet, Link, useNavigate } from 'react-router-dom';
import { useAppDispatch, useAppSelector } from '../app/hooks';
import { logout, selectCurrentUser } from '../features/auth/authSlice';
import { useAppContext } from '../contexts/AppContext';
import { FaBell, FaUserCircle, FaCog, FaSignOutAlt, FaChartPie, FaShieldAlt } from 'react-icons/fa';

export const MainLayout = () => {
  const { t } = useAppContext();
  const user = useAppSelector(selectCurrentUser);
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const [showProfileMenu, setShowProfileMenu] = useState(false);
  const [showNotifications, setShowNotifications] = useState(false);
  const profileRef = useRef(null);
  const notificationRef = useRef(null);

  // Close dropdowns when clicking outside
  useEffect(() => {
    const handleClickOutside = event => {
      if (profileRef.current && !profileRef.current.contains(event.target)) {
        setShowProfileMenu(false);
      }
      if (notificationRef.current && !notificationRef.current.contains(event.target)) {
        setShowNotifications(false);
      }
    };

    document.addEventListener('mousedown', handleClickOutside);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, []);

  const handleLogout = () => {
    dispatch(logout());
    navigate('/login');
  };

  return (
    <div className='min-h-screen bg-surface-2 dark:bg-surface'>
      {/* Navigation */}
      <nav className='bg-surface dark:bg-surface shadow-sm'>
        <div className='max-w-7xl mx-auto px-4 sm:px-6 lg:px-8'>
          <div className='flex justify-between h-16'>
            <div className='flex'>
              {/* Logo */}
              <div className='flex-shrink-0'>
                <Link to='/dashboard' className='flex items-center'>
                  <img className='h-8 w-auto' src='/ikimina-logo.png' alt='Ikimina' />
                  <span className='ml-2 text-xl font-bold text-fg dark:text-sidebar-fg'>
                    {t('ikimina')}
                  </span>
                </Link>
              </div>
              <div className='hidden sm:ml-6 sm:flex sm:space-x-8'>
                {/* Dashboard - shown for all users */}
                <Link
                  to='/dashboard'
                  className='bg-primary-subtle border-primary text-primary inline-flex items-center px-1 pt-1 border-b-2 text-sm font-medium'
                >
                  {t('dashboardNav')}
                </Link>

                {/* Member (ROLE_USER) specific menu */}
                {user?.role === 'ROLE_USER' && (
                  <>
                    <Link
                      to='/dashboard/member'
                      className='border-transparent text-fg-muted hover:border-border hover:text-fg inline-flex items-center px-1 pt-1 border-b-2 text-sm font-medium'
                    >
                      My Profile
                    </Link>
                    <Link
                      to='/dashboard/savings'
                      className='border-transparent text-fg-muted hover:border-border hover:text-fg inline-flex items-center px-1 pt-1 border-b-2 text-sm font-medium'
                    >
                      {t('savings')}
                    </Link>
                    <Link
                      to='/dashboard/loans'
                      className='border-transparent text-fg-muted hover:border-border hover:text-fg inline-flex items-center px-1 pt-1 border-b-2 text-sm font-medium'
                    >
                      {t('loans')}
                    </Link>
                    <Link
                      to='/fines'
                      className='border-transparent text-fg-muted hover:border-border hover:text-fg inline-flex items-center px-1 pt-1 border-b-2 text-sm font-medium'
                    >
                      {t('fines')}
                    </Link>
                  </>
                )}

                {/* Group Admin (ROLE_GROUP_ADMIN) specific menu */}
                {user?.role === 'ROLE_GROUP_ADMIN' && (
                  <>
                    <Link
                      to='/dashboard/members'
                      className='border-transparent text-fg-muted hover:border-border hover:text-fg inline-flex items-center px-1 pt-1 border-b-2 text-sm font-medium'
                    >
                      {t('members')}
                    </Link>
                    <Link
                      to='/dashboard/savings'
                      className='border-transparent text-fg-muted hover:border-border hover:text-fg inline-flex items-center px-1 pt-1 border-b-2 text-sm font-medium'
                    >
                      {t('savings')}
                    </Link>
                    <Link
                      to='/dashboard/loans'
                      className='border-transparent text-fg-muted hover:border-border hover:text-fg inline-flex items-center px-1 pt-1 border-b-2 text-sm font-medium'
                    >
                      {t('loans')}
                    </Link>
                    <Link
                      to='/dashboard/savings-distribution'
                      className='border-transparent text-fg-muted hover:border-border hover:text-fg inline-flex items-center px-1 pt-1 border-b-2 text-sm font-medium'
                    >
                      <FaChartPie className='mr-2 h-4 w-4' />
                      Distribution
                    </Link>
                    <Link
                      to='/dashboard/reports'
                      className='border-transparent text-fg-muted hover:border-border hover:text-fg inline-flex items-center px-1 pt-1 border-b-2 text-sm font-medium'
                    >
                      {t('reports')}
                    </Link>
                  </>
                )}

                {/* Super Admin (ROLE_SUPER_ADMIN) specific menu */}
                {user?.role === 'ROLE_SUPER_ADMIN' && (
                  <>
                    <Link
                      to='/dashboard/groups'
                      className='border-transparent text-fg-muted hover:border-border hover:text-fg inline-flex items-center px-1 pt-1 border-b-2 text-sm font-medium'
                    >
                      {t('groups')}
                    </Link>
                    <Link
                      to='/dashboard/members'
                      className='border-transparent text-fg-muted hover:border-border hover:text-fg inline-flex items-center px-1 pt-1 border-b-2 text-sm font-medium'
                    >
                      {t('members')}
                    </Link>
                    <Link
                      to='/dashboard/savings'
                      className='border-transparent text-fg-muted hover:border-border hover:text-fg inline-flex items-center px-1 pt-1 border-b-2 text-sm font-medium'
                    >
                      {t('savings')}
                    </Link>
                    <Link
                      to='/dashboard/loans'
                      className='border-transparent text-fg-muted hover:border-border hover:text-fg inline-flex items-center px-1 pt-1 border-b-2 text-sm font-medium'
                    >
                      {t('loans')}
                    </Link>
                    <Link
                      to='/dashboard/savings-distribution'
                      className='border-transparent text-fg-muted hover:border-border hover:text-fg inline-flex items-center px-1 pt-1 border-b-2 text-sm font-medium'
                    >
                      <FaChartPie className='mr-2 h-4 w-4' />
                      Distribution
                    </Link>
                    <Link
                      to='/dashboard/reports'
                      className='border-transparent text-fg-muted hover:border-border hover:text-fg inline-flex items-center px-1 pt-1 border-b-2 text-sm font-medium'
                    >
                      {t('reports')}
                    </Link>
                    <Link
                      to='/dashboard/admin'
                      className='border-transparent text-purple-500 hover:border-purple-300 hover:text-purple-700 inline-flex items-center px-1 pt-1 border-b-2 text-sm font-medium'
                    >
                      <FaShieldAlt className='mr-2 h-4 w-4' />
                      Super Admin
                    </Link>
                  </>
                )}
              </div>
            </div>
            <div className='hidden sm:ml-6 sm:flex sm:items-center space-x-4'>
              {/* Notifications */}
              <div className='relative' ref={notificationRef}>
                <button
                  type='button'
                  className='bg-surface dark:bg-surface p-1 rounded-full text-fg-subtle hover:text-fg-muted focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary'
                  onClick={() => setShowNotifications(!showNotifications)}
                >
                  <span className='sr-only'>View notifications</span>
                  <FaBell className='h-6 w-6' />
                  <span className='absolute top-0 right-0 block h-2 w-2 rounded-full bg-red-400'></span>
                </button>

                {/* Notifications Dropdown */}
                {showNotifications && (
                  <div className='origin-top-right absolute right-0 mt-2 w-80 rounded-md shadow-lg bg-surface dark:bg-surface ring-1 ring-black ring-opacity-5 focus:outline-none z-50'>
                    <div className='py-1'>
                      <div className='px-4 py-3 border-b border-border dark:border-border'>
                        <h3 className='text-sm font-medium text-fg dark:text-fg'>
                          {t('notifications')}
                        </h3>
                      </div>
                      <div className='px-4 py-3'>
                        <p className='text-sm text-fg-muted dark:text-fg-subtle'>
                          No new notifications
                        </p>
                      </div>
                    </div>
                  </div>
                )}
              </div>

              {/* Profile dropdown */}
              <div className='relative' ref={profileRef}>
                <button
                  type='button'
                  className='bg-surface dark:bg-surface flex text-sm rounded-full focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary'
                  onClick={() => setShowProfileMenu(!showProfileMenu)}
                >
                  <span className='sr-only'>Open user menu</span>
                  <div className='h-8 w-8 rounded-full bg-primary-subtle dark:bg-sidebar flex items-center justify-center'>
                    <span className='text-primary dark:text-sidebar-fg-muted font-medium'>
                      {user?.firstName?.charAt(0) || 'U'}
                    </span>
                  </div>
                </button>

                {/* Profile Dropdown */}
                {showProfileMenu && (
                  <div className='origin-top-right absolute right-0 mt-2 w-48 rounded-md shadow-lg bg-surface dark:bg-surface ring-1 ring-black ring-opacity-5 focus:outline-none z-50'>
                    <div className='py-1'>
                      <div className='px-4 py-3 border-b border-border dark:border-border'>
                        <p className='text-sm font-medium text-fg dark:text-fg'>
                          {user?.firstName && user?.lastName
                            ? `${user.firstName} ${user.lastName}`
                            : 'User'}
                        </p>
                        <p className='text-sm text-fg-muted dark:text-fg-subtle'>
                          {user?.email || 'user@example.com'}
                        </p>
                      </div>
                      <Link
                        to='/dashboard/settings'
                        className='block px-4 py-2 text-sm text-fg dark:text-fg hover:bg-surface-2 dark:hover:bg-surface-2 flex items-center'
                        onClick={() => setShowProfileMenu(false)}
                      >
                        <FaUserCircle className='mr-2' />
                        {t('profile')}
                      </Link>
                      <Link
                        to='/dashboard/settings'
                        className='block px-4 py-2 text-sm text-fg dark:text-fg hover:bg-surface-2 dark:hover:bg-surface-2 flex items-center'
                        onClick={() => setShowProfileMenu(false)}
                      >
                        <FaCog className='mr-2' />
                        {t('settings')}
                      </Link>
                      <button
                        onClick={() => {
                          handleLogout();
                          setShowProfileMenu(false);
                        }}
                        className='block w-full text-left px-4 py-2 text-sm text-fg dark:text-fg hover:bg-surface-2 dark:hover:bg-surface-2 flex items-center'
                      >
                        <FaSignOutAlt className='mr-2' />
                        {t('logout')}
                      </button>
                    </div>
                  </div>
                )}
              </div>
            </div>
            <div className='-mr-2 flex items-center sm:hidden'>
              <button
                type='button'
                className='bg-surface inline-flex items-center justify-center p-2 rounded-md text-fg-subtle hover:text-fg-muted hover:bg-surface-2 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary'
                aria-controls='mobile-menu'
                aria-expanded='false'
              >
                <span className='sr-only'>Open main menu</span>
                <svg
                  className='block h-6 w-6'
                  xmlns='http://www.w3.org/2000/svg'
                  fill='none'
                  viewBox='0 0 24 24'
                  stroke='currentColor'
                  aria-hidden='true'
                >
                  <path
                    strokeLinecap='round'
                    strokeLinejoin='round'
                    strokeWidth='2'
                    d='M4 6h16M4 12h16M4 18h16'
                  />
                </svg>
              </button>
            </div>
          </div>
        </div>

        {/* Mobile menu, show/hide based on menu state. */}
        <div className='sm:hidden' id='mobile-menu'>
          <div className='pt-2 pb-3 space-y-1'>
            {/* Dashboard - shown for all users */}
            <Link
              to='/dashboard'
              className='bg-primary-subtle border-primary text-primary block pl-3 pr-4 py-2 border-l-4 text-base font-medium'
            >
              Dashboard
            </Link>

            {/* Member (ROLE_USER) specific menu */}
            {user?.role === 'ROLE_USER' && (
              <>
                <Link
                  to='/dashboard/member'
                  className='border-transparent text-fg-muted hover:bg-bg hover:border-border hover:text-fg block pl-3 pr-4 py-2 border-l-4 text-base font-medium'
                >
                  My Profile
                </Link>
                <Link
                  to='/dashboard/savings'
                  className='border-transparent text-fg-muted hover:bg-bg hover:border-border hover:text-fg block pl-3 pr-4 py-2 border-l-4 text-base font-medium'
                >
                  Savings
                </Link>
                <Link
                  to='/dashboard/loans'
                  className='border-transparent text-fg-muted hover:bg-bg hover:border-border hover:text-fg block pl-3 pr-4 py-2 border-l-4 text-base font-medium'
                >
                  Loans
                </Link>
                <Link
                  to='/fines'
                  className='border-transparent text-fg-muted hover:bg-bg hover:border-border hover:text-fg block pl-3 pr-4 py-2 border-l-4 text-base font-medium'
                >
                  Fines
                </Link>
              </>
            )}

            {/* Group Admin (ROLE_GROUP_ADMIN) specific menu */}
            {user?.role === 'ROLE_GROUP_ADMIN' && (
              <>
                <Link
                  to='/dashboard/members'
                  className='border-transparent text-fg-muted hover:bg-bg hover:border-border hover:text-fg block pl-3 pr-4 py-2 border-l-4 text-base font-medium'
                >
                  Members
                </Link>
                <Link
                  to='/dashboard/savings'
                  className='border-transparent text-fg-muted hover:bg-bg hover:border-border hover:text-fg block pl-3 pr-4 py-2 border-l-4 text-base font-medium'
                >
                  Savings
                </Link>
                <Link
                  to='/dashboard/loans'
                  className='border-transparent text-fg-muted hover:bg-bg hover:border-border hover:text-fg block pl-3 pr-4 py-2 border-l-4 text-base font-medium'
                >
                  Loans
                </Link>
                <Link
                  to='/dashboard/savings-distribution'
                  className='border-transparent text-fg-muted hover:bg-bg hover:border-border hover:text-fg block pl-3 pr-4 py-2 border-l-4 text-base font-medium'
                >
                  Distribution
                </Link>
                <Link
                  to='/dashboard/reports'
                  className='border-transparent text-fg-muted hover:bg-bg hover:border-border hover:text-fg block pl-3 pr-4 py-2 border-l-4 text-base font-medium'
                >
                  Reports
                </Link>
              </>
            )}

            {/* Super Admin (ROLE_SUPER_ADMIN) specific menu */}
            {user?.role === 'ROLE_SUPER_ADMIN' && (
              <>
                <Link
                  to='/dashboard/groups'
                  className='border-transparent text-fg-muted hover:bg-bg hover:border-border hover:text-fg block pl-3 pr-4 py-2 border-l-4 text-base font-medium'
                >
                  Groups
                </Link>
                <Link
                  to='/dashboard/members'
                  className='border-transparent text-fg-muted hover:bg-bg hover:border-border hover:text-fg block pl-3 pr-4 py-2 border-l-4 text-base font-medium'
                >
                  Members
                </Link>
                <Link
                  to='/dashboard/savings'
                  className='border-transparent text-fg-muted hover:bg-bg hover:border-border hover:text-fg block pl-3 pr-4 py-2 border-l-4 text-base font-medium'
                >
                  Savings
                </Link>
                <Link
                  to='/dashboard/loans'
                  className='border-transparent text-fg-muted hover:bg-bg hover:border-border hover:text-fg block pl-3 pr-4 py-2 border-l-4 text-base font-medium'
                >
                  Loans
                </Link>
                <Link
                  to='/dashboard/savings-distribution'
                  className='border-transparent text-fg-muted hover:bg-bg hover:border-border hover:text-fg block pl-3 pr-4 py-2 border-l-4 text-base font-medium'
                >
                  Distribution
                </Link>
                <Link
                  to='/dashboard/reports'
                  className='border-transparent text-fg-muted hover:bg-bg hover:border-border hover:text-fg block pl-3 pr-4 py-2 border-l-4 text-base font-medium'
                >
                  Reports
                </Link>
                <Link
                  to='/dashboard/admin'
                  className='border-transparent text-purple-500 hover:bg-bg hover:border-border hover:text-purple-700 block pl-3 pr-4 py-2 border-l-4 text-base font-medium'
                >
                  Super Admin
                </Link>
              </>
            )}
          </div>
          <div className='pt-4 pb-3 border-t border-border'>
            <div className='flex items-center px-4'>
              <div className='flex-shrink-0'>
                <div className='h-10 w-10 rounded-full bg-primary-subtle flex items-center justify-center'>
                  <span className='text-primary font-medium'>
                    {user?.firstName?.charAt(0) || 'U'}
                  </span>
                </div>
              </div>
              <div className='ml-3'>
                <div className='text-base font-medium text-fg'>
                  {user?.firstName} {user?.lastName}
                </div>
                <div className='text-sm font-medium text-fg-muted'>{user?.email}</div>
              </div>
            </div>
            <div className='mt-3 space-y-1'>
              <Link
                to='/dashboard/settings'
                className='block px-4 py-2 text-base font-medium text-fg-muted hover:text-fg hover:bg-surface-2'
              >
                Your Profile
              </Link>
              <Link
                to='/dashboard/settings'
                className='block px-4 py-2 text-base font-medium text-fg-muted hover:text-fg hover:bg-surface-2'
              >
                Settings
              </Link>
              <button
                onClick={handleLogout}
                className='block w-full text-left px-4 py-2 text-base font-medium text-fg-muted hover:text-fg hover:bg-surface-2'
              >
                Sign out
              </button>
            </div>
          </div>
        </div>
      </nav>

      {/* Main content */}
      <div className='py-10'>
        <main>
          <div className='max-w-7xl mx-auto sm:px-6 lg:px-8'>
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  );
};

export default MainLayout;
