import React, { useEffect } from 'react';
import { Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { Provider } from 'react-redux';
import { store } from './app/store';
import { MainLayout } from './layouts/MainLayout';
import { LoginForm } from './features/auth/LoginForm';
import { RegisterForm } from './features/auth/RegisterForm';
import DashboardLayout from './features/dashboard/DashboardLayout';
import DashboardHome from './features/dashboard/DashboardHome';
import MemberDashboard from './features/dashboard/MemberDashboard';
import LoansPage from './features/loans/LoansPage';
import MembersPage from './features/members/MembersPage';
import ReportsPage from './features/reports/ReportsPage';
import SettingsPage from './features/settings/SettingsPage';
import GroupsPage from './features/groups/GroupsPage';
import { useAppSelector } from './app/hooks';
import { selectIsAuthenticated, selectCurrentUser } from './features/auth/authSlice';
import SavingsPage from './features/savings/SavingsPage';
import SavingsDistributionPage from './features/savings/components/SavingsDistributionPage';
import SuperAdminDashboard from './features/admin/components/SuperAdminDashboard';
import GroupManagement from './features/admin/components/GroupManagement';
import SubscriptionManagement from './features/admin/components/SubscriptionManagement';
import { GroupProvider } from './contexts/GroupContext';
import { AppProvider } from './contexts/AppContext';
import { ToastContainer } from 'react-toastify';
import 'react-toastify/dist/ReactToastify.css';

// Scroll to top on route change
const ScrollToTop = () => {
  const { pathname } = useLocation();

  useEffect(() => {
    window.scrollTo(0, 0);
  }, [pathname]);

  return null;
};

// Protected Route component
const ProtectedRoute = ({ children, requiredRole }) => {
  const isAuthenticated = useAppSelector(selectIsAuthenticated);
  const user = useAppSelector(selectCurrentUser);
  const location = useLocation();
  
  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }
  
  if (requiredRole && user?.role !== requiredRole) {
    return <Navigate to="/dashboard" replace />;
  }
  
  return <>{children}</>;
};

// Public Route component
const PublicRoute = ({ children }) => {
  const isAuthenticated = useAppSelector(selectIsAuthenticated);
  
  if (isAuthenticated) {
    return <Navigate to="/dashboard" replace />;
  }
  
  return <>{children}</>;
};

// Main App component
const App = () => {
  return (
    <Provider store={store}>
      <AppProvider>
        <GroupProvider>
          <ScrollToTop />
          <ToastContainer 
            position="top-right"
            autoClose={5000}
            hideProgressBar={false}
            newestOnTop={false}
            closeOnClick
            rtl={false}
            pauseOnFocusLoss
            draggable
            pauseOnHover
          />
          <Routes>
            <Route path="/" element={<Navigate to="/login" replace />} />
          
          {/* Public Routes */}
          <Route path="/login" element={
            <PublicRoute>
              <LoginForm />
            </PublicRoute>
          } />
          <Route path="/register" element={
            <PublicRoute>
              <RegisterForm />
            </PublicRoute>
          } />
          
          {/* Protected Routes - Wrapped in MainLayout */}
          <Route element={
            <ProtectedRoute>
              <MainLayout />
            </ProtectedRoute>
          }>
            <Route path="dashboard" element={<DashboardLayout />}>
              <Route index element={<DashboardHome />} />
              <Route path="member" element={<MemberDashboard />} />
              <Route path="savings" element={<SavingsPage />} />
              <Route path="savings-distribution" element={<SavingsDistributionPage />} />
              <Route path="admin" element={<SuperAdminDashboard />} />
              <Route path="admin/groups" element={<GroupManagement />} />
              <Route path="admin/subscriptions" element={<SubscriptionManagement />} />
              <Route path="loans" element={<LoansPage />} />
              <Route path="members" element={<MembersPage />} />
              <Route path="reports" element={<ReportsPage />} />
              <Route path="settings" element={<SettingsPage />} />
            </Route>
          </Route>
          
          {/* Super Admin Only Routes */}
          <Route element={
            <ProtectedRoute requiredRole="ROLE_SUPER_ADMIN">
              <MainLayout />
            </ProtectedRoute>
          }>
            <Route path="dashboard/groups" element={<GroupsPage />} />
          </Route>
          
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
        </GroupProvider>
      </AppProvider>
    </Provider>
  );
};

export default App;
