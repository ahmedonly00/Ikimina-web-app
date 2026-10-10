import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { z } from 'zod';
import { get, post, postIdempotent } from '../../api/client';
import {
  canDecideWithdrawals,
  canPayWithdrawals,
  type Balances,
  type BucketView,
  type Page,
  type WithdrawalStatus,
  type WithdrawalView,
} from '../../api/types';
import { useSession } from '../../auth/session';
import { Alert, Badge, Button, Card, ErrorMessage, Loading, SelectField, TextField } from '../../components/ui';
import { fieldError } from '../../lib/forms';
import { useIdempotencyKey } from '../../lib/idempotency';
import { formatRwf } from '../../lib/money';
import { useGroup } from '../groups/GroupLayout';

const today = () => {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
};

function useRefreshWithdrawals() {
  const group = useGroup();
  const queryClient = useQueryClient();
  return () => {
    for (const key of ['withdrawals', 'balances', 'transactions', 'reversals']) {
      void queryClient.invalidateQueries({ queryKey: [key, group.groupId] });
    }
  };
}

export function WithdrawalStatusBadge({ status }: { status: WithdrawalStatus }) {
  const { t } = useTranslation();
  const tone = status === 'REJECTED' ? 'accent' : status === 'PAID' || status === 'APPROVED' ? 'brand' : 'neutral';
  return <Badge tone={tone}>{t(`withdrawalStatus.${status}`)}</Badge>;
}

const askSchema = z.object({
  bucketId: z.string().min(1, 'validation.required'),
  amount: z.string().transform((v) => v.replace(/[\s,]/g, '')).pipe(z.string().regex(/^[1-9][0-9]{0,15}$/, 'validation.amountWhole')),
  reason: z.string().trim().max(500).optional(),
});

/**
 * A member's own withdrawals (owner decisions, Phase 3c): ask for money from a fund that allows it,
 * see each request's status and earliest payout date, and cancel before it is paid. The server checks
 * every rule (bylaws, fund, open loan, balance) and lists every reason it refuses.
 */
export function MyWithdrawals() {
  const { t, i18n } = useTranslation();
  const group = useGroup();
  const refresh = useRefreshWithdrawals();
  const [asked, setAsked] = useState<WithdrawalView | null>(null);
  const funds = useQuery({ queryKey: ['buckets', group.groupId], queryFn: () => get<BucketView[]>(`/groups/${group.groupId}/buckets`) });
  const balances = useQuery({
    queryKey: ['balances', group.groupId, group.myMemberId],
    queryFn: () => get<Balances>(`/groups/${group.groupId}/members/${group.myMemberId}/balances`),
  });
  const mine = useQuery({
    queryKey: ['withdrawals', group.groupId, 'mine'],
    queryFn: () => get<Page<WithdrawalView>>(`/groups/${group.groupId}/withdrawals?memberId=${group.myMemberId}&size=50`),
  });
  const { register, handleSubmit, formState, reset } = useForm<z.input<typeof askSchema>, unknown, z.output<typeof askSchema>>({
    resolver: zodResolver(askSchema),
    defaultValues: { bucketId: '', amount: '', reason: '' },
  });
  const ask = useMutation({
    mutationFn: (v: z.output<typeof askSchema>) =>
      post<WithdrawalView>(`/groups/${group.groupId}/withdrawals`, { bucketId: v.bucketId, amount: v.amount, reason: v.reason || undefined }),
    onSuccess: (view) => {
      setAsked(view);
      reset({ bucketId: '', amount: '', reason: '' });
      refresh();
    },
  });
  const date = (iso: string) => new Date(`${iso}T00:00:00`).toLocaleDateString(i18n.language);

  if (funds.isPending || balances.isPending) {
    return <Loading />;
  }
  const balanceOf = new Map(balances.data?.buckets.map((b) => [b.bucketId, b.balance]) ?? []);
  const withdrawable = funds.data?.filter((f) => f.terms.withdrawable && f.type !== 'SOCIAL_FUND') ?? [];

  return (
    <section aria-labelledby="my-withdrawals-title" className="flex flex-col gap-3">
      <h3 id="my-withdrawals-title" className="font-bold">
        {t('withdrawals.mine')}
      </h3>
      {withdrawable.length === 0 ? (
        <p className="text-sm text-muted">{t('withdrawals.noFunds')}</p>
      ) : (
        <Card>
          <form noValidate onSubmit={handleSubmit((v) => ask.mutate(v))} className="flex flex-col gap-3">
            {asked && <Alert tone="success">{t('withdrawals.asked', { amount: formatRwf(asked.amount), date: date(asked.earliestPayoutOn) })}</Alert>}
            <ErrorMessage error={ask.error ?? funds.error} />
            <SelectField label={t('withdrawals.fund')} error={fieldError(t, formState.errors.bucketId)} {...register('bucketId')}>
              <option value="">{t('record.choose')}</option>
              {withdrawable.map((f) => (
                <option key={f.bucketId} value={f.bucketId}>
                  {t('withdrawals.fundOption', { name: f.name, balance: formatRwf(balanceOf.get(f.bucketId) ?? '0') })}
                </option>
              ))}
            </SelectField>
            <TextField label={t('record.amount')} inputMode="numeric" error={fieldError(t, formState.errors.amount)} {...register('amount')} />
            <TextField label={t('withdrawals.reason')} hint={t('loanProducts.optional')} {...register('reason')} />
            <Button type="submit" busy={ask.isPending}>
              {t('withdrawals.ask')}
            </Button>
          </form>
        </Card>
      )}
      <ErrorMessage error={mine.error} />
      <ul className="flex flex-col gap-2">
        {mine.data?.items.map((w) => (
          <Withdrawal key={w.withdrawalId} withdrawal={w} />
        ))}
      </ul>
    </section>
  );
}

/** The Withdrawals tab: officers see and handle everyone's requests; a member sees their own. */
export function WithdrawalsPage() {
  const { t } = useTranslation();
  const group = useGroup();
  const [status, setStatus] = useState<WithdrawalStatus | ''>('REQUESTED');
  const list = useQuery({
    queryKey: ['withdrawals', group.groupId, status],
    queryFn: () => get<Page<WithdrawalView>>(`/groups/${group.groupId}/withdrawals?size=100${status ? `&status=${status}` : ''}`),
  });
  return (
    <div className="flex flex-col gap-4">
      <h2 className="text-lg font-bold">{t('withdrawals.title')}</h2>
      <SelectField label={t('loans.filter')} value={status} onChange={(e) => setStatus(e.target.value as WithdrawalStatus | '')}>
        <option value="">{t('withdrawals.all')}</option>
        {(['REQUESTED', 'APPROVED', 'PAID', 'REJECTED', 'CANCELLED'] as const).map((s) => (
          <option key={s} value={s}>
            {t(`withdrawalStatus.${s}`)}
          </option>
        ))}
      </SelectField>
      {list.isPending && <Loading />}
      <ErrorMessage error={list.error} />
      {list.data?.items.length === 0 && <p className="text-muted">{t('withdrawals.none')}</p>}
      <ul className="flex flex-col gap-2">
        {list.data?.items.map((w) => (
          <Withdrawal key={w.withdrawalId} withdrawal={w} />
        ))}
      </ul>
    </div>
  );
}

const payForm = z
  .object({
    method: z.enum(['CASH', 'MOMO_MANUAL', 'BANK']),
    externalRef: z.string().trim().max(100).optional(),
    businessDate: z.string().min(1, 'validation.required'),
  })
  .refine((v) => v.method !== 'MOMO_MANUAL' || (v.externalRef ?? '').length > 0, { path: ['externalRef'], message: 'validation.required' });

function Withdrawal({ withdrawal: w }: { withdrawal: WithdrawalView }) {
  const { t, i18n } = useTranslation();
  const group = useGroup();
  const { withStepUp } = useSession();
  const refresh = useRefreshWithdrawals();
  const idempotency = useIdempotencyKey();
  const [rejectReason, setRejectReason] = useState('');
  const base = `/groups/${group.groupId}/withdrawals/${w.withdrawalId}`;
  const mine = w.member?.memberId === group.myMemberId;
  const date = (iso: string) => new Date(`${iso}T00:00:00`).toLocaleDateString(i18n.language);

  // Approving needs a fresh password, like other approvals of money; withStepUp asks when the server does.
  const decide = useMutation({
    mutationFn: (approve: boolean) =>
      withStepUp(() => (approve ? post(`${base}/approve`) : post(`${base}/reject`, { reason: rejectReason.trim() }))),
    onSuccess: refresh,
  });
  const cancel = useMutation({ mutationFn: () => post(`${base}/cancel`), onSuccess: refresh });
  const { register, handleSubmit, formState, control } = useForm<z.input<typeof payForm>, unknown, z.output<typeof payForm>>({
    resolver: zodResolver(payForm),
    defaultValues: { method: 'CASH', businessDate: today() },
  });
  const method = useWatch({ control, name: 'method' });
  const pay = useMutation({
    mutationFn: (v: z.output<typeof payForm>) =>
      postIdempotent(`${base}/pay`, { ...v, externalRef: v.externalRef || undefined }, idempotency.current()),
    onSuccess: () => {
      idempotency.rotate();
      refresh();
    },
  });
  const due = today() >= w.earliestPayoutOn;

  return (
    <li className="flex flex-col gap-2 rounded-xl border border-line bg-card p-3">
      <div className="flex flex-wrap items-center gap-2">
        <span className="font-semibold">{formatRwf(w.amount)}</span>
        <span className="text-sm text-muted">{w.bucketName}</span>
        {!mine && w.member && (
          <span className="text-sm text-muted">{t('loans.memberLabel', { number: w.member.memberNumber, name: w.member.fullName ?? '' })}</span>
        )}
        <WithdrawalStatusBadge status={w.status} />
        {w.reversed && <Badge tone="accent">{t('savings.reversed')}</Badge>}
        {w.recordedByMember && <Badge tone="accent">{t('withdrawals.ownFlag')}</Badge>}
      </div>
      <p className="text-sm text-muted">
        {t('withdrawals.dates', { requested: date(w.requestedOn), earliest: date(w.earliestPayoutOn) })}
      </p>
      {w.reason && <p className="text-sm">{w.reason}</p>}
      {w.decisionReason && <p className="text-sm">{t('loans.rejectedBecause', { reason: w.decisionReason })}</p>}
      <ErrorMessage error={decide.error ?? cancel.error ?? pay.error} />

      {w.status === 'REQUESTED' && !mine && canDecideWithdrawals(group.myRole) && (
        <div className="flex flex-col gap-2">
          <Button busy={decide.isPending} onClick={() => decide.mutate(true)}>
            {t('withdrawals.approve')}
          </Button>
          <TextField label={t('loans.rejectReason')} value={rejectReason} onChange={(e) => setRejectReason(e.target.value)} maxLength={500} />
          <Button variant="secondary" disabled={!rejectReason.trim()} busy={decide.isPending} onClick={() => decide.mutate(false)}>
            {t('withdrawals.reject')}
          </Button>
        </div>
      )}
      {w.status === 'APPROVED' && canPayWithdrawals(group.myRole) && (
        <form noValidate onSubmit={handleSubmit((v) => pay.mutate(v))} className="flex flex-col gap-2">
          {!due && <Alert tone="warning">{t('withdrawals.notYet', { date: date(w.earliestPayoutOn) })}</Alert>}
          <SelectField label={t('record.method')} {...register('method')}>
            <option value="CASH">{t('record.CASH')}</option>
            <option value="MOMO_MANUAL">{t('record.MOMO_MANUAL')}</option>
            <option value="BANK">{t('record.BANK')}</option>
          </SelectField>
          {method !== 'CASH' && <TextField label={t('record.reference')} error={fieldError(t, formState.errors.externalRef)} {...register('externalRef')} />}
          <TextField label={t('record.date')} type="date" max={today()} error={fieldError(t, formState.errors.businessDate)} {...register('businessDate')} />
          <Button type="submit" disabled={!due} busy={pay.isPending}>
            {t('withdrawals.pay')}
          </Button>
        </form>
      )}
      {mine && (w.status === 'REQUESTED' || w.status === 'APPROVED') && (
        <Button variant="ghost" busy={cancel.isPending} onClick={() => cancel.mutate()}>
          {t('withdrawals.cancel')}
        </Button>
      )}
    </li>
  );
}
