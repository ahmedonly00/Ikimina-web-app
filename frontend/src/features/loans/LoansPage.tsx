import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate } from 'react-router';
import { z } from 'zod';
import { get, post } from '../../api/client';
import { canRequestLoan, type LoanProductView, type LoanStatus, type LoanView, type Page } from '../../api/types';
import { Badge, Button, Card, ErrorMessage, Loading, SelectField, TextField } from '../../components/ui';
import { fieldError } from '../../lib/forms';
import { formatRwf } from '../../lib/money';
import { useGroup } from '../groups/GroupLayout';

const STATUSES: LoanStatus[] = ['SUBMITTED', 'PARTIALLY_COUNTERSIGNED', 'APPROVED', 'DISBURSED', 'OVERDUE', 'SETTLED', 'REJECTED', 'CANCELLED'];

export function LoanStatusBadge({ status }: { status: LoanStatus }) {
  const { t } = useTranslation();
  const tone = status === 'OVERDUE' || status === 'REJECTED' ? 'accent' : status === 'DISBURSED' || status === 'APPROVED' ? 'brand' : 'neutral';
  return <Badge tone={tone}>{t(`loanStatus.${status}`)}</Badge>;
}

/** Loans (spec 17.4): a member sees their own, officers see everyone's (the server decides which). */
export function LoansPage() {
  const { t, i18n } = useTranslation();
  const group = useGroup();
  const [status, setStatus] = useState<LoanStatus | ''>('');
  const loans = useQuery({
    queryKey: ['loans', group.groupId, status],
    queryFn: () => get<Page<LoanView>>(`/groups/${group.groupId}/loans?size=100${status ? `&status=${status}` : ''}`),
  });

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-lg font-bold">{t('loans.title')}</h2>
        {canRequestLoan(group.myRole) && (
          <Link to="new" className="inline-flex min-h-11 items-center rounded-lg bg-brand px-4 font-semibold text-on-brand hover:bg-brand-strong">
            {t('loans.request')}
          </Link>
        )}
      </div>
      <SelectField label={t('loans.filter')} value={status} onChange={(e) => setStatus(e.target.value as LoanStatus | '')}>
        <option value="">{t('loans.all')}</option>
        {STATUSES.map((s) => (
          <option key={s} value={s}>
            {t(`loanStatus.${s}`)}
          </option>
        ))}
      </SelectField>
      {loans.isPending && <Loading />}
      <ErrorMessage error={loans.error} />
      {loans.data?.items.length === 0 && <p className="text-muted">{t('loans.none')}</p>}
      <ul className="flex flex-col gap-2">
        {loans.data?.items.map((loan) => (
          <li key={loan.loanId}>
            <Link to={loan.loanId} className="flex flex-wrap items-center gap-2 rounded-xl border border-line bg-card p-3 hover:border-brand">
              <span className="font-semibold">{formatRwf(loan.principal)}</span>
              <span className="text-sm text-muted">{loan.borrower?.fullName ?? loan.borrower?.memberNumber}</span>
              <span className="text-sm text-muted">{loan.productName}</span>
              <span className="text-sm text-muted">{new Date(loan.requestedAt).toLocaleDateString(i18n.language)}</span>
              <LoanStatusBadge status={loan.status} />
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}

const requestSchema = z.object({
  productId: z.string().min(1, 'validation.required'),
  amount: z.string().transform((v) => v.replace(/[\s,]/g, '')).pipe(z.string().regex(/^[1-9][0-9]{0,15}$/, 'validation.amountWhole')),
  termMonths: z.string().regex(/^[1-9][0-9]{0,2}$/, 'validation.required'),
  purpose: z.string().trim().max(1000).optional(),
});

/** A member asks for a loan for themselves; if it breaks the group's rules, every reason is shown. */
export function RequestLoanPage() {
  const { t } = useTranslation();
  const group = useGroup();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const products = useQuery({
    queryKey: ['loan-products', group.groupId],
    queryFn: () => get<LoanProductView[]>(`/groups/${group.groupId}/loan-products`),
  });
  const { register, handleSubmit, formState, control } = useForm<z.input<typeof requestSchema>, unknown, z.output<typeof requestSchema>>({
    resolver: zodResolver(requestSchema),
    defaultValues: { productId: '', amount: '', termMonths: '', purpose: '' },
  });
  const productId = useWatch({ control, name: 'productId' });
  const chosen = products.data?.find((p) => p.productId === productId);
  const request = useMutation({
    mutationFn: (v: z.output<typeof requestSchema>) =>
      post<LoanView>(`/groups/${group.groupId}/loans`, {
        productId: v.productId,
        amount: v.amount,
        termMonths: Number.parseInt(v.termMonths, 10),
        purpose: v.purpose || undefined,
      }),
    onSuccess: (loan) => {
      void queryClient.invalidateQueries({ queryKey: ['loans', group.groupId] });
      void navigate(`/groups/${group.groupId}/loans/${loan.loanId}`);
    },
  });

  if (products.isPending) {
    return <Loading />;
  }
  const open = products.data?.filter((p) => p.status === 'ACTIVE') ?? [];
  return (
    <Card>
      <h2 className="mb-3 text-lg font-bold">{t('loans.request')}</h2>
      {open.length === 0 ? (
        <p className="text-muted">{t('loans.noProducts')}</p>
      ) : (
        <form noValidate onSubmit={handleSubmit((v) => request.mutate(v))} className="flex flex-col gap-4">
          <ErrorMessage error={request.error ?? products.error} />
          <SelectField label={t('loans.product')} error={fieldError(t, formState.errors.productId)} {...register('productId')}>
            <option value="">{t('record.choose')}</option>
            {open.map((p) => (
              <option key={p.productId} value={p.productId}>
                {p.name}
              </option>
            ))}
          </SelectField>
          {chosen && (
            <p className="text-sm text-muted">
              {t('loans.productHint', {
                rate: String(chosen.terms.interestRatePercent).replace(/\.?0+$/, '') || '0',
                period: t(`loanProducts.per_${chosen.terms.interestPeriod}`),
                min: chosen.terms.minTermMonths,
                max: chosen.terms.maxTermMonths,
              })}
            </p>
          )}
          <TextField label={t('loans.amount')} inputMode="numeric" error={fieldError(t, formState.errors.amount)} {...register('amount')} />
          <TextField label={t('loans.termMonths')} inputMode="numeric" error={fieldError(t, formState.errors.termMonths)} {...register('termMonths')} />
          <TextField label={t('loans.purpose')} hint={t('loanProducts.optional')} {...register('purpose')} />
          <Button type="submit" busy={request.isPending}>
            {t('loans.submit')}
          </Button>
        </form>
      )}
    </Card>
  );
}
