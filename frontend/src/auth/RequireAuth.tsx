import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router';
import { Loading } from '../components/ui';
import { useSession } from './session';

/** Sends signed-out visitors to sign-in, remembering where they were going (e.g. an invitation link). */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { status, signedOutDeliberately } = useSession();
  const location = useLocation();
  if (status === 'restoring') {
    return <Loading />;
  }
  if (status === 'signedOut') {
    // After a deliberate sign-out, do not remember the page: on a shared phone the next person
    // must not be sent to the previous person's screens.
    const state = signedOutDeliberately ? null : { from: location.pathname + location.search };
    return <Navigate to="/login" replace state={state} />;
  }
  return <>{children}</>;
}

/** Signed-in people have no business on the sign-in pages. */
export function GuestOnly({ children }: { children: ReactNode }) {
  const { status } = useSession();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from ?? '/';
  if (status === 'restoring') {
    return <Loading />;
  }
  return status === 'signedIn' ? <Navigate to={from} replace /> : <>{children}</>;
}
