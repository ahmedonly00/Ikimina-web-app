import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { get, post } from '../../api/client';
import { canViewMembers, isOffice, type MemberView, type Page, type TransferView } from '../../api/types';
import { useSession } from '../../auth/session';
import { Alert, Button, Card, ErrorMessage, Loading, SelectField } from '../../components/ui';
import { useGroup } from './GroupLayout';

/** Transfer of office (spec 2.2): the holder offers, the recipient accepts; nothing changes in between. */
export function OfficesPage() {
  const { t } = useTranslation();
  const group = useGroup();
  const queryClient = useQueryClient();
  const { withStepUp } = useSession();
  const [to, setTo] = useState('');
  const [offered, setOffered] = useState(false);
  const holder = isOffice(group.myRole);

  const transfers = useQuery({ queryKey: ['transfers', group.groupId], queryFn: () => get<TransferView[]>(`/groups/${group.groupId}/offices/transfers`) });
  const members = useQuery({
    queryKey: ['members', group.groupId, 'all'],
    queryFn: () => get<Page<MemberView>>(`/groups/${group.groupId}/members?page=0&size=200`),
    enabled: holder && canViewMembers(group.myRole),
  });
  const numbers = new Map(members.data?.items.map((m) => [m.memberId, m.memberNumber]) ?? []);
  const label = (memberId: string) => (memberId === group.myMemberId ? t('members.you') : numbers.get(memberId) ?? '…');

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['transfers', group.groupId] });
    void queryClient.invalidateQueries({ queryKey: ['group', group.groupId] });
    void queryClient.invalidateQueries({ queryKey: ['members', group.groupId] });
  };
  const offer = useMutation({
    mutationFn: () => withStepUp(() => post<TransferView>(`/groups/${group.groupId}/offices/transfer`, { role: group.myRole, toMemberId: to })),
    onSuccess: () => {
      setOffered(true);
      refresh();
    },
  });
  const act = useMutation({
    mutationFn: ({ transferId, action }: { transferId: string; action: 'accept' | 'decline' | 'cancel' }) =>
      post(`/groups/${group.groupId}/offices/transfers/${transferId}/${action}`),
    onSuccess: refresh,
  });

  const candidates = members.data?.items.filter((m) => m.status === 'ACTIVE' && !isOffice(m.role)) ?? [];

  return (
    <div className="flex flex-col gap-4">
      <Card>
        <h2 className="mb-1 text-lg font-bold">{t('offices.title')}</h2>
        <p className="mb-3 text-sm text-muted">{t('offices.intro')}</p>
        {!holder ? (
          <p className="text-muted">{t('offices.notHolder')}</p>
        ) : (
          <div className="flex flex-col gap-3">
            {offered && <Alert tone="success">{t('offices.offered')}</Alert>}
            <ErrorMessage error={offer.error} />
            <p>
              {t('offices.role')}: <strong>{t(`roles.${group.myRole}`)}</strong>
            </p>
            <SelectField label={t('offices.to')} value={to} onChange={(e) => setTo(e.target.value)}>
              <option value="" />
              {candidates.map((m) => (
                <option key={m.memberId} value={m.memberId}>
                  {m.memberNumber} · {m.fullName}
                </option>
              ))}
            </SelectField>
            <Button disabled={!to} busy={offer.isPending} onClick={() => offer.mutate()}>
              {t('offices.submit')}
            </Button>
          </div>
        )}
      </Card>
      <section aria-labelledby="transfers-title">
        <h2 id="transfers-title" className="mb-2 text-lg font-bold">
          {t('offices.pendingTitle')}
        </h2>
        {transfers.isPending && <Loading />}
        <ErrorMessage error={transfers.error ?? act.error} />
        {transfers.data?.length === 0 && <p className="text-muted">{t('offices.none')}</p>}
        <ul className="flex flex-col gap-2">
          {transfers.data?.map((transfer) => (
            <li key={transfer.transferId} className="rounded-xl border border-line bg-card p-3">
              <p className="font-semibold">
                {t('offices.line', { role: t(`roles.${transfer.role}`), from: label(transfer.fromMemberId), to: label(transfer.toMemberId) })}
              </p>
              <div className="mt-2 flex flex-wrap gap-2">
                {transfer.toMemberId === group.myMemberId && (
                  <>
                    <Button busy={act.isPending} onClick={() => act.mutate({ transferId: transfer.transferId, action: 'accept' })}>
                      {t('offices.accept')}
                    </Button>
                    <Button variant="secondary" busy={act.isPending} onClick={() => act.mutate({ transferId: transfer.transferId, action: 'decline' })}>
                      {t('offices.decline')}
                    </Button>
                  </>
                )}
                {transfer.fromMemberId === group.myMemberId && (
                  <Button variant="secondary" busy={act.isPending} onClick={() => act.mutate({ transferId: transfer.transferId, action: 'cancel' })}>
                    {t('offices.cancel')}
                  </Button>
                )}
              </div>
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}
