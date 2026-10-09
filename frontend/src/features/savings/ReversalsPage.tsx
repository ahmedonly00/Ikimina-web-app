import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { get, post } from '../../api/client';
import type { ReversalView } from '../../api/types';
import { useSession } from '../../auth/session';
import { Button, ErrorMessage, Loading, TextField } from '../../components/ui';
import { useGroup } from '../groups/GroupLayout';

/**
 * Reversals waiting for a decision (owner decision, Phase 2): asked by one officer, approved by a
 * different President or Treasurer after re-entering their password. The server refuses
 * self-approval whatever this screen shows.
 */
export function ReversalsPage() {
  const { t, i18n } = useTranslation();
  const group = useGroup();
  const reversals = useQuery({ queryKey: ['reversals', group.groupId], queryFn: () => get<ReversalView[]>(`/groups/${group.groupId}/reversals`) });

  if (reversals.isPending) {
    return <Loading />;
  }
  return (
    <section aria-labelledby="reversals-title" className="flex flex-col gap-3">
      <h2 id="reversals-title" className="text-lg font-bold">{t('reversals.title')}</h2>
      <ErrorMessage error={reversals.error} />
      {reversals.data?.length === 0 && <p className="text-muted">{t('reversals.none')}</p>}
      <ul className="flex flex-col gap-2">
        {reversals.data?.map((r) => (
          <Reversal key={r.requestId} reversal={r} requestedOn={new Date(r.createdAt).toLocaleString(i18n.language)} />
        ))}
      </ul>
    </section>
  );
}

function Reversal({ reversal, requestedOn }: { reversal: ReversalView; requestedOn: string }) {
  const { t } = useTranslation();
  const group = useGroup();
  const queryClient = useQueryClient();
  const { withStepUp } = useSession();
  const [reason, setReason] = useState('');
  const decide = useMutation({
    mutationFn: (approve: boolean) =>
      withStepUp(() =>
        approve
          ? post(`/groups/${group.groupId}/reversals/${reversal.requestId}/approve`)
          : post(`/groups/${group.groupId}/reversals/${reversal.requestId}/reject`, { reason: reason.trim() }),
      ),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['reversals', group.groupId] });
      void queryClient.invalidateQueries({ queryKey: ['balances', group.groupId] });
      void queryClient.invalidateQueries({ queryKey: ['transactions', group.groupId] });
      void queryClient.invalidateQueries({ queryKey: ['obligations', group.groupId] });
    },
  });
  const mine = reversal.requestedBy === group.myMemberId;

  return (
    <li className="flex flex-col gap-2 rounded-xl border border-line bg-card p-3">
      <p className="text-sm text-muted">{t('reversals.requestedOn', { date: requestedOn })}</p>
      <p>
        <span className="font-semibold">{t('reversals.reason')}:</span> {reversal.reason}
      </p>
      <ErrorMessage error={decide.error} />
      {!mine && (
        <>
          <Button variant="danger" busy={decide.isPending} onClick={() => decide.mutate(true)}>
            {t('reversals.approve')}
          </Button>
          <TextField label={t('reversals.rejectReason')} value={reason} onChange={(e) => setReason(e.target.value)} maxLength={500} />
          <Button variant="secondary" disabled={!reason.trim()} busy={decide.isPending} onClick={() => decide.mutate(false)}>
            {t('reversals.reject')}
          </Button>
        </>
      )}
    </li>
  );
}
