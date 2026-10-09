import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { z } from 'zod';
import { get, post } from '../../api/client';
import type { InvitationView } from '../../api/types';
import { Alert, Button, Card, ErrorMessage, Loading, SelectField, TextField } from '../../components/ui';
import { fieldError, phoneField } from '../../lib/forms';
import { displayPhone, normalisePhone } from '../../lib/phone';
import { useGroup } from './GroupLayout';

const schema = z.object({ phone: phoneField, role: z.enum(['MEMBER', 'AUDITOR']) });
type Values = z.infer<typeof schema>;

export function InvitePage() {
  const { t, i18n } = useTranslation();
  const group = useGroup();
  const queryClient = useQueryClient();
  const [sentTo, setSentTo] = useState<string | null>(null);
  const { register, handleSubmit, formState, reset } = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { role: 'MEMBER' },
  });
  const pending = useQuery({
    queryKey: ['invitations', group.groupId],
    queryFn: () => get<InvitationView[]>(`/groups/${group.groupId}/invitations`),
  });

  const invite = useMutation({
    mutationFn: (values: Values) =>
      post<InvitationView>(`/groups/${group.groupId}/invitations`, { phone: normalisePhone(values.phone), role: values.role }),
    onSuccess: (invitation) => {
      setSentTo(invitation.phone);
      reset({ phone: '', role: 'MEMBER' });
      void queryClient.invalidateQueries({ queryKey: ['invitations', group.groupId] });
    },
  });
  const revoke = useMutation({
    mutationFn: (invitationId: string) => post(`/groups/${group.groupId}/invitations/${invitationId}/revoke`),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['invitations', group.groupId] }),
  });

  return (
    <div className="flex flex-col gap-4">
      <Card>
        <h2 className="mb-1 text-lg font-bold">{t('invite.title')}</h2>
        <p className="mb-3 text-sm text-muted">{t('invite.intro')}</p>
        <form noValidate onSubmit={handleSubmit((values) => invite.mutate(values))} className="flex flex-col gap-4">
          {sentTo && <Alert tone="success">{t('invite.sent', { phone: displayPhone(sentTo) })}</Alert>}
          <ErrorMessage error={invite.error} />
          <TextField
            label={t('invite.phone')}
            type="tel"
            inputMode="tel"
            placeholder="0788 123 456"
            error={fieldError(t, formState.errors.phone)}
            {...register('phone')}
          />
          <SelectField label={t('invite.role')} {...register('role')}>
            <option value="MEMBER">{t('roles.MEMBER')}</option>
            <option value="AUDITOR">{t('roles.AUDITOR')}</option>
          </SelectField>
          <Button type="submit" busy={invite.isPending}>
            {t('invite.submit')}
          </Button>
        </form>
      </Card>
      <section aria-labelledby="pending-title">
        <h2 id="pending-title" className="mb-2 text-lg font-bold">
          {t('invite.pendingTitle')}
        </h2>
        {pending.isPending && <Loading />}
        <ErrorMessage error={pending.error ?? revoke.error} />
        {pending.data?.length === 0 && <p className="text-muted">{t('invite.none')}</p>}
        <ul className="flex flex-col gap-2">
          {pending.data?.map((invitation) => (
            <li key={invitation.invitationId} className="flex flex-wrap items-center gap-2 rounded-xl border border-line bg-card p-3">
              <span className="font-semibold">{displayPhone(invitation.phone)}</span>
              <span className="text-sm text-muted">{t(`roles.${invitation.role}`)}</span>
              <span className="text-sm text-muted">
                {t('invite.expires', { date: new Date(invitation.expiresAt).toLocaleDateString(i18n.language) })}
              </span>
              <Button variant="ghost" className="ml-auto" onClick={() => revoke.mutate(invitation.invitationId)}>
                {t('invite.revoke')}
              </Button>
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}
