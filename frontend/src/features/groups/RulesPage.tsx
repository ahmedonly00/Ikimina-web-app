import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { z } from 'zod';
import { api, get, post } from '../../api/client';
import { canEditSettings, type GroupSettings, type SettingsView } from '../../api/types';
import { useSession } from '../../auth/session';
import { Alert, Button, Card, ErrorMessage, Loading, SelectField, TextField } from '../../components/ui';
import { amountField, fieldError } from '../../lib/forms';
import { formatRwf, toWireAmount } from '../../lib/money';
import { useGroup } from './GroupLayout';

const schema = z.object({
  defaultLocale: z.enum(['rw', 'en']),
  interestRecognition: z.enum(['WHEN_PAID', 'WHEN_DUE']),
  withdrawalsAllowed: z.enum(['true', 'false']),
  withdrawalNoticeDays: z.string().regex(/^(?:[0-9]|[1-9][0-9]|[12][0-9]{2}|3[0-5][0-9]|36[0-5])$/, 'validation.days'),
  exitFee: amountField,
});
type Values = z.infer<typeof schema>;

function toForm(settings: GroupSettings): Values {
  return {
    defaultLocale: settings.defaultLocale,
    interestRecognition: settings.interestRecognition,
    withdrawalsAllowed: settings.withdrawalsAllowed ? 'true' : 'false',
    withdrawalNoticeDays: String(settings.withdrawalNoticeDays),
    exitFee: settings.exitFee.replace(/\.00$/, ''),
  };
}

/**
 * The group's bylaws (spec 17.2). Money rules need a second officer: saving them creates a
 * proposal (HTTP 202) that another officer confirms here, after re-entering their password.
 */
export function RulesPage() {
  const { t } = useTranslation();
  const group = useGroup();
  const queryClient = useQueryClient();
  const { withStepUp } = useSession();
  const [outcome, setOutcome] = useState<'saved' | 'proposed' | null>(null);
  const [rejectReason, setRejectReason] = useState('');
  const settings = useQuery({ queryKey: ['settings', group.groupId], queryFn: () => get<SettingsView>(`/groups/${group.groupId}/settings`) });
  const editable = canEditSettings(group.myRole);
  const refresh = () => void queryClient.invalidateQueries({ queryKey: ['settings', group.groupId] });

  const save = useMutation({
    mutationFn: async (values: Values) => {
      const body = {
        version: settings.data?.version,
        settings: {
          defaultLocale: values.defaultLocale,
          interestRecognition: values.interestRecognition,
          withdrawalsAllowed: values.withdrawalsAllowed === 'true',
          withdrawalNoticeDays: Number.parseInt(values.withdrawalNoticeDays, 10),
          exitFee: toWireAmount(values.exitFee),
        },
      };
      const before = settings.data?.pendingChange?.changeId;
      const result = await withStepUp(() => api<SettingsView>('PUT', `/groups/${group.groupId}/settings`, body));
      return result.pendingChange && result.pendingChange.changeId !== before ? 'proposed' : 'saved';
    },
    onSuccess: (result) => {
      setOutcome(result);
      refresh();
    },
  });
  const decide = useMutation({
    mutationFn: ({ changeId, confirm }: { changeId: string; confirm: boolean }) =>
      withStepUp(() =>
        confirm
          ? post(`/groups/${group.groupId}/settings/changes/${changeId}/confirm`)
          : post(`/groups/${group.groupId}/settings/changes/${changeId}/reject`, { reason: rejectReason.trim() }),
      ),
    onSuccess: refresh,
  });

  if (settings.isPending) {
    return <Loading />;
  }
  if (settings.error || !settings.data) {
    return <ErrorMessage error={settings.error} />;
  }
  const { settings: current, pendingChange } = settings.data;

  return (
    <div className="flex flex-col gap-4">
      {pendingChange && (
        <Card className="border-accent">
          <h2 className="mb-2 font-bold">{t('settings.pendingTitle')}</h2>
          <p className="mb-2 text-sm text-muted">
            {t('settings.pendingBy', { date: new Date(pendingChange.proposedAt).toLocaleString() })}
          </p>
          <table className="mb-3 w-full text-sm">
            <thead>
              <tr className="text-left text-muted">
                <th scope="col" className="py-1" />
                <th scope="col">{t('settings.current')}</th>
                <th scope="col">{t('settings.proposedValue')}</th>
              </tr>
            </thead>
            <tbody>
              <Row label={t('settings.interestRecognition')} now={t(`settings.${current.interestRecognition}`)} next={t(`settings.${pendingChange.proposed.interestRecognition}`)} />
              <Row
                label={t('settings.withdrawalsAllowed')}
                now={current.withdrawalsAllowed ? t('common.yes') : t('common.no')}
                next={pendingChange.proposed.withdrawalsAllowed ? t('common.yes') : t('common.no')}
              />
              <Row label={t('settings.withdrawalNoticeDays')} now={String(current.withdrawalNoticeDays)} next={String(pendingChange.proposed.withdrawalNoticeDays)} />
              <Row label={t('settings.exitFee')} now={formatRwf(current.exitFee)} next={formatRwf(pendingChange.proposed.exitFee)} />
            </tbody>
          </table>
          <ErrorMessage error={decide.error} />
          {editable && (
            <div className="flex flex-col gap-2">
              <Button busy={decide.isPending} onClick={() => decide.mutate({ changeId: pendingChange.changeId, confirm: true })}>
                {t('settings.confirm')}
              </Button>
              <TextField label={t('settings.rejectReason')} value={rejectReason} onChange={(e) => setRejectReason(e.target.value)} maxLength={500} />
              <Button
                variant="danger"
                disabled={!rejectReason.trim()}
                busy={decide.isPending}
                onClick={() => decide.mutate({ changeId: pendingChange.changeId, confirm: false })}
              >
                {t('settings.reject')}
              </Button>
            </div>
          )}
        </Card>
      )}
      <Card>
        <h2 className="mb-3 text-lg font-bold">{t('settings.title')}</h2>
        {outcome === 'saved' && <Alert tone="success">{t('settings.saved')}</Alert>}
        {outcome === 'proposed' && <Alert tone="warning">{t('settings.proposed')}</Alert>}
        {!editable && <p className="mb-3 text-sm text-muted">{t('settings.readOnly')}</p>}
        <RulesForm key={settings.data.version} initial={toForm(current)} editable={editable} busy={save.isPending} error={save.error} onSave={(values) => save.mutate(values)} />
      </Card>
    </div>
  );
}

function Row({ label, now, next }: { label: string; now: string; next: string }) {
  return (
    <tr className={now === next ? '' : 'font-semibold'}>
      <th scope="row" className="py-1 pr-2 text-left font-normal">
        {label}
      </th>
      <td>{now}</td>
      <td>{next}</td>
    </tr>
  );
}

function RulesForm({ initial, editable, busy, error, onSave }: { initial: Values; editable: boolean; busy: boolean; error: unknown; onSave: (values: Values) => void }) {
  const { t } = useTranslation();
  const { register, handleSubmit, formState } = useForm<Values>({ resolver: zodResolver(schema), defaultValues: initial });
  return (
    <form noValidate onSubmit={handleSubmit(onSave)} className="mt-3 flex flex-col gap-4">
      <ErrorMessage error={error} />
      <fieldset disabled={!editable} className="flex flex-col gap-4">
        <SelectField label={t('settings.defaultLocale')} {...register('defaultLocale')}>
          <option value="rw">{t('lang.rw')}</option>
          <option value="en">{t('lang.en')}</option>
        </SelectField>
        <fieldset className="flex flex-col gap-4 rounded-lg border border-line p-3">
          <legend className="px-1 font-semibold">{t('settings.moneyRules')}</legend>
          <p className="text-sm text-muted">{t('settings.moneyRulesNote')}</p>
          <SelectField label={t('settings.interestRecognition')} {...register('interestRecognition')}>
            <option value="WHEN_PAID">{t('settings.WHEN_PAID')}</option>
            <option value="WHEN_DUE">{t('settings.WHEN_DUE')}</option>
          </SelectField>
          <SelectField label={t('settings.withdrawalsAllowed')} {...register('withdrawalsAllowed')}>
            <option value="false">{t('common.no')}</option>
            <option value="true">{t('common.yes')}</option>
          </SelectField>
          <TextField label={t('settings.withdrawalNoticeDays')} inputMode="numeric" error={fieldError(t, formState.errors.withdrawalNoticeDays)} {...register('withdrawalNoticeDays')} />
          <TextField label={t('settings.exitFee')} inputMode="decimal" error={fieldError(t, formState.errors.exitFee)} {...register('exitFee')} />
        </fieldset>
        {editable && (
          <Button type="submit" busy={busy}>
            {t('settings.save')}
          </Button>
        )}
      </fieldset>
    </form>
  );
}
