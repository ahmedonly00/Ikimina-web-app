import React from 'react';
import { Link } from 'react-router-dom';
import {
  // A briefcase, not a dollar sign - the figures are Rwandan francs.
  FiBriefcase,
  FiUsers,
  FiTrendingUp,
  FiCheckCircle,
  FiAlertTriangle,
  FiPlusCircle,
  FiFileText,
  FiUserPlus,
} from 'react-icons/fi';
import { useAppContext } from '../../contexts/AppContext';
import { useAppSelector } from '../../app/hooks';
import { selectCurrentUser, selectCurrentGroup } from '../auth/authSlice';
import { useGetGroupSummaryQuery } from '../../app/api/apiSlice';
import QueryError from '../../components/ui/QueryError';
import MemberDashboard from './MemberDashboard';

/**
 * Group dashboard.
 *
 * Every figure comes from the ledger via /api/ledger/groups/{id}/summary.
 * This screen previously rendered hardcoded values - a total of "$24,780",
 * "48 members", and a Recent Activity list of invented people. On a financial
 * product that is worse than showing nothing, because an admin cannot tell
 * invented numbers from real ones.
 *
 * Where there is no data source yet (activity feed, payment schedule), the
 * section says so rather than filling the space with plausible fiction.
 */
/*
 * Literal class strings rather than `bg-<tone>-subtle`. Tailwind builds its CSS
 * by scanning source text for class names, so an interpolated name matches
 * nothing and the icon would render with no background at all.
 */
const TONES = {
  primary: 'bg-primary-subtle text-primary',
  success: 'bg-success-subtle text-success',
  info: 'bg-info-subtle text-info',
  warning: 'bg-warning-subtle text-warning',
};

const StatCard = ({ icon: Icon, label, value, tone = 'primary', hint }) => (
  <div className='card p-5'>
    <div className='flex items-center gap-3'>
      <span
        className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-lg ${
          TONES[tone] ?? TONES.primary
        }`}
        aria-hidden='true'
      >
        <Icon className='h-5 w-5' />
      </span>
      <div className='min-w-0'>
        <p className='truncate text-sm text-fg-muted'>{label}</p>
        <p className='tabular mt-0.5 text-xl font-semibold text-fg'>{value}</p>
      </div>
    </div>
    {hint && <p className='mt-3 text-xs text-fg-subtle'>{hint}</p>}
  </div>
);

const EmptySection = ({ title, subtitle, note }) => (
  <section className='card'>
    <header className='border-b border-border px-5 py-4'>
      <h2>{title}</h2>
      <p className='mt-0.5 text-sm text-fg-muted'>{subtitle}</p>
    </header>
    <div className='px-5 py-10 text-center'>
      <p className='text-sm text-fg-muted'>{note}</p>
    </div>
  </section>
);

/*
 * Split out of DashboardHome so the summary query is never called
 * conditionally. A hook after an early return breaks the Rules of Hooks -
 * React tracks hooks by call order, and that order would change with the role.
 */
const GroupDashboard = () => {
  const user = useAppSelector(selectCurrentUser);
  const currentGroup = useAppSelector(selectCurrentGroup);
  const { t, formatCurrency } = useAppContext();

  const groupId = currentGroup?.id ?? user?.savingsGroupId;
  const {
    data: summary,
    isLoading,
    error,
    refetch,
  } = useGetGroupSummaryQuery(groupId, { skip: !groupId });

  const dash = '—';
  const money = v => (v === undefined || v === null ? dash : formatCurrency(v));
  const count = v => (v === undefined || v === null ? dash : String(v));

  return (
    <div className='h-full w-full overflow-y-auto bg-bg'>
      <div className='mx-auto max-w-7xl px-4 py-6 sm:px-6 lg:px-8'>
        <header className='mb-6'>
          <h1>{t('dashboard')}</h1>
          <p className='mt-1 text-sm text-fg-muted'>
            {currentGroup?.name ? currentGroup.name : t('dashboardWelcome')}
          </p>
        </header>

        <QueryError error={error} onRetry={refetch} title={t('error')} />

        {!groupId && (
          <div className='card mb-6 p-5'>
            <p className='text-sm text-fg-muted'>
              No group selected. Choose a group to see its figures.
            </p>
          </div>
        )}

        {/* A failed reconciliation is surfaced, not hidden: it means the ledger
            and the member balances disagree and someone needs to look. */}
        {summary && !summary.reconciled && (
          <div
            className='mb-6 flex items-start gap-3 rounded-card border-l-4 border-warning bg-warning-subtle p-4'
            role='alert'
          >
            <FiAlertTriangle className='mt-0.5 shrink-0 text-warning' aria-hidden='true' />
            <p className='text-sm text-fg'>
              This group&apos;s fund balance does not match the sum of its member balances. Check
              the reconciliation report before relying on these totals.
            </p>
          </div>
        )}

        <div className='grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4'>
          <StatCard
            icon={FiBriefcase}
            label={t('totalSavings')}
            value={isLoading ? dash : money(summary?.fundBalance)}
            hint='Held by the group, summed from the ledger'
          />
          <StatCard
            icon={FiTrendingUp}
            label={t('monthlySavings')}
            value={isLoading ? dash : money(summary?.thisMonth)}
            tone='success'
            hint='Net movement this month'
          />
          <StatCard
            icon={FiUsers}
            label={t('activeMembers')}
            value={isLoading ? dash : count(summary?.activeMembers)}
            tone='info'
            hint='On the group roster'
          />
          {/* Distinct from Active Members. These two cards previously both read
              summary.activeMembers under different labels, so the dashboard
              showed the same number twice and implied it meant two things. */}
          <StatCard
            icon={FiCheckCircle}
            label={t('contributingMembers')}
            value={isLoading ? dash : count(summary?.contributingMembers)}
            tone='warning'
            hint='With at least one recorded contribution'
          />
        </div>

        <div className='mt-6 grid grid-cols-1 gap-6 lg:grid-cols-3'>
          <div className='lg:col-span-2'>
            <EmptySection
              title={t('recentActivity')}
              subtitle={t('latestActivities')}
              note='An activity feed is not wired up yet. Contributions appear on the Savings page.'
            />
          </div>

          <div className='space-y-6'>
            <section className='card'>
              <header className='border-b border-border px-5 py-4'>
                <h2>{t('quickActions')}</h2>
              </header>
              <div className='grid grid-cols-1 gap-2 p-4'>
                <Link to='/dashboard/savings' className='btn-secondary justify-start'>
                  <FiPlusCircle className='h-4 w-4' aria-hidden='true' />
                  {t('recordPayment')}
                </Link>
                <Link to='/dashboard/members' className='btn-secondary justify-start'>
                  <FiUserPlus className='h-4 w-4' aria-hidden='true' />
                  {t('addMember')}
                </Link>
                <Link to='/dashboard/reports' className='btn-secondary justify-start'>
                  <FiFileText className='h-4 w-4' aria-hidden='true' />
                  {t('generateReport')}
                </Link>
              </div>
            </section>

            <EmptySection
              title={t('upcomingPayments')}
              subtitle={t('scheduledPayments')}
              note='Payment scheduling is not implemented yet.'
            />
          </div>
        </div>
      </div>
    </div>
  );
};

const DashboardHome = () => {
  const user = useAppSelector(selectCurrentUser);
  // Members get a different screen entirely; admins get the group view.
  return user?.role === 'ROLE_USER' ? <MemberDashboard /> : <GroupDashboard />;
};

export default DashboardHome;
