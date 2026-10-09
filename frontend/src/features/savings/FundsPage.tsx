import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm, useWatch, type UseFormRegisterReturn } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { z } from 'zod';
import { get, patch, post } from '../../api/client';
import { canEditSettings, type BucketTerms, type BucketView, type Frequency } from '../../api/types';
import { useSession } from '../../auth/session';
import { Alert, Badge, Button, Card, ErrorMessage, Loading, SelectField, TextField } from '../../components/ui';
import { fieldError, requiredText } from '../../lib/forms';
import { formatRwf } from '../../lib/money';
import { useGroup } from '../groups/GroupLayout';

const FREQUENCIES: Frequency[] = ['WEEKLY', 'BIWEEKLY', 'MONTHLY', 'PER_MEETING', 'ADHOC'];
// Whole francs, as on the payment form; the server checks the rest (minimum > 0 when mandatory).
const wholeRwf = z.string().transform((v) => v.replace(/[\s,]/g, '')).pipe(z.string().regex(/^[0-9]{1,16}$/, 'validation.amountWhole'));

const termsShape = {
  mandatory: z.boolean(),
  minimumContribution: wholeRwf,
  contributionFrequency: z.enum(['WEEKLY', 'BIWEEKLY', 'MONTHLY', 'PER_MEETING', 'ADHOC']),
  withdrawable: z.boolean(),
};

const createSchema = z
  .object({
    name: requiredText(200),
    description: z.string().trim().max(1000).optional(),
    type: z.enum(['SAVINGS', 'SOCIAL_FUND', 'SHARES']),
    cycleType: z.enum(['ROLLING', 'FIXED_TERM']),
    startDate: z.string().min(1, 'validation.required'),
    endDate: z.string().optional(),
    ...termsShape,
  })
  .refine((v) => v.cycleType !== 'FIXED_TERM' || !!v.endDate, { path: ['endDate'], message: 'validation.required' });

const termsSchema = z.object(termsShape);
type TermsField = keyof typeof termsShape;

const invalidateFunds = (queryClient: ReturnType<typeof useQueryClient>, groupId: string) =>
  void queryClient.invalidateQueries({ queryKey: ['buckets', groupId] });

/**
 * Savings funds (owner decision, Phase 2): managed with SETTINGS_EDIT. Names and open/closed apply
 * at once; a change to the money terms is only a proposal until a second officer confirms it.
 */
export function FundsPage() {
  const { t } = useTranslation();
  const group = useGroup();
  const [creating, setCreating] = useState(false);
  const funds = useQuery({ queryKey: ['buckets', group.groupId], queryFn: () => get<BucketView[]>(`/groups/${group.groupId}/buckets`) });
  const manager = canEditSettings(group.myRole);

  if (funds.isPending) {
    return <Loading />;
  }
  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-lg font-bold">{t('funds.title')}</h2>
        {manager && !creating && <Button onClick={() => setCreating(true)}>{t('funds.create')}</Button>}
      </div>
      {!manager && <Alert>{t('funds.readOnly')}</Alert>}
      {creating && <CreateFund onDone={() => setCreating(false)} />}
      <ErrorMessage error={funds.error} />
      {funds.data?.length === 0 && <p className="text-muted">{t('funds.none')}</p>}
      <ul className="flex flex-col gap-3">
        {funds.data?.map((fund) => (
          <Fund key={fund.bucketId} fund={fund} manager={manager} />
        ))}
      </ul>
    </div>
  );
}

function TermsFields({ register, amountError }: { register: (name: TermsField) => UseFormRegisterReturn; amountError?: { message?: string } }) {
  const { t } = useTranslation();
  return (
    <>
      <TextField label={t('funds.minimum')} inputMode="numeric" error={fieldError(t, amountError)} {...register('minimumContribution')} />
      <SelectField label={t('funds.frequency')} {...register('contributionFrequency')}>
        {FREQUENCIES.map((f) => (
          <option key={f} value={f}>
            {t(`funds.${f}`)}
          </option>
        ))}
      </SelectField>
      <label className="flex min-h-11 items-center gap-2">
        <input type="checkbox" className="size-5" {...register('mandatory')} />
        {t('funds.mandatory')}
      </label>
      <label className="flex min-h-11 items-center gap-2">
        <input type="checkbox" className="size-5" {...register('withdrawable')} />
        {t('funds.withdrawable')}
      </label>
    </>
  );
}

function CreateFund({ onDone }: { onDone: () => void }) {
  const { t } = useTranslation();
  const group = useGroup();
  const queryClient = useQueryClient();
  const { register, handleSubmit, formState, control } = useForm<z.input<typeof createSchema>, unknown, z.output<typeof createSchema>>({
    resolver: zodResolver(createSchema),
    defaultValues: {
      type: 'SAVINGS',
      cycleType: 'ROLLING',
      contributionFrequency: 'MONTHLY',
      mandatory: true,
      withdrawable: false,
      minimumContribution: '',
      name: '',
      startDate: '',
    },
  });
  const cycleType = useWatch({ control, name: 'cycleType' });
  const create = useMutation({
    mutationFn: (v: z.output<typeof createSchema>) =>
      post<BucketView>(`/groups/${group.groupId}/buckets`, {
        name: v.name,
        description: v.description || undefined,
        type: v.type,
        cycleType: v.cycleType,
        startDate: v.startDate,
        terms: {
          mandatory: v.mandatory,
          minimumContribution: v.minimumContribution,
          contributionFrequency: v.contributionFrequency,
          withdrawable: v.withdrawable,
          endDate: v.cycleType === 'FIXED_TERM' && v.endDate ? v.endDate : null,
          latePenaltyRule: null,
        } satisfies BucketTerms,
      }),
    onSuccess: () => {
      invalidateFunds(queryClient, group.groupId);
      onDone();
    },
  });

  return (
    <Card>
      <form noValidate onSubmit={handleSubmit((v) => create.mutate(v))} className="flex flex-col gap-4">
        <h3 className="font-bold">{t('funds.create')}</h3>
        <ErrorMessage error={create.error} />
        <TextField label={t('funds.name')} error={fieldError(t, formState.errors.name)} {...register('name')} />
        <TextField label={t('funds.description')} {...register('description')} />
        <SelectField label={t('funds.type')} {...register('type')}>
          {(['SAVINGS', 'SOCIAL_FUND', 'SHARES'] as const).map((v) => (
            <option key={v} value={v}>
              {t(`funds.${v}`)}
            </option>
          ))}
        </SelectField>
        <SelectField label={t('funds.cycleType')} {...register('cycleType')}>
          <option value="ROLLING">{t('funds.ROLLING')}</option>
          <option value="FIXED_TERM">{t('funds.FIXED_TERM')}</option>
        </SelectField>
        <TextField label={t('funds.startDate')} type="date" error={fieldError(t, formState.errors.startDate)} {...register('startDate')} />
        {cycleType === 'FIXED_TERM' && (
          <TextField label={t('funds.endDate')} type="date" error={fieldError(t, formState.errors.endDate)} {...register('endDate')} />
        )}
        <TermsFields register={(name) => register(name)} amountError={formState.errors.minimumContribution} />
        <div className="flex gap-2">
          <Button type="submit" busy={create.isPending}>
            {t('funds.save')}
          </Button>
          <Button variant="ghost" onClick={onDone}>
            {t('common.cancel')}
          </Button>
        </div>
      </form>
    </Card>
  );
}

function TermsSummary({ terms }: { terms: BucketTerms }) {
  const { t } = useTranslation();
  const yesNo = (v: boolean) => t(v ? 'funds.yes' : 'funds.no');
  return (
    <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 text-sm">
      <dt className="text-muted">{t('funds.minimum')}</dt>
      <dd>{formatRwf(terms.minimumContribution)}</dd>
      <dt className="text-muted">{t('funds.frequency')}</dt>
      <dd>{t(`funds.${terms.contributionFrequency}`)}</dd>
      <dt className="text-muted">{t('funds.mandatory')}</dt>
      <dd>{yesNo(terms.mandatory)}</dd>
      <dt className="text-muted">{t('funds.withdrawable')}</dt>
      <dd>{yesNo(terms.withdrawable)}</dd>
    </dl>
  );
}

function Fund({ fund, manager }: { fund: BucketView; manager: boolean }) {
  const { t, i18n } = useTranslation();
  const group = useGroup();
  const queryClient = useQueryClient();
  const { withStepUp } = useSession();
  const [editing, setEditing] = useState(false);
  const [proposed, setProposed] = useState(false);
  const [rejectReason, setRejectReason] = useState('');
  const base = `/groups/${group.groupId}/buckets/${fund.bucketId}`;

  const { register, handleSubmit, formState } = useForm<z.input<typeof termsSchema>, unknown, z.output<typeof termsSchema>>({
    resolver: zodResolver(termsSchema),
    defaultValues: {
      mandatory: fund.terms.mandatory,
      minimumContribution: fund.terms.minimumContribution.replace(/\.0+$/, ''),
      contributionFrequency: fund.terms.contributionFrequency,
      withdrawable: fund.terms.withdrawable,
    },
  });

  // Changing money terms needs a fresh password (the server asks for it); status changes do not.
  const update = useMutation({
    mutationFn: (body: Record<string, unknown>) => withStepUp(() => patch<BucketView>(base, { version: fund.version, ...body })),
    onSuccess: (_view, body) => {
      if (body.terms) {
        setEditing(false);
        setProposed(true);
      }
      invalidateFunds(queryClient, group.groupId);
    },
  });
  const decide = useMutation({
    mutationFn: (confirm: boolean) =>
      withStepUp(() =>
        confirm
          ? post(`${base}/changes/${fund.pendingChange?.changeId}/confirm`)
          : post(`${base}/changes/${fund.pendingChange?.changeId}/reject`, { reason: rejectReason.trim() }),
      ),
    onSuccess: () => invalidateFunds(queryClient, group.groupId),
  });
  const pending = fund.pendingChange;
  const proposedByMe = pending?.proposedBy === group.myMemberId;

  return (
    <li className="flex flex-col gap-3 rounded-xl border border-line bg-card p-4">
      <div className="flex flex-wrap items-center gap-2">
        <h3 className="font-bold">{fund.name}</h3>
        <Badge>{t(`funds.${fund.type}`)}</Badge>
        {fund.status === 'CLOSED' && <Badge tone="accent">{t('funds.closed')}</Badge>}
      </div>
      {fund.description && <p className="text-sm text-muted">{fund.description}</p>}
      <p className="text-sm text-muted">
        {t('funds.startDate')}: {new Date(`${fund.startDate}T00:00:00`).toLocaleDateString(i18n.language)}
        {fund.terms.endDate && ` · ${t('funds.endDate')}: ${new Date(`${fund.terms.endDate}T00:00:00`).toLocaleDateString(i18n.language)}`}
      </p>
      <TermsSummary terms={fund.terms} />
      <ErrorMessage error={update.error ?? decide.error} />
      {proposed && !pending && <Alert tone="success">{t('funds.proposed')}</Alert>}

      {pending && (
        <section className="flex flex-col gap-2 rounded-lg bg-surface p-3">
          <Alert tone="warning">{t('funds.pending')}</Alert>
          <p className="text-sm font-semibold">{t('funds.proposedValue')}</p>
          <TermsSummary terms={pending.proposed} />
          {manager && !proposedByMe && (
            <>
              <Button busy={decide.isPending} onClick={() => decide.mutate(true)}>
                {t('funds.confirm')}
              </Button>
              <TextField label={t('funds.rejectReason')} value={rejectReason} onChange={(e) => setRejectReason(e.target.value)} maxLength={500} />
              <Button variant="secondary" disabled={!rejectReason.trim()} busy={decide.isPending} onClick={() => decide.mutate(false)}>
                {t('funds.reject')}
              </Button>
            </>
          )}
        </section>
      )}

      {manager && editing && (
        <form
          noValidate
          className="flex flex-col gap-3"
          onSubmit={handleSubmit((v) => update.mutate({ terms: { ...v, endDate: fund.terms.endDate, latePenaltyRule: fund.terms.latePenaltyRule } }))}
        >
          <TermsFields register={(name) => register(name)} amountError={formState.errors.minimumContribution} />
          <div className="flex gap-2">
            <Button type="submit" busy={update.isPending}>
              {t('funds.proposeTerms')}
            </Button>
            <Button variant="ghost" onClick={() => setEditing(false)}>
              {t('common.cancel')}
            </Button>
          </div>
        </form>
      )}
      {manager && !editing && (
        <div className="flex flex-wrap gap-2">
          {fund.status === 'ACTIVE' && (
            <Button variant="secondary" onClick={() => setEditing(true)}>
              {t('funds.editTerms')}
            </Button>
          )}
          <Button variant="ghost" busy={update.isPending} onClick={() => update.mutate({ status: fund.status === 'ACTIVE' ? 'CLOSED' : 'ACTIVE' })}>
            {fund.status === 'ACTIVE' ? t('funds.close') : t('funds.reopen')}
          </Button>
        </div>
      )}
    </li>
  );
}
