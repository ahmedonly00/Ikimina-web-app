import { QueryClient, QueryClientProvider, useQuery } from '@tanstack/react-query';
import { lazy, Suspense, useEffect, type ReactNode } from 'react';
import { createBrowserRouter, Navigate, Outlet, RouterProvider } from 'react-router';
import { ApiError, get } from './api/client';
import type { Profile } from './api/types';
import { GuestOnly, RequireAuth } from './auth/RequireAuth';
import { SessionProvider, useSession } from './auth/session';
import { AppShell } from './components/AppShell';
import { Loading } from './components/ui';
import { LoginPage } from './features/auth/LoginPage';
import { ForgotPasswordPage, ResetPasswordPage } from './features/auth/PasswordResetPages';
import { RegisterPage } from './features/auth/RegisterPage';
import { VerifyPhonePage } from './features/auth/VerifyPhonePage';
import { changeLanguage } from './i18n';

// Screens behind sign-in are split out so the first load stays small on slow networks (spec 18.1).
const MyGroupsPage = lazy(() => import('./features/groups/MyGroupsPage').then((m) => ({ default: m.MyGroupsPage })));
const CreateGroupPage = lazy(() => import('./features/groups/CreateGroupPage').then((m) => ({ default: m.CreateGroupPage })));
const GroupLayout = lazy(() => import('./features/groups/GroupLayout').then((m) => ({ default: m.GroupLayout })));
const GroupOverview = lazy(() => import('./features/groups/GroupLayout').then((m) => ({ default: m.GroupOverview })));
const MembersPage = lazy(() => import('./features/groups/MembersPage').then((m) => ({ default: m.MembersPage })));
const InvitePage = lazy(() => import('./features/groups/InvitePage').then((m) => ({ default: m.InvitePage })));
const RulesPage = lazy(() => import('./features/groups/RulesPage').then((m) => ({ default: m.RulesPage })));
const OfficesPage = lazy(() => import('./features/groups/OfficesPage').then((m) => ({ default: m.OfficesPage })));
const AcceptInvitationPage = lazy(() => import('./features/groups/AcceptInvitationPage').then((m) => ({ default: m.AcceptInvitationPage })));
const ProfilePage = lazy(() => import('./features/profile/ProfilePage').then((m) => ({ default: m.ProfilePage })));

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      // Retry network hiccups (spec 18.1), never a refusal: a 4xx will not change on retry.
      retry: (failures, error) => !(error instanceof ApiError && error.status < 500) && failures < 2,
    },
  },
});

/** Once signed in, the user's saved language preference wins over the device default. */
function SyncLanguage() {
  const { status } = useSession();
  const profile = useQuery({ queryKey: ['me'], queryFn: () => get<Profile>('/me'), enabled: status === 'signedIn' });
  const locale = profile.data?.locale;
  useEffect(() => {
    if (locale) {
      changeLanguage(locale);
    }
  }, [locale]);
  return null;
}

function Shell() {
  return (
    <AppShell>
      <SyncLanguage />
      <Suspense fallback={<Loading />}>
        <Outlet />
      </Suspense>
    </AppShell>
  );
}

const guest = (page: ReactNode) => <GuestOnly>{page}</GuestOnly>;
const signedIn = (page: ReactNode) => <RequireAuth>{page}</RequireAuth>;

const router = createBrowserRouter([
  {
    element: <Shell />,
    children: [
      { path: '/login', element: guest(<LoginPage />) },
      { path: '/register', element: guest(<RegisterPage />) },
      { path: '/verify', element: guest(<VerifyPhonePage />) },
      { path: '/forgot-password', element: guest(<ForgotPasswordPage />) },
      { path: '/reset-password', element: guest(<ResetPasswordPage />) },
      { path: '/', element: signedIn(<MyGroupsPage />) },
      { path: '/profile', element: signedIn(<ProfilePage />) },
      { path: '/groups/new', element: signedIn(<CreateGroupPage />) },
      { path: '/invitations/accept', element: signedIn(<AcceptInvitationPage />) },
      {
        path: '/groups/:groupId',
        element: signedIn(<GroupLayout />),
        children: [
          { index: true, element: <GroupOverview /> },
          { path: 'members', element: <MembersPage /> },
          { path: 'invite', element: <InvitePage /> },
          { path: 'rules', element: <RulesPage /> },
          { path: 'offices', element: <OfficesPage /> },
        ],
      },
      { path: '*', element: <Navigate to="/" replace /> },
    ],
  },
]);

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <SessionProvider>
        <RouterProvider router={router} />
      </SessionProvider>
    </QueryClientProvider>
  );
}
