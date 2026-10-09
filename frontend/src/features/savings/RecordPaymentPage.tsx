import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { z } from 'zod';
import { get, postIdempotent } from '../../api/client';
import type { BucketView, ContributionView, MemberView, Page } from '../../api/types';
import { Alert, Button, Card, ErrorMessage, Loading, SelectField, TextField } from '../../components/ui';
import { fieldError } from '../../lib/forms';
import { useIdempotencyKey } from '../../lib/idempotency';
import { formatRwf } from '../../lib/money';
import { useGroup } from '../groups/GroupLayout';

const today = () => {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
};

const schema = z
  .object({
    memberId: z.string().min(1, 'validation.required'),
    bucketId: z.string().min(1, 'validation.required'),
    // RWF has no minor unit in practice (spec 4.3): whole francs only, as the server requires.
    amount: z.string().transform((v) => v.replace(/[\s,]/g, '')).pipe(z.string().regex(/^[1-9][0-9]{0,15}$/, 'validation.amountWhole')),
    method: z.enum(['CASH', 'MOMO_MANUAL', 'BANK']),
    externalRef: z.string().trim().max(100).optional(),
    businessDate: z.string().min(1, 'validation.required'),
  })
  .refine((v) => v.method !== 'MOMO_MANUAL' || (v.externalRef ?? '').length > 0, { path: ['externalRef'], message: 'validation.required' });
type Input = z.input<typeof schema>;
type Values = z.output<typeof schema>;

/**
 * Recording a payment (spec 18.4 flow 1; Treasurer only - CONTRIBUTION_RECORD). The Idempotency-Key
 * stays the same until the payment is recorded, so tapping twice or retrying on a bad connection
 * records it once (H7).
 */
export function RecordPaymentPage() {
  const { t } = useTranslation();
  const group = useGroup();
  const queryClient = useQueryClient();
  const idempotency = useIdempotencyKey();
  const [recorded, setRecorded] = useState<{ view: ContributionView; name: string } | null>(null);

  const members = useQuery({
    queryKey: ['members', group.groupId, 'all'],
    queryFn: () => get<Page<MemberView>>(`/groups/${group.groupId}/members?page=0&size=200`),
  });
  const funds = useQuery({ queryKey: ['buckets', group.groupId], queryFn: () => get<BucketView[]>(`/groups/${group.groupId}/buckets`) });
  const { register, handleSubmit, formState, control, reset } = useForm<Input, unknown, Values>({
    resolver: zodResolver(schema),
    defaultValues: { method: 'CASH', businessDate: today(), memberId: '', bucketId: '', amount: '' },
  });
  const method = useWatch({ control, name: 'method' });

  const record = useMutation({
    mutationFn: (values: Values) =>
      postIdempotent<ContributionView>(
        `/groups/${group.groupId}/contributions`,
        { ...values, amount: values.amount, externalRef: values.externalRef || undefined },
        idempotency.current(),
      ),
    onSuccess: (view, values) => {
      idempotency.rotate();
      const member = members.data?.items.find((m) => m.memberId === values.memberId);
      setRecorded({ view, name: member?.fullName ?? '' });
      reset({ method: 'CASH', businessDate: today(), memberId: '', bucketId: values.bucketId, amount: '' });
      void queryClient.invalidateQueries({ queryKey: ['balances', group.groupId] });
      void queryClient.invalidateQueries({ queryKey: ['obligations', group.groupId] });
      void queryClient.invalidateQueries({ queryKey: ['transactions', group.groupId] });
    },
  });

  if (members.isPending || funds.isPending) {
    return <Loading />;
  }
  const active = members.data?.items.filter((m) => m.status === 'ACTIVE') ?? [];
  const openFunds = funds.data?.filter((f) => f.status === 'ACTIVE') ?? [];

  return (
    <Card>
      <h2 className="mb-3 text-lg font-bold">{t('record.title')}</h2>
      <form noValidate onSubmit={handleSubmit((values) => record.mutate(values))} className="flex flex-col gap-4">
        {recorded && (
          <Alert tone="success">
            {t('record.recorded', { amount: formatRwf(recorded.view.amount), name: recorded.name, balance: formatRwf(recorded.view.memberBalance) })}
          </Alert>
        )}
        <ErrorMessage error={record.error ?? members.error ?? funds.error} />
        <SelectField label={t('record.member')} error={fieldError(t, formState.errors.memberId)} {...register('memberId')}>
          <option value="">{t('record.choose')}</option>
          {active.map((m) => (
            <option key={m.memberId} value={m.memberId}>
              {m.memberNumber} · {m.fullName}
            </option>
          ))}
        </SelectField>
        <SelectField label={t('record.fund')} error={fieldError(t, formState.errors.bucketId)} {...register('bucketId')}>
          <option value="">{t('record.choose')}</option>
          {openFunds.map((f) => (
            <option key={f.bucketId} value={f.bucketId}>
              {f.name}
            </option>
          ))}
        </SelectField>
        <TextField label={t('record.amount')} inputMode="numeric" error={fieldError(t, formState.errors.amount)} {...register('amount')} />
        <SelectField label={t('record.method')} {...register('method')}>
          <option value="CASH">{t('record.CASH')}</option>
          <option value="MOMO_MANUAL">{t('record.MOMO_MANUAL')}</option>
          <option value="BANK">{t('record.BANK')}</option>
        </SelectField>
        {method !== 'CASH' && (
          <TextField label={t('record.reference')} error={fieldError(t, formState.errors.externalRef)} {...register('externalRef')} />
        )}
        <TextField label={t('record.date')} type="date" max={today()} error={fieldError(t, formState.errors.businessDate)} {...register('businessDate')} />
        <Button type="submit" busy={record.isPending}>
          {t('record.submit')}
        </Button>
      </form>
    </Card>
  );
}
