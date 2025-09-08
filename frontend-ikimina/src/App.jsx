import React, { useEffect } from 'react';
import { Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { Provider } from 'react-redux';
import { store } from './app/store';
import { MainLayout } from './layouts/MainLayout';
import { LoginForm } from './features/auth/LoginForm';
import { RegisterForm } from './features/auth/RegisterForm';
import DashboardLayout from './features/dashboard/DashboardLayout';
import DashboardHome from './features/dashboard/DashboardHome';
import LoansPage from './features/loans/LoansPage';
import MembersPage from './features/members/MembersPage';
import ReportsPage from './features/reports/ReportsPage';
import SettingsPage from './features/settings/SettingsPage';
import GroupsPage from './features/groups/GroupsPage';
import { useAppSelector } from './app/hooks';
import { selectIsAuthenticated } from './features/auth/authSlice';
import { GroupProvider } from './contexts/GroupContext';
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
const ProtectedRoute = ({ children }) => {
  const isAuthenticated = useAppSelector(selectIsAuthenticated);
  const location = useLocation();
  
  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
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
          <Route path="/" element={<MainLayout />}>
            <Route index element={<Navigate to="/login" replace />} />
            <Route path="login" element={
              <PublicRoute>
                <LoginForm />
              </PublicRoute>
            } />
            <Route path="register" element={
              <PublicRoute>
                <RegisterForm />
              </PublicRoute>
            } />
          </Route>
          
          <Route path="/dashboard" element={
            <ProtectedRoute>
              <DashboardLayout />
            </ProtectedRoute>
          }>
            <Route index element={<DashboardHome />} />
            <Route path="savings" element={<div>Savings</div>} />
            <Route path="loans" element={<LoansPage />} />
            <Route path="members" element={<MembersPage />} />
            <Route path="reports" element={<ReportsPage />} />
            <Route path="groups" element={<GroupsPage />} />
            <Route path="settings" element={<SettingsPage />} />
          </Route>
          
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </GroupProvider>
    </Provider>
  );
};

export default App;
