import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm, type UseFormRegisterReturn } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { z } from 'zod';
import { get, patch, post } from '../../api/client';
import { canEditSettings, type LoanProductView, type ProductTerms } from '../../api/types';
import { useSession } from '../../auth/session';
import { Alert, Badge, Button, Card, ErrorMessage, Loading, SelectField, TextField } from '../../components/ui';
import { fieldError, requiredText } from '../../lib/forms';
import { formatRwf } from '../../lib/money';
import { useGroup } from '../groups/GroupLayout';

const optionalWholeRwf = z
  .string()
  .transform((v) => v.replace(/[\s,]/g, ''))
  .pipe(z.string().regex(/^([1-9][0-9]{0,15})?$/, 'validation.amountWhole'));
const months = z.string().regex(/^[1-9][0-9]{0,2}$/, 'validation.required');

const termsSchema = z
  .object({
    interestMethod: z.enum(['FLAT', 'REDUCING_BALANCE']),
    interestRatePercent: z.string().regex(/^(100|[0-9]{1,2})(\.[0-9]{1,4})?$/, 'validation.rate'),
    interestPeriod: z.enum(['MONTH', 'LOAN_TERM']),
    repaymentFrequency: z.enum(['MONTHLY', 'AT_MATURITY']),
    minTermMonths: months,
    maxTermMonths: months,
    minAmount: optionalWholeRwf,
    maxAmount: optionalWholeRwf,
    maxMultipleOfSavings: z.string().regex(/^([0-9]{1,5}(\.[0-9]{1,2})?)?$/, 'validation.multiple'),
    dualApprovalThreshold: optionalWholeRwf,
    graceDays: z.string().regex(/^[0-9]{1,3}$/, 'validation.required'),
    allowConcurrentLoans: z.boolean(),
  })
  .refine((v) => Number.parseInt(v.maxTermMonths, 10) >= Number.parseInt(v.minTermMonths, 10), {
    path: ['maxTermMonths'],
    message: 'validation.termRange',
  });
type TermsInput = z.input<typeof termsSchema>;
type TermsValues = z.output<typeof termsSchema>;
type TermsField = keyof TermsInput;

const createSchema = z.object({ name: requiredText(120), terms: termsSchema });

const blank = (v: string) => (v === '' ? null : v);

/** Form values to the API's terms; the allocation order is kept as it is (default: fines, interest, principal). */
function toTerms(v: TermsValues, allocationOrder: ProductTerms['allocationOrder']): ProductTerms {
  return {
    interestMethod: v.interestMethod,
    interestRatePercent: v.interestRatePercent,
    interestPeriod: v.interestPeriod,
    repaymentFrequency: v.repaymentFrequency,
    minTermMonths: Number.parseInt(v.minTermMonths, 10),
    maxTermMonths: Number.parseInt(v.maxTermMonths, 10),
    minAmount: blank(v.minAmount),
    maxAmount: blank(v.maxAmount),
    maxMultipleOfSavings: blank(v.maxMultipleOfSavings),
    dualApprovalThreshold: blank(v.dualApprovalThreshold),
    graceDays: Number.parseInt(v.graceDays, 10),
    allocationOrder,
    allowConcurrentLoans: v.allowConcurrentLoans,
  };
}

const wholeText = (amount: string | null) => (amount ? amount.replace(/\.0+$/, '') : '');

function toForm(terms: ProductTerms): TermsInput {
  return {
    interestMethod: terms.interestMethod,
    interestRatePercent: String(terms.interestRatePercent).replace(/\.?0+$/, '') || '0',
    interestPeriod: terms.interestPeriod,
    repaymentFrequency: terms.repaymentFrequency === 'AT_MATURITY' ? 'AT_MATURITY' : 'MONTHLY',
    minTermMonths: String(terms.minTermMonths),
    maxTermMonths: String(terms.maxTermMonths),
    minAmount: wholeText(terms.minAmount),
    maxAmount: wholeText(terms.maxAmount),
    maxMultipleOfSavings: terms.maxMultipleOfSavings === null ? '' : String(terms.maxMultipleOfSavings),
    dualApprovalThreshold: wholeText(terms.dualApprovalThreshold),
    graceDays: String(terms.graceDays),
    allowConcurrentLoans: terms.allowConcurrentLoans,
  };
}

const DEFAULT_TERMS: TermsInput = {
  interestMethod: 'FLAT',
  interestRatePercent: '5',
  interestPeriod: 'MONTH',
  repaymentFrequency: 'MONTHLY',
  minTermMonths: '1',
  maxTermMonths: '12',
  minAmount: '',
  maxAmount: '',
  maxMultipleOfSavings: '',
  dualApprovalThreshold: '',
  graceDays: '0',
  allowConcurrentLoans: false,
};

/**
 * Loan products (spec 6.4, owner decision Phase 3): managed with SETTINGS_EDIT. The name and
 * open/closed apply at once; a change to the money terms waits for a second officer.
 */
export function LoanProductsPage() {
  const { t } = useTranslation();
  const group = useGroup();
  const [creating, setCreating] = useState(false);
  const products = useQuery({
    queryKey: ['loan-products', group.groupId],
    queryFn: () => get<LoanProductView[]>(`/groups/${group.groupId}/loan-products`),
  });
  const manager = canEditSettings(group.myRole);

  if (products.isPending) {
    return <Loading />;
  }
  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-lg font-bold">{t('loanProducts.title')}</h2>
        {manager && !creating && <Button onClick={() => setCreating(true)}>{t('loanProducts.create')}</Button>}
      </div>
      {!manager && <Alert>{t('loanProducts.readOnly')}</Alert>}
      {creating && <CreateProduct onDone={() => setCreating(false)} />}
      <ErrorMessage error={products.error} />
      {products.data?.length === 0 && <p className="text-muted">{t('loanProducts.none')}</p>}
      <ul className="flex flex-col gap-3">
        {products.data?.map((product) => (
          <Product key={product.productId} product={product} manager={manager} />
        ))}
      </ul>
    </div>
  );
}

function TermsFields({ field, errors }: { field: (name: TermsField) => UseFormRegisterReturn; errors: Partial<Record<TermsField, { message?: string }>> }) {
  const { t } = useTranslation();
  const error = (name: TermsField) => fieldError(t, errors[name]);
  return (
    <>
      <SelectField label={t('loanProducts.interestMethod')} {...field('interestMethod')}>
        <option value="FLAT">{t('loanProducts.FLAT')}</option>
        <option value="REDUCING_BALANCE">{t('loanProducts.REDUCING_BALANCE')}</option>
      </SelectField>
      <TextField label={t('loanProducts.rate')} inputMode="decimal" error={error('interestRatePercent')} {...field('interestRatePercent')} />
      <SelectField label={t('loanProducts.interestPeriod')} {...field('interestPeriod')}>
        <option value="MONTH">{t('loanProducts.MONTH')}</option>
        <option value="LOAN_TERM">{t('loanProducts.LOAN_TERM')}</option>
      </SelectField>
      <SelectField label={t('loanProducts.frequency')} {...field('repaymentFrequency')}>
        <option value="MONTHLY">{t('loanProducts.MONTHLY')}</option>
        <option value="AT_MATURITY">{t('loanProducts.AT_MATURITY')}</option>
      </SelectField>
      <div className="grid grid-cols-2 gap-3">
        <TextField label={t('loanProducts.minTerm')} inputMode="numeric" error={error('minTermMonths')} {...field('minTermMonths')} />
        <TextField label={t('loanProducts.maxTerm')} inputMode="numeric" error={error('maxTermMonths')} {...field('maxTermMonths')} />
        <TextField label={t('loanProducts.minAmount')} inputMode="numeric" hint={t('loanProducts.optional')} error={error('minAmount')} {...field('minAmount')} />
        <TextField label={t('loanProducts.maxAmount')} inputMode="numeric" hint={t('loanProducts.optional')} error={error('maxAmount')} {...field('maxAmount')} />
      </div>
      <TextField
        label={t('loanProducts.multiple')}
        inputMode="decimal"
        hint={t('loanProducts.multipleHint')}
        error={error('maxMultipleOfSavings')}
        {...field('maxMultipleOfSavings')}
      />
      <TextField
        label={t('loanProducts.threshold')}
        inputMode="numeric"
        hint={t('loanProducts.thresholdHint')}
        error={error('dualApprovalThreshold')}
        {...field('dualApprovalThreshold')}
      />
      <TextField label={t('loanProducts.graceDays')} inputMode="numeric" error={error('graceDays')} {...field('graceDays')} />
      <label className="flex min-h-11 items-center gap-2">
        <input type="checkbox" className="size-5" {...field('allowConcurrentLoans')} />
        {t('loanProducts.allowConcurrent')}
      </label>
    </>
  );
}

function CreateProduct({ onDone }: { onDone: () => void }) {
  const { t } = useTranslation();
  const group = useGroup();
  const queryClient = useQueryClient();
  const { register, handleSubmit, formState } = useForm<z.input<typeof createSchema>, unknown, z.output<typeof createSchema>>({
    resolver: zodResolver(createSchema),
    defaultValues: { name: '', terms: DEFAULT_TERMS },
  });
  const create = useMutation({
    mutationFn: (v: z.output<typeof createSchema>) =>
      post<LoanProductView>(`/groups/${group.groupId}/loan-products`, { name: v.name, terms: toTerms(v.terms, ['FINES', 'INTEREST', 'PRINCIPAL']) }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['loan-products', group.groupId] });
      onDone();
    },
  });

  return (
    <Card>
      <form noValidate onSubmit={handleSubmit((v) => create.mutate(v))} className="flex flex-col gap-4">
        <h3 className="font-bold">{t('loanProducts.create')}</h3>
        <ErrorMessage error={create.error} />
        <TextField label={t('loanProducts.name')} error={fieldError(t, formState.errors.name)} {...register('name')} />
        <TermsFields field={(name) => register(`terms.${name}`)} errors={formState.errors.terms ?? {}} />
        <div className="flex gap-2">
          <Button type="submit" busy={create.isPending}>
            {t('loanProducts.save')}
          </Button>
          <Button variant="ghost" onClick={onDone}>
            {t('common.cancel')}
          </Button>
        </div>
      </form>
    </Card>
  );
}

function TermsSummary({ terms }: { terms: ProductTerms }) {
  const { t } = useTranslation();
  const money = (amount: string | null) => (amount ? formatRwf(amount) : t('loanProducts.noLimit'));
  return (
    <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 text-sm">
      <dt className="text-muted">{t('loanProducts.interest')}</dt>
      <dd>
        {t('loanProducts.rateSummary', {
          rate: String(terms.interestRatePercent).replace(/\.?0+$/, '') || '0',
          period: t(`loanProducts.per_${terms.interestPeriod}`),
          method: t(`loanProducts.${terms.interestMethod}`),
        })}
      </dd>
      <dt className="text-muted">{t('loanProducts.frequency')}</dt>
      <dd>{t(`loanProducts.${terms.repaymentFrequency}`)}</dd>
      <dt className="text-muted">{t('loanProducts.term')}</dt>
      <dd>{t('loanProducts.termRange', { min: terms.minTermMonths, max: terms.maxTermMonths })}</dd>
      <dt className="text-muted">{t('loanProducts.amount')}</dt>
      <dd>
        {money(terms.minAmount)} – {money(terms.maxAmount)}
      </dd>
      <dt className="text-muted">{t('loanProducts.multiple')}</dt>
      <dd>{terms.maxMultipleOfSavings === null ? t('loanProducts.noLimit') : `× ${String(terms.maxMultipleOfSavings)}`}</dd>
      <dt className="text-muted">{t('loanProducts.threshold')}</dt>
      <dd>{terms.dualApprovalThreshold ? formatRwf(terms.dualApprovalThreshold) : t('loanProducts.alwaysTwo')}</dd>
      <dt className="text-muted">{t('loanProducts.graceDays')}</dt>
      <dd>{terms.graceDays}</dd>
    </dl>
  );
}

function Product({ product, manager }: { product: LoanProductView; manager: boolean }) {
  const { t } = useTranslation();
  const group = useGroup();
  const queryClient = useQueryClient();
  const { withStepUp } = useSession();
  const [editing, setEditing] = useState(false);
  const [proposed, setProposed] = useState(false);
  const [rejectReason, setRejectReason] = useState('');
  const base = `/groups/${group.groupId}/loan-products/${product.productId}`;
  const refresh = () => void queryClient.invalidateQueries({ queryKey: ['loan-products', group.groupId] });

  const { register, handleSubmit, formState } = useForm<TermsInput, unknown, TermsValues>({
    resolver: zodResolver(termsSchema),
    defaultValues: toForm(product.terms),
  });
  // A change to the money terms needs a fresh password (the server asks for it); open/closed does not.
  const update = useMutation({
    mutationFn: (body: Record<string, unknown>) => withStepUp(() => patch<LoanProductView>(base, { version: product.version, ...body })),
    onSuccess: (_view, body) => {
      if (body.terms) {
        setEditing(false);
        setProposed(true);
      }
      refresh();
    },
  });
  const decide = useMutation({
    mutationFn: (confirm: boolean) =>
      withStepUp(() =>
        confirm
          ? post(`${base}/changes/${product.pendingChange?.changeId}/confirm`)
          : post(`${base}/changes/${product.pendingChange?.changeId}/reject`, { reason: rejectReason.trim() }),
      ),
    onSuccess: refresh,
  });
  const pending = product.pendingChange;
  const proposedByMe = pending?.proposedBy === group.myMemberId;

  return (
    <li className="flex flex-col gap-3 rounded-xl border border-line bg-card p-4">
      <div className="flex flex-wrap items-center gap-2">
        <h3 className="font-bold">{product.name}</h3>
        {product.status === 'CLOSED' && <Badge tone="accent">{t('loanProducts.closed')}</Badge>}
      </div>
      <TermsSummary terms={product.terms} />
      <ErrorMessage error={update.error ?? decide.error} />
      {proposed && !pending && <Alert tone="success">{t('loanProducts.proposed')}</Alert>}

      {pending && (
        <section className="flex flex-col gap-2 rounded-lg bg-surface p-3">
          <Alert tone="warning">{t('loanProducts.pending')}</Alert>
          <TermsSummary terms={pending.proposed} />
          {manager && !proposedByMe && (
            <>
              <Button busy={decide.isPending} onClick={() => decide.mutate(true)}>
                {t('loanProducts.confirm')}
              </Button>
              <TextField label={t('loanProducts.rejectReason')} value={rejectReason} onChange={(e) => setRejectReason(e.target.value)} maxLength={500} />
              <Button variant="secondary" disabled={!rejectReason.trim()} busy={decide.isPending} onClick={() => decide.mutate(false)}>
                {t('loanProducts.reject')}
              </Button>
            </>
          )}
        </section>
      )}

      {manager && editing && (
        <form noValidate className="flex flex-col gap-3" onSubmit={handleSubmit((v) => update.mutate({ terms: toTerms(v, product.terms.allocationOrder) }))}>
          <TermsFields field={(name) => register(name)} errors={formState.errors} />
          <div className="flex gap-2">
            <Button type="submit" busy={update.isPending}>
              {t('loanProducts.proposeTerms')}
            </Button>
            <Button variant="ghost" onClick={() => setEditing(false)}>
              {t('common.cancel')}
            </Button>
          </div>
        </form>
      )}
      {manager && !editing && (
        <div className="flex flex-wrap gap-2">
          {product.status === 'ACTIVE' && (
            <Button variant="secondary" onClick={() => setEditing(true)}>
              {t('loanProducts.editTerms')}
            </Button>
          )}
          <Button variant="ghost" busy={update.isPending} onClick={() => update.mutate({ status: product.status === 'ACTIVE' ? 'CLOSED' : 'ACTIVE' })}>
            {product.status === 'ACTIVE' ? t('loanProducts.close') : t('loanProducts.reopen')}
          </Button>
        </div>
      )}
    </li>
  );
}
