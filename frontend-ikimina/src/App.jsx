import React, { useEffect, lazy, Suspense } from 'react';
import { Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { MainLayout } from './layouts/MainLayout';
import { LoginForm } from './features/auth/LoginForm';
import { RegisterForm } from './features/auth/RegisterForm';
import { useAppSelector } from './app/hooks';
import { selectIsAuthenticated, selectUserRole } from './features/auth/authSlice';
import { GroupProvider } from './contexts/GroupContext';
import { AppProvider } from './contexts/AppContext';

// Route components are code-split: a member signing in on a slow connection
// downloads the login screen, not the admin dashboards and the spreadsheet
// library too.
const DashboardLayout = lazy(() => import('./features/dashboard/DashboardLayout'));
const DashboardHome = lazy(() => import('./features/dashboard/DashboardHome'));
const MemberDashboard = lazy(() => import('./features/dashboard/MemberDashboard'));
const LoansPage = lazy(() => import('./features/loans/LoansPage'));
const MembersPage = lazy(() => import('./features/members/MembersPage'));
const ReportsPage = lazy(() => import('./features/reports/ReportsPage'));
const SettingsPage = lazy(() => import('./features/settings/SettingsPage'));
const GroupsPage = lazy(() => import('./features/groups/GroupsPage'));
const SavingsPage = lazy(() => import('./features/savings/SavingsPage'));
const SavingsDistributionPage = lazy(
  () => import('./features/savings/components/SavingsDistributionPage')
);
const SuperAdminDashboard = lazy(() => import('./features/admin/components/SuperAdminDashboard'));
const GroupManagement = lazy(() => import('./features/admin/components/GroupManagement'));
const SubscriptionManagement = lazy(
  () => import('./features/admin/components/SubscriptionManagement')
);

const RouteFallback = () => (
  <div className='flex items-center justify-center h-64' role='status' aria-live='polite'>
    <div className='h-8 w-8 animate-spin rounded-full border-2 border-primary border-t-transparent' />
    <span className='sr-only'>Loading</span>
  </div>
);
const SUPER_ADMIN = 'ROLE_SUPER_ADMIN';
const GROUP_ADMIN = 'ROLE_GROUP_ADMIN';

// Scroll to top on route change
const ScrollToTop = () => {
  const { pathname } = useLocation();

  useEffect(() => {
    window.scrollTo(0, 0);
  }, [pathname]);

  return null;
};

// Route guard. `allowedRoles` accepts a list so a route can be shared by
// several roles; omit it to require authentication only.
// This is a UX guard, not a security boundary - the backend re-checks on
// every request.
const ProtectedRoute = ({ children, allowedRoles }) => {
  const isAuthenticated = useAppSelector(selectIsAuthenticated);
  const role = useAppSelector(selectUserRole);
  const location = useLocation();

  if (!isAuthenticated) {
    return <Navigate to='/login' state={{ from: location }} replace />;
  }

  if (allowedRoles && !allowedRoles.includes(role)) {
    return <Navigate to='/dashboard' replace />;
  }

  return <>{children}</>;
};

const PublicRoute = ({ children }) => {
  const isAuthenticated = useAppSelector(selectIsAuthenticated);

  if (isAuthenticated) {
    return <Navigate to='/dashboard' replace />;
  }

  return <>{children}</>;
};

const App = () => {
  return (
    <AppProvider>
      <GroupProvider>
        <ScrollToTop />
        <Suspense fallback={<RouteFallback />}>
          <Routes>
            <Route path='/' element={<Navigate to='/login' replace />} />

            {/* Public routes */}
            <Route
              path='/login'
              element={
                <PublicRoute>
                  <LoginForm />
                </PublicRoute>
              }
            />
            <Route
              path='/register'
              element={
                <PublicRoute>
                  <RegisterForm />
                </PublicRoute>
              }
            />

            {/* Authenticated routes */}
            <Route
              element={
                <ProtectedRoute>
                  <MainLayout />
                </ProtectedRoute>
              }
            >
              <Route path='dashboard' element={<DashboardLayout />}>
                <Route index element={<DashboardHome />} />
                <Route path='member' element={<MemberDashboard />} />
                <Route path='savings' element={<SavingsPage />} />
                <Route path='loans' element={<LoansPage />} />
                <Route path='settings' element={<SettingsPage />} />

                {/*
                  Group-admin and above.

                  Members and Reports read the whole group - /api/users/group/{id}
                  and /api/savings/groups/{id}, both of which require
                  canAdministerGroup. They were reachable by any signed-in user,
                  so a member landing here got a 403 from the API instead of
                  being sent somewhere useful. The server was right to refuse;
                  the route is what was wrong.
                */}
                <Route
                  path='members'
                  element={
                    <ProtectedRoute allowedRoles={[SUPER_ADMIN, GROUP_ADMIN]}>
                      <MembersPage />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path='reports'
                  element={
                    <ProtectedRoute allowedRoles={[SUPER_ADMIN, GROUP_ADMIN]}>
                      <ReportsPage />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path='savings-distribution'
                  element={
                    <ProtectedRoute allowedRoles={[SUPER_ADMIN, GROUP_ADMIN]}>
                      <SavingsDistributionPage />
                    </ProtectedRoute>
                  }
                />

                {/* Super-admin only */}
                <Route
                  path='admin'
                  element={
                    <ProtectedRoute allowedRoles={[SUPER_ADMIN]}>
                      <SuperAdminDashboard />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path='admin/groups'
                  element={
                    <ProtectedRoute allowedRoles={[SUPER_ADMIN]}>
                      <GroupManagement />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path='admin/subscriptions'
                  element={
                    <ProtectedRoute allowedRoles={[SUPER_ADMIN]}>
                      <SubscriptionManagement />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path='groups'
                  element={
                    <ProtectedRoute allowedRoles={[SUPER_ADMIN]}>
                      <GroupsPage />
                    </ProtectedRoute>
                  }
                />
              </Route>
            </Route>

            <Route path='*' element={<Navigate to='/' replace />} />
          </Routes>
        </Suspense>
      </GroupProvider>
    </AppProvider>
  );
};

export default App;
