import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useParams } from 'react-router';
import { get } from '../../api/client';
import type { Statement } from '../../api/types';
import { Button, ErrorMessage, Loading, TextField } from '../../components/ui';
import { formatRwf } from '../../lib/money';
import { useGroup } from '../groups/GroupLayout';
import { MemberSavings } from './MemberSavings';

/** "My savings": what every member sees about themselves (spec 18.3 member screens). */
export function MySavingsPage() {
  const { t } = useTranslation();
  const group = useGroup();
  return <MemberSavings memberId={group.myMemberId} title={t('savings.title')} />;
}

/** An officer looking at one member's savings, from the member list. */
export function MemberSavingsPage() {
  const { t } = useTranslation();
  const group = useGroup();
  const { memberId = '' } = useParams();
  return (
    <>
      <Link to={`/groups/${group.groupId}/members`} className="mb-3 inline-flex min-h-11 items-center font-semibold text-brand underline">
        {t('savings.back')}
      </Link>
      <MemberSavings memberId={memberId} title={t('groups.nav.savings')} />
    </>
  );
}

/** Spec 15.1 member statement: opening balance, every entry, closing balance, for a period. */
export function StatementPage() {
  const { t, i18n } = useTranslation();
  const group = useGroup();
  const { memberId = '' } = useParams();
  const [range, setRange] = useState<{ from: string; to: string } | null>(null);
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const query = range ? `?from=${range.from}&to=${range.to}` : '';
  const statement = useQuery({
    queryKey: ['statement', group.groupId, memberId, query],
    queryFn: () => get<Statement>(`/groups/${group.groupId}/members/${memberId}/statement${query}`),
  });
  const date = (iso: string) => new Date(`${iso}T00:00:00`).toLocaleDateString(i18n.language);

  return (
    <div className="flex flex-col gap-4">
      <h2 className="text-lg font-bold">
        {t('statement.title')}
        {statement.data?.fullName ? ` · ${statement.data.fullName}` : ''}
      </h2>
      <form
        className="flex flex-wrap items-end gap-2"
        onSubmit={(event) => {
          event.preventDefault();
          if (from && to) {
            setRange({ from, to });
          }
        }}
      >
        <div className="min-w-36 flex-1">
          <TextField label={t('statement.from')} type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
        </div>
        <div className="min-w-36 flex-1">
          <TextField label={t('statement.to')} type="date" value={to} onChange={(e) => setTo(e.target.value)} />
        </div>
        <Button type="submit" variant="secondary" disabled={!from || !to}>
          {t('statement.show')}
        </Button>
      </form>
      {statement.isPending && <Loading />}
      <ErrorMessage error={statement.error} />
      {statement.data && (
        <p className="text-sm text-muted">
          {date(statement.data.from)} – {date(statement.data.to)}
        </p>
      )}
      {statement.data?.buckets.map((bucket) => (
        <section key={bucket.bucketId} className="overflow-x-auto rounded-xl border border-line bg-card p-3">
          <h3 className="mb-2 font-bold">{bucket.name}</h3>
          <table className="w-full min-w-[32rem] text-sm">
            <thead>
              <tr className="text-left text-muted">
                <th scope="col" className="py-1">{t('statement.date')}</th>
                <th scope="col">{t('statement.details')}</th>
                <th scope="col" className="text-right">{t('statement.in')}</th>
                <th scope="col" className="text-right">{t('statement.out')}</th>
                <th scope="col" className="text-right">{t('statement.balance')}</th>
              </tr>
            </thead>
            <tbody>
              <tr className="font-semibold">
                <td colSpan={4} className="py-1">{t('statement.opening')}</td>
                <td className="text-right">{formatRwf(bucket.opening)}</td>
              </tr>
              {bucket.entries.length === 0 && (
                <tr>
                  <td colSpan={5} className="py-2 text-muted">{t('statement.noEntries')}</td>
                </tr>
              )}
              {bucket.entries.map((entry) => (
                <tr key={entry.journalId + entry.direction} className="border-t border-line">
                  <td className="py-1">{date(entry.businessDate)}</td>
                  <td>
                    {entry.description}
                    {entry.externalRef && <span className="block font-mono text-xs text-muted">{entry.externalRef}</span>}
                  </td>
                  {/* Savings are a credit-normal account: credits add to the member's balance. */}
                  <td className="text-right">{entry.direction === 'CREDIT' ? formatRwf(entry.amount) : ''}</td>
                  <td className="text-right">{entry.direction === 'DEBIT' ? formatRwf(entry.amount) : ''}</td>
                  <td className="text-right">{formatRwf(entry.balanceAfter)}</td>
                </tr>
              ))}
              <tr className="border-t border-line font-semibold">
                <td colSpan={4} className="py-1">{t('statement.closing')}</td>
                <td className="text-right">{formatRwf(bucket.closing)}</td>
              </tr>
            </tbody>
          </table>
        </section>
      ))}
    </div>
  );
}
