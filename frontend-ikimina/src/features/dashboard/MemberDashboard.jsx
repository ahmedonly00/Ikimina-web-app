import React, { useMemo } from 'react';
import { Link } from 'react-router-dom';
import {
  FiBriefcase,
  FiCreditCard,
  FiTrendingUp,
  FiAlertCircle,
  FiPlusCircle,
  FiFileText,
} from 'react-icons/fi';
import { useSelector } from 'react-redux';
import { useAppContext } from '../../contexts/AppContext';
import { selectCurrentUser, selectCurrentGroup } from '../auth/authSlice';
import { useGetSavingsQuery, useGetLoansQuery, useGetFinesQuery } from '../../app/api/apiSlice';
import QueryError from '../../components/ui/QueryError';
import PageShell from '../../components/ui/PageShell';

/**
 * A member's own view of their position.
 *
 * Every figure on this screen used to be invented: a total of RWF 1,250,000,
 * a "current loan" of RWF 350,000 against a group that had no loans at all, a
 * monthly contribution due in January 2024, and a list of transactions with
 * hardcoded 2023/2024 dates. This is the screen belonging to the person whose
 * money it is, so showing them somebody's placeholder numbers is the worst
 * place in the app to do it.
 *
 * It now reads the member's own savings, loans and fines. Sections with no
 * data source say so instead of being filled in.
 */
const NO_ROWS = [];

const StatCard = ({ icon: Icon, label, value, hint, tone = 'primary' }) => {
  const tones = {
    primary: 'bg-primary-subtle text-primary',
    success: 'bg-success-subtle text-success',
    info: 'bg-info-subtle text-info',
    warning: 'bg-warning-subtle text-warning',
  };
  return (
    <div className='card p-5'>
      <div className='flex items-center gap-3'>
        <span
          className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-lg ${tones[tone]}`}
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
};

const MemberDashboard = () => {
  const { t, formatCurrency, formatDate } = useAppContext();
  const user = useSelector(selectCurrentUser);
  const currentGroup = useSelector(selectCurrentGroup);
  const userId = user?.id;

  const savingsQuery = useGetSavingsQuery(userId, { skip: !userId });
  const loansQuery = useGetLoansQuery(userId, { skip: !userId });
  const finesQuery = useGetFinesQuery(userId, { skip: !userId });

  const savings = savingsQuery.data ?? NO_ROWS;
  const loans = loansQuery.data ?? NO_ROWS;
  const fines = finesQuery.data ?? NO_ROWS;

  const totals = useMemo(() => {
    let ubwizigame = 0;
    let ingoboka = 0;
    for (const s of savings) {
      const amount = Number(s.amount) || 0;
      if (s.type === 'UBWIZIGAME') ubwizigame += amount;
      else if (s.type === 'INGOBOKA') ingoboka += amount;
    }
    return { ubwizigame, ingoboka, all: ubwizigame + ingoboka };
  }, [savings]);

  // Anything not yet settled is what a member actually wants to see.
  const outstandingLoan = useMemo(
    () =>
      loans
        .filter(l => ['APPROVED', 'ACTIVE', 'DISBURSED'].includes(String(l.status).toUpperCase()))
        .reduce((sum, l) => sum + (Number(l.amount) || 0), 0),
    [loans]
  );

  const unpaidFines = useMemo(
    () =>
      fines
        .filter(f => !f.paid && String(f.status).toUpperCase() !== 'PAID')
        .reduce((sum, f) => sum + (Number(f.amount) || 0), 0),
    [fines]
  );

  const recent = useMemo(
    () => [...savings].sort((a, b) => String(b.date).localeCompare(String(a.date))).slice(0, 6),
    [savings]
  );

  const loading = savingsQuery.isLoading;
  const dash = '—';

  return (
    <PageShell
      // dashboardWelcome is a whole sentence, so it cannot take a name appended.
      title={user?.firstName ? `${t('welcome')}, ${user.firstName}` : t('dashboard')}
      subtitle={currentGroup?.name || undefined}
    >
      <QueryError
        error={savingsQuery.error || loansQuery.error || finesQuery.error}
        onRetry={() => {
          savingsQuery.refetch();
          loansQuery.refetch();
          finesQuery.refetch();
        }}
        title={t('error')}
      />

      <div className='grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4'>
        <StatCard
          icon={FiBriefcase}
          label={t('totalSavings')}
          value={loading ? dash : formatCurrency(totals.all)}
          hint='Everything you have contributed'
        />
        <StatCard
          icon={FiTrendingUp}
          label='Ubwizigame'
          value={loading ? dash : formatCurrency(totals.ubwizigame)}
          tone='success'
          hint='Your share of the fund'
        />
        <StatCard
          icon={FiCreditCard}
          label='Outstanding loan'
          value={loansQuery.isLoading ? dash : formatCurrency(outstandingLoan)}
          tone='info'
          hint={loans.length === 0 ? 'You have no loans' : `${loans.length} on record`}
        />
        <StatCard
          icon={FiAlertCircle}
          label='Unpaid fines'
          value={finesQuery.isLoading ? dash : formatCurrency(unpaidFines)}
          tone='warning'
          hint={fines.length === 0 ? 'No fines' : `${fines.length} on record`}
        />
      </div>

      <div className='mt-6 grid grid-cols-1 gap-6 lg:grid-cols-3'>
        <section className='card lg:col-span-2'>
          <header className='border-b border-border px-5 py-4'>
            <h2>Your contributions</h2>
            <p className='mt-0.5 text-sm text-fg-muted'>Most recent first</p>
          </header>

          {recent.length > 0 ? (
            <div className='overflow-x-auto'>
              <table className='min-w-full text-sm'>
                <thead className='table-head'>
                  <tr>
                    <th className='px-5 py-3 text-left'>Date</th>
                    <th className='px-5 py-3 text-left'>Type</th>
                    <th className='px-5 py-3 text-right'>Amount</th>
                  </tr>
                </thead>
                <tbody>
                  {recent.map(s => (
                    <tr key={s.id} className='table-row'>
                      <td className='tabular px-5 py-3 text-fg'>{formatDate(s.date)}</td>
                      <td className='px-5 py-3'>
                        <span className={s.type === 'UBWIZIGAME' ? 'badge-info' : 'badge-neutral'}>
                          {s.type === 'UBWIZIGAME' ? 'Ubwizigame' : 'Ingoboka'}
                        </span>
                      </td>
                      <td className='tabular px-5 py-3 text-right font-medium text-fg'>
                        {formatCurrency(s.amount)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <div className='px-5 py-10 text-center'>
              <p className='text-sm text-fg-muted'>
                {loading ? 'Loading…' : 'You have no recorded contributions yet.'}
              </p>
            </div>
          )}
        </section>

        <div className='space-y-6'>
          <section className='card'>
            <header className='border-b border-border px-5 py-4'>
              <h2>{t('quickActions')}</h2>
            </header>
            <div className='grid grid-cols-1 gap-2 p-4'>
              <Link to='/dashboard/savings' className='btn-secondary justify-start'>
                <FiPlusCircle className='h-4 w-4' aria-hidden='true' />
                {t('viewSavings')}
              </Link>
              <Link to='/dashboard/loans' className='btn-secondary justify-start'>
                <FiFileText className='h-4 w-4' aria-hidden='true' />
                {t('loans')}
              </Link>
            </div>
          </section>

          {/*
            Deliberately not an invented schedule. The previous version listed
            a contribution due on 15 January 2024 and a group meeting on the
            20th, neither of which came from anywhere.
          */}
          <section className='card'>
            <header className='border-b border-border px-5 py-4'>
              <h2>{t('upcomingPayments')}</h2>
            </header>
            <div className='px-5 py-10 text-center'>
              <p className='text-sm text-fg-muted'>
                Contribution scheduling is not implemented yet, so there is nothing to show here.
              </p>
            </div>
          </section>
        </div>
      </div>
    </PageShell>
  );
};

export default MemberDashboard;
