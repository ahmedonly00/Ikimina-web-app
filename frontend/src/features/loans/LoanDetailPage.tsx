import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm, useWatch, type UseFormRegisterReturn } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { Link, useParams } from 'react-router';
import { z } from 'zod';
import { get, post, postIdempotent } from '../../api/client';
import { canDisburse, canRecordRepayments, type LoanView, type ScheduleView } from '../../api/types';
import { useSession } from '../../auth/session';
import { Alert, Badge, Button, Card, ErrorMessage, Loading, SelectField, TextField } from '../../components/ui';
import { fieldError } from '../../lib/forms';
import { useIdempotencyKey } from '../../lib/idempotency';
import { formatRwf } from '../../lib/money';
import { formatDecimal } from '../../lib/numbers';
import { useGroup } from '../groups/GroupLayout';
import { LoanStatusBadge } from './LoansPage';

const today = () => {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
};

function useLoanQueries(loanId: string) {
  const group = useGroup();
  const queryClient = useQueryClient();
  return () => {
    void queryClient.invalidateQueries({ queryKey: ['loan', group.groupId, loanId] });
    void queryClient.invalidateQueries({ queryKey: ['schedule', group.groupId, loanId] });
    void queryClient.invalidateQueries({ queryKey: ['loans', group.groupId] });
    void queryClient.invalidateQueries({ queryKey: ['reversals', group.groupId] });
  };
}

/** One loan: its terms, decisions, schedule and repayments, with the actions the viewer's role allows. */
export function LoanDetailPage() {
  const { t, i18n } = useTranslation();
  const group = useGroup();
  const { loanId = '' } = useParams();
  const loan = useQuery({
    queryKey: ['loan', group.groupId, loanId],
    queryFn: () => get<LoanView>(`/groups/${group.groupId}/loans/${loanId}`),
  });

  if (loan.isPending) {
    return <Loading />;
  }
  if (loan.error || !loan.data) {
    return <ErrorMessage error={loan.error} />;
  }
  const l = loan.data;
  const mine = l.borrower?.memberId === group.myMemberId;
  const deciding = l.status === 'SUBMITTED' || l.status === 'PARTIALLY_COUNTERSIGNED';
  const outstanding = l.status === 'DISBURSED' || l.status === 'OVERDUE';
  const date = (iso: string) => new Date(iso).toLocaleDateString(i18n.language);

  return (
    <div className="flex flex-col gap-4">
      <Link to={`/groups/${group.groupId}/loans`} className="inline-flex min-h-11 items-center font-semibold text-brand underline">
        {t('loans.back')}
      </Link>
      <section className="rounded-xl border border-line bg-card p-4" aria-labelledby="loan-title">
        <div className="flex flex-wrap items-center gap-2">
          <h2 id="loan-title" className="text-2xl font-bold">
            {formatRwf(l.principal)}
          </h2>
          <LoanStatusBadge status={l.status} />
        </div>
        <dl className="mt-3 grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 text-sm">
          <dt className="text-muted">{t('loans.borrower')}</dt>
          <dd>
            {t('loans.memberLabel', { number: l.borrower?.memberNumber ?? '', name: l.borrower?.fullName ?? '' })}
            {mine && <span className="text-muted"> ({t('members.you')})</span>}
          </dd>
          <dt className="text-muted">{t('loans.product')}</dt>
          <dd>{l.productName}</dd>
          <dt className="text-muted">{t('loans.termMonths')}</dt>
          <dd>{l.termMonths}</dd>
          <dt className="text-muted">{t('loanProducts.interest')}</dt>
          <dd>
            {t('loanProducts.rateSummary', {
              rate: formatDecimal(l.interestRatePercent),
              period: t(`loanProducts.per_${l.interestPeriod}`),
              method: t(`loanProducts.${l.interestMethod}`),
            })}
          </dd>
          {l.purpose && (
            <>
              <dt className="text-muted">{t('loans.purpose')}</dt>
              <dd>{l.purpose}</dd>
            </>
          )}
          <dt className="text-muted">{t('loans.requestedOn')}</dt>
          <dd>{date(l.requestedAt)}</dd>
          {l.maturesOn && (
            <>
              <dt className="text-muted">{t('loans.maturesOn')}</dt>
              <dd>{new Date(`${l.maturesOn}T00:00:00`).toLocaleDateString(i18n.language)}</dd>
            </>
          )}
          {(outstanding || l.status === 'SETTLED') && (
            <>
              <dt className="text-muted">{t('loans.owed')}</dt>
              <dd className="font-semibold">{formatRwf(l.outstanding.total)}</dd>
            </>
          )}
        </dl>
        {l.rejectionReason && <Alert tone="danger">{t('loans.rejectedBecause', { reason: l.rejectionReason })}</Alert>}
        {l.disbursedByBorrower && <Alert tone="warning">{t('loans.disbursedByBorrower')}</Alert>}
      </section>

      <Approvals loan={l} />
      {/* Only someone the loan is still waiting for, who has not decided yet (spec 9.3; the server decides the same way). */}
      {deciding && !mine && l.waitingFor.includes(group.myRole) && !l.approvals.some((a) => a.memberId === group.myMemberId) && (
        <Decide loan={l} />
      )}
      {mine && (deciding || l.status === 'APPROVED') && <Cancel loan={l} />}
      {l.status === 'APPROVED' && canDisburse(group.myRole) && <Disburse loan={l} />}
      {outstanding && canRecordRepayments(group.myRole) && <Repay loan={l} />}
      {(outstanding || l.status === 'SETTLED') && <Schedule loanId={l.loanId} />}
      {l.repayments.length > 0 && <Repayments loan={l} />}
    </div>
  );
}

function Approvals({ loan }: { loan: LoanView }) {
  const { t, i18n } = useTranslation();
  return (
    <section aria-labelledby="approvals-title">
      <h3 id="approvals-title" className="mb-2 font-bold">
        {t('loans.approvals', { count: loan.requiredApprovals })}
      </h3>
      <ul className="flex flex-col gap-2">
        {loan.approvals.map((a, index) => (
          <li key={index} className="flex flex-wrap items-center gap-2 rounded-xl border border-line bg-card p-3 text-sm">
            <Badge tone={a.decision === 'APPROVE' ? 'brand' : 'accent'}>{t(`loans.decision_${a.decision}`)}</Badge>
            <span className="font-semibold">{t(`roles.${a.role}`)}</span>
            <span className="text-muted">{new Date(a.decidedAt).toLocaleString(i18n.language)}</span>
            {a.comment && <span className="basis-full">{a.comment}</span>}
          </li>
        ))}
      </ul>
      {loan.waitingFor.length > 0 && (
        <p className="mt-2 text-sm text-muted">
          {t('loans.waitingFor', { roles: loan.waitingFor.map((r) => t(`roles.${r}`)).join(t('loans.or')) })}
        </p>
      )}
    </section>
  );
}

function Decide({ loan }: { loan: LoanView }) {
  const { t } = useTranslation();
  const group = useGroup();
  const { withStepUp } = useSession();
  const refresh = useLoanQueries(loan.loanId);
  const [comment, setComment] = useState('');
  const [reason, setReason] = useState('');
  const base = `/groups/${group.groupId}/loans/${loan.loanId}`;
  // Approving at or above the two-approval amount needs a fresh password; withStepUp asks when the server does.
  const decide = useMutation({
    mutationFn: (approve: boolean) =>
      withStepUp(() =>
        approve ? post<LoanView>(`${base}/approve`, { comment: comment.trim() || undefined }) : post<LoanView>(`${base}/reject`, { reason: reason.trim() }),
      ),
    onSuccess: refresh,
  });

  return (
    <Card>
      <h3 className="mb-2 font-bold">{t('loans.decide')}</h3>
      <div className="flex flex-col gap-3">
        <ErrorMessage error={decide.error} />
        <TextField label={t('loans.comment')} hint={t('loanProducts.optional')} value={comment} onChange={(e) => setComment(e.target.value)} maxLength={1000} />
        <Button busy={decide.isPending} onClick={() => decide.mutate(true)}>
          {t('loans.approve')}
        </Button>
        <TextField label={t('loans.rejectReason')} value={reason} onChange={(e) => setReason(e.target.value)} maxLength={500} />
        <Button variant="danger" disabled={!reason.trim()} busy={decide.isPending} onClick={() => decide.mutate(false)}>
          {t('loans.reject')}
        </Button>
      </div>
    </Card>
  );
}

function Cancel({ loan }: { loan: LoanView }) {
  const { t } = useTranslation();
  const group = useGroup();
  const refresh = useLoanQueries(loan.loanId);
  const [sure, setSure] = useState(false);
  const cancel = useMutation({
    mutationFn: () => post<LoanView>(`/groups/${group.groupId}/loans/${loan.loanId}/cancel`),
    onSuccess: refresh,
  });
  return (
    <div className="flex flex-col gap-2">
      <ErrorMessage error={cancel.error} />
      {!sure ? (
        <Button variant="ghost" onClick={() => setSure(true)}>
          {t('loans.cancel')}
        </Button>
      ) : (
        <div className="flex flex-wrap gap-2">
          <Button variant="danger" busy={cancel.isPending} onClick={() => cancel.mutate()}>
            {t('loans.cancelConfirm')}
          </Button>
          <Button variant="ghost" onClick={() => setSure(false)}>
            {t('common.cancel')}
          </Button>
        </div>
      )}
    </div>
  );
}

const moneyForm = z
  .object({
    method: z.enum(['CASH', 'MOMO_MANUAL', 'BANK']),
    externalRef: z.string().trim().max(100).optional(),
    businessDate: z.string().min(1, 'validation.required'),
  })
  .refine((v) => v.method !== 'MOMO_MANUAL' || (v.externalRef ?? '').length > 0, { path: ['externalRef'], message: 'validation.required' });

/** Treasurer records that the money was handed over; the same Idempotency-Key covers every retry (H7). */
function Disburse({ loan }: { loan: LoanView }) {
  const { t } = useTranslation();
  const group = useGroup();
  const refresh = useLoanQueries(loan.loanId);
  const idempotency = useIdempotencyKey();
  const { register, handleSubmit, formState, control } = useForm<z.input<typeof moneyForm>, unknown, z.output<typeof moneyForm>>({
    resolver: zodResolver(moneyForm),
    defaultValues: { method: 'CASH', businessDate: today() },
  });
  const method = useWatch({ control, name: 'method' });
  const disburse = useMutation({
    mutationFn: (v: z.output<typeof moneyForm>) =>
      postIdempotent<LoanView>(
        `/groups/${group.groupId}/loans/${loan.loanId}/disburse`,
        { ...v, externalRef: v.externalRef || undefined },
        idempotency.current(),
      ),
    onSuccess: () => {
      idempotency.rotate();
      refresh();
    },
  });

  return (
    <Card>
      <h3 className="mb-2 font-bold">{t('loans.disburse')}</h3>
      <p className="mb-3 text-sm text-muted">{t('loans.disburseHint', { amount: formatRwf(loan.principal) })}</p>
      <form noValidate onSubmit={handleSubmit((v) => disburse.mutate(v))} className="flex flex-col gap-3">
        <ErrorMessage error={disburse.error} />
        <MethodFields field={(name) => register(name)} method={method} errors={formState.errors} />
        <Button type="submit" busy={disburse.isPending}>
          {t('loans.disburseSubmit')}
        </Button>
      </form>
    </Card>
  );
}

function MethodFields({
  field,
  method,
  errors,
}: {
  field: (name: 'method' | 'externalRef' | 'businessDate') => UseFormRegisterReturn;
  method: string;
  errors: { externalRef?: { message?: string }; businessDate?: { message?: string } };
}) {
  const { t } = useTranslation();
  return (
    <>
      <SelectField label={t('record.method')} {...field('method')}>
        <option value="CASH">{t('record.CASH')}</option>
        <option value="MOMO_MANUAL">{t('record.MOMO_MANUAL')}</option>
        <option value="BANK">{t('record.BANK')}</option>
      </SelectField>
      {method !== 'CASH' && <TextField label={t('record.reference')} error={fieldError(t, errors.externalRef)} {...field('externalRef')} />}
      <TextField label={t('record.date')} type="date" max={today()} error={fieldError(t, errors.businessDate)} {...field('businessDate')} />
    </>
  );
}

const repayForm = z
  .object({
    amount: z.string().transform((v) => v.replace(/[\s,]/g, '')).pipe(z.string().regex(/^[1-9][0-9]{0,15}$/, 'validation.amountWhole')),
    method: z.enum(['CASH', 'MOMO_MANUAL', 'BANK']),
    externalRef: z.string().trim().max(100).optional(),
    businessDate: z.string().min(1, 'validation.required'),
  })
  .refine((v) => v.method !== 'MOMO_MANUAL' || (v.externalRef ?? '').length > 0, { path: ['externalRef'], message: 'validation.required' });

function Repay({ loan }: { loan: LoanView }) {
  const { t } = useTranslation();
  const group = useGroup();
  const refresh = useLoanQueries(loan.loanId);
  const idempotency = useIdempotencyKey();
  const [recorded, setRecorded] = useState<string | null>(null);
  const { register, handleSubmit, formState, control, reset } = useForm<z.input<typeof repayForm>, unknown, z.output<typeof repayForm>>({
    resolver: zodResolver(repayForm),
    defaultValues: { amount: '', method: 'CASH', businessDate: today() },
  });
  const method = useWatch({ control, name: 'method' });
  const repay = useMutation({
    mutationFn: (v: z.output<typeof repayForm>) =>
      postIdempotent<LoanView>(
        `/groups/${group.groupId}/loans/${loan.loanId}/repayments`,
        { ...v, externalRef: v.externalRef || undefined },
        idempotency.current(),
      ),
    onSuccess: (view, v) => {
      idempotency.rotate();
      setRecorded(t('loans.repaid', { amount: formatRwf(v.amount), owed: formatRwf(view.outstanding.total) }));
      reset({ amount: '', method: 'CASH', businessDate: today() });
      refresh();
    },
  });

  return (
    <Card>
      <h3 className="mb-2 font-bold">{t('loans.repay')}</h3>
      <p className="mb-3 text-sm text-muted">{t('loans.owedNow', { amount: formatRwf(loan.outstanding.total) })}</p>
      <form noValidate onSubmit={handleSubmit((v) => repay.mutate(v))} className="flex flex-col gap-3">
        {recorded && <Alert tone="success">{recorded}</Alert>}
        <ErrorMessage error={repay.error} />
        <TextField label={t('record.amount')} inputMode="numeric" error={fieldError(t, formState.errors.amount)} {...register('amount')} />
        <MethodFields field={(name) => register(name)} method={method} errors={formState.errors} />
        <Button type="submit" busy={repay.isPending}>
          {t('loans.repaySubmit')}
        </Button>
      </form>
    </Card>
  );
}

function Schedule({ loanId }: { loanId: string }) {
  const { t, i18n } = useTranslation();
  const group = useGroup();
  const schedule = useQuery({
    queryKey: ['schedule', group.groupId, loanId],
    queryFn: () => get<ScheduleView>(`/groups/${group.groupId}/loans/${loanId}/schedule`),
  });
  if (schedule.isPending) {
    return <Loading />;
  }
  return (
    <section className="overflow-x-auto rounded-xl border border-line bg-card p-3" aria-labelledby="schedule-title">
      <h3 id="schedule-title" className="mb-2 font-bold">
        {t('loans.schedule')}
      </h3>
      <ErrorMessage error={schedule.error} />
      <table className="w-full min-w-[32rem] text-sm">
        <thead>
          <tr className="text-left text-muted">
            <th scope="col" className="py-1">
              {t('loans.installmentNo')}
            </th>
            <th scope="col">{t('loans.due')}</th>
            <th scope="col" className="text-right">{t('loans.principal')}</th>
            <th scope="col" className="text-right">{t('loans.interestDue')}</th>
            <th scope="col" className="text-right">{t('loans.paid')}</th>
            <th scope="col">{t('loans.state')}</th>
          </tr>
        </thead>
        <tbody>
          {schedule.data?.installments.map((row) => (
            <tr key={row.number} className="border-t border-line">
              <td className="py-1">{row.number}</td>
              <td>{new Date(`${row.dueDate}T00:00:00`).toLocaleDateString(i18n.language)}</td>
              <td className="text-right">{formatRwf(row.principalDue)}</td>
              <td className="text-right">{formatRwf(row.interestDue)}</td>
              <td className="text-right">{t('loans.paidParts', { principal: formatRwf(row.principalPaid), interest: formatRwf(row.interestPaid) })}</td>
              <td>
                <Badge tone={row.status === 'OVERDUE' ? 'accent' : row.status === 'PAID' ? 'brand' : 'neutral'}>{t(`installmentStatus.${row.status}`)}</Badge>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}

function Repayments({ loan }: { loan: LoanView }) {
  const { t } = useTranslation();
  const group = useGroup();
  return (
    <section aria-labelledby="repayments-title">
      <h3 id="repayments-title" className="mb-2 font-bold">
        {t('loans.repayments')}
      </h3>
      <ul className="flex flex-col gap-2">
        {loan.repayments.map((r) => (
          <Repayment key={r.repaymentId} loanId={loan.loanId} repayment={r} canReverse={canRecordRepayments(group.myRole)} />
        ))}
      </ul>
    </section>
  );
}

function Repayment({ loanId, repayment, canReverse }: { loanId: string; repayment: LoanView['repayments'][number]; canReverse: boolean }) {
  const { t, i18n } = useTranslation();
  const group = useGroup();
  const refresh = useLoanQueries(loanId);
  const [asking, setAsking] = useState(false);
  const [reason, setReason] = useState('');
  const request = useMutation({
    mutationFn: () => post(`/groups/${group.groupId}/journals/${repayment.journalId}/reverse`, { reason: reason.trim() }),
    onSuccess: refresh,
  });

  return (
    <li className="rounded-xl border border-line bg-card p-3">
      <div className="flex flex-wrap items-center gap-2">
        <span className="font-semibold">{formatRwf(repayment.amount)}</span>
        <span className="text-sm text-muted">{t('loans.split', { interest: formatRwf(repayment.interest), principal: formatRwf(repayment.principal) })}</span>
        <span className="text-sm text-muted">{new Date(`${repayment.businessDate}T00:00:00`).toLocaleDateString(i18n.language)}</span>
        <span className="text-sm text-muted">{t(`record.${repayment.method === 'MOMO_API' ? 'MOMO_MANUAL' : repayment.method}`)}</span>
        {repayment.externalRef && <span className="font-mono text-xs text-muted">{repayment.externalRef}</span>}
        {repayment.reversed && <Badge tone="accent">{t('savings.reversed')}</Badge>}
        {repayment.recordedByBorrower && <Badge tone="accent">{t('loans.ownLoanFlag')}</Badge>}
      </div>
      {canReverse && !repayment.reversed && !request.isSuccess && (
        <>
          {!asking ? (
            <Button variant="ghost" className="-ml-2 mt-1" onClick={() => setAsking(true)}>
              {t('savings.requestReversal')}
            </Button>
          ) : (
            <div className="mt-2 flex flex-col gap-2">
              <ErrorMessage error={request.error} />
              <TextField label={t('loans.reversalReason')} value={reason} onChange={(e) => setReason(e.target.value)} maxLength={500} />
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
