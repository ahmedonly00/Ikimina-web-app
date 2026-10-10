import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import { get, post } from '../../api/client';
import { canRecordContributions, type Balances, type ObligationView, type Page, type TransactionView } from '../../api/types';
import { Alert, Badge, Button, ErrorMessage, Loading, TextField } from '../../components/ui';
import { formatRwf } from '../../lib/money';
import { useGroup } from '../groups/GroupLayout';

/**
 * One member's savings: balance per fund, what they owe, and their payments. Members see their
 * own; officers can open anyone's (the server enforces both).
 */
export function MemberSavings({ memberId, title }: { memberId: string; title: string }) {
  const { t, i18n } = useTranslation();
  const group = useGroup();
  const base = `/groups/${group.groupId}`;
  const balances = useQuery({ queryKey: ['balances', group.groupId, memberId], queryFn: () => get<Balances>(`${base}/members/${memberId}/balances`) });
  const dues = useQuery({
    queryKey: ['obligations', group.groupId, memberId],
    queryFn: () => get<Page<ObligationView>>(`${base}/obligations?memberId=${memberId}&size=50`),
  });
  const payments = useQuery({
    queryKey: ['transactions', group.groupId, memberId],
    queryFn: () => get<Page<TransactionView>>(`${base}/members/${memberId}/transactions?size=50`),
  });

  if (balances.isPending) {
    return <Loading />;
  }
  if (balances.error || !balances.data) {
    return <ErrorMessage error={balances.error} />;
  }
  const fundName = new Map(balances.data.buckets.map((b) => [b.bucketId, b.name]));
  const open = dues.data?.items.filter((o) => o.status !== 'PAID' && o.status !== 'WAIVED') ?? [];
  const date = (iso: string) => new Date(`${iso}T00:00:00`).toLocaleDateString(i18n.language);

  return (
    <div className="flex flex-col gap-4">
      <h2 className="text-lg font-bold">{title}</h2>
      <section className="rounded-xl border border-line bg-card p-4" aria-label={t('savings.total')}>
        <p className="text-sm text-muted">{t('savings.total')}</p>
        <p className="text-3xl font-bold text-brand">{formatRwf(balances.data.total)}</p>
        {balances.data.buckets.length === 0 && <p className="mt-2 text-muted">{t('savings.noBuckets')}</p>}
        <ul className="mt-3 grid grid-cols-1 gap-2 sm:grid-cols-2">
          {balances.data.buckets.map((bucket) => (
            <li key={bucket.bucketId} className="rounded-lg bg-surface p-3">
              <span className="block text-sm text-muted">{bucket.name}</span>
              <span className="text-lg font-semibold">{formatRwf(bucket.balance)}</span>
            </li>
          ))}
        </ul>
        <Link to={`/groups/${group.groupId}/savings/statement/${memberId}`} className="mt-3 inline-flex min-h-11 items-center font-semibold text-brand underline">
          {t('savings.statement')}
        </Link>
      </section>

      <section aria-labelledby="dues-title">
        <h3 id="dues-title" className="mb-2 font-bold">{t('savings.dues')}</h3>
        <ErrorMessage error={dues.error} />
        {dues.data && open.length === 0 && <p className="text-muted">{t('savings.noDues')}</p>}
        <ul className="flex flex-col gap-2">
          {open.map((o) => (
            <li key={o.obligationId} className="flex flex-wrap items-center gap-2 rounded-xl border border-line bg-card p-3">
              <span className="font-semibold">{fundName.get(o.bucketId)}</span>
              <span className="text-sm text-muted">{t('savings.due', { date: date(o.dueDate) })}</span>
              <span className="text-sm">{t('savings.paidOf', { paid: formatRwf(o.amountPaid), due: formatRwf(o.amountDue) })}</span>
              <Badge tone={o.status === 'OVERDUE' ? 'accent' : 'neutral'}>{t(`obligationStatus.${o.status}`)}</Badge>
            </li>
          ))}
        </ul>
      </section>

      <section aria-labelledby="payments-title">
        <h3 id="payments-title" className="mb-2 font-bold">{t('savings.transactions')}</h3>
        <ErrorMessage error={payments.error} />
        {payments.data?.items.length === 0 && <p className="text-muted">{t('savings.noTransactions')}</p>}
        <ul className="flex flex-col gap-2">
          {payments.data?.items.map((p) => (
            <Payment key={p.transactionId} payment={p} canReverse={canRecordContributions(group.myRole)} />
          ))}
        </ul>
      </section>
    </div>
  );
}

function Payment({ payment, canReverse }: { payment: TransactionView; canReverse: boolean }) {
  const { t, i18n } = useTranslation();
  const group = useGroup();
  const queryClient = useQueryClient();
  const [asking, setAsking] = useState(false);
  const [reason, setReason] = useState('');
  const request = useMutation({
    mutationFn: () => post(`/groups/${group.groupId}/journals/${payment.journalId}/reverse`, { reason: reason.trim() }),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['reversals', group.groupId] }),
  });

  return (
    <li className="rounded-xl border border-line bg-card p-3">
      <div className="flex flex-wrap items-center gap-2">
        <span className="font-semibold">{formatRwf(payment.amount)}</span>
        <span className="text-sm text-muted">{payment.bucketName}</span>
        <span className="text-sm text-muted">{new Date(`${payment.businessDate}T00:00:00`).toLocaleDateString(i18n.language)}</span>
        <span className="text-sm text-muted">{t(`record.${payment.method === 'MOMO_API' ? 'MOMO_MANUAL' : payment.method}`)}</span>
        {payment.externalRef && <span className="font-mono text-xs text-muted">{payment.externalRef}</span>}
        {payment.type === 'WITHDRAWAL' && <Badge>{t('savings.withdrawal')}</Badge>}
        {payment.reversed && <Badge tone="accent">{t('savings.reversed')}</Badge>}
      </div>
      {canReverse && !payment.reversed && !request.isSuccess && (
        <>
          {!asking ? (
            <Button variant="ghost" className="-ml-2 mt-1" onClick={() => setAsking(true)}>
              {t('savings.requestReversal')}
            </Button>
          ) : (
            <div className="mt-2 flex flex-col gap-2">
              <ErrorMessage error={request.error} />
              <TextField label={t('savings.reversalReason')} value={reason} onChange={(e) => setReason(e.target.value)} maxLength={500} />
              <div className="flex gap-2">
                <Button variant="danger" disabled={!reason.trim()} busy={request.isPending} onClick={() => request.mutate()}>
                  {t('savings.requestReversal')}
                </Button>
                <Button variant="ghost" onClick={() => setAsking(false)}>
                  {t('common.cancel')}
                </Button>
              </div>
            </div>
          )}
        </>
      )}
      {request.isSuccess && <Alert tone="warning">{t('savings.reversalRequested')}</Alert>}
    </li>
  );
}
