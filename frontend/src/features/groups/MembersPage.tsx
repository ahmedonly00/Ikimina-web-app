import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import { get, patch } from '../../api/client';
import { canManageMembers, canSeeEveryonesSavings, isOffice, type GroupRole, type MemberView, type Page } from '../../api/types';
import { useSession } from '../../auth/session';
import { Alert, Badge, Button, ErrorMessage, Loading, SelectField, TextField } from '../../components/ui';
import { displayPhone } from '../../lib/phone';
import { useGroup } from './GroupLayout';

const PAGE_SIZE = 50;
const ASSIGNABLE: GroupRole[] = ['MEMBER', 'AUDITOR', 'TREASURER', 'SECRETARY'];

export function MembersPage() {
  const { t } = useTranslation();
  const group = useGroup();
  const [page, setPage] = useState(0);
  const members = useQuery({
    queryKey: ['members', group.groupId, page],
    queryFn: () => get<Page<MemberView>>(`/groups/${group.groupId}/members?page=${page}&size=${PAGE_SIZE}`),
  });
  const [open, setOpen] = useState<string | null>(null);

  if (members.isPending) {
    return <Loading />;
  }
  if (members.error || !members.data) {
    return <ErrorMessage error={members.error} />;
  }
  const occupied = new Set(members.data.items.filter((m) => m.status === 'ACTIVE' && isOffice(m.role)).map((m) => m.role));

  return (
    <section aria-labelledby="members-title">
      <h2 id="members-title" className="mb-1 text-lg font-bold">
        {t('members.title')}
      </h2>
      <p className="mb-3 text-sm text-muted">{t('members.count', { count: members.data.totalItems })}</p>
      <ul className="flex flex-col gap-2">
        {members.data.items.map((member) => (
          <li key={member.memberId} className="rounded-xl border border-line bg-card p-3">
            <div className="flex flex-wrap items-center gap-2">
              <span className="font-mono text-sm text-muted">{member.memberNumber}</span>
              <span className="font-semibold">
                {member.fullName}
                {member.memberId === group.myMemberId && <span className="text-muted"> ({t('members.you')})</span>}
              </span>
              <Badge tone={isOffice(member.role) ? 'brand' : 'neutral'}>{t(`roles.${member.role}`)}</Badge>
              {member.status !== 'ACTIVE' && <Badge tone="accent">{t(`memberStatus.${member.status}`)}</Badge>}
            </div>
            <p className="text-sm text-muted">{displayPhone(member.phone)}</p>
            {canSeeEveryonesSavings(group.myRole) && member.status !== 'INVITED' && (
              <Link to={`${member.memberId}/savings`} className="inline-flex min-h-11 items-center font-semibold text-brand underline">
                {t('savings.viewSavings')}
              </Link>
            )}
            {canManageMembers(group.myRole) && member.memberId !== group.myMemberId && member.status !== 'REMOVED' && (
              <>
                <Button
                  variant="ghost"
                  className="mt-1 -ml-2"
                  aria-expanded={open === member.memberId}
                  onClick={() => setOpen(open === member.memberId ? null : member.memberId)}
                >
                  {t('members.manage')}
                </Button>
                {open === member.memberId && <ManageMember member={member} occupied={occupied} onDone={() => setOpen(null)} />}
              </>
            )}
          </li>
        ))}
      </ul>
      {members.data.totalPages > 1 && (
        <div className="mt-4 flex justify-between">
          <Button variant="secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>
            {t('members.previous')}
          </Button>
          <Button variant="secondary" disabled={page + 1 >= members.data.totalPages} onClick={() => setPage(page + 1)}>
            {t('members.next')}
          </Button>
        </div>
      )}
    </section>
  );
}

function ManageMember({ member, occupied, onDone }: { member: MemberView; occupied: Set<GroupRole>; onDone: () => void }) {
  const { t } = useTranslation();
  const group = useGroup();
  const { withStepUp } = useSession();
  const queryClient = useQueryClient();
  const [role, setRole] = useState<GroupRole>(member.role);
  const [reason, setReason] = useState('');
  const [done, setDone] = useState(false);

  const update = useMutation({
    mutationFn: (change: { role?: GroupRole; status?: string; reason?: string }) =>
      withStepUp(() => patch<MemberView>(`/groups/${group.groupId}/members/${member.memberId}`, change)),
    onSuccess: () => {
      setDone(true);
      void queryClient.invalidateQueries({ queryKey: ['members', group.groupId] });
      void queryClient.invalidateQueries({ queryKey: ['group', group.groupId] });
    },
  });

  if (isOffice(member.role)) {
    return <p className="mt-2 text-sm text-muted">{t('members.officeNote')}</p>;
  }

  return (
    <div className="mt-2 flex flex-col gap-3 border-t border-line pt-3">
      {done && <Alert tone="success">{t('members.updated')}</Alert>}
      <ErrorMessage error={update.error} />
      <div className="flex flex-wrap items-end gap-2">
        <div className="min-w-48 flex-1">
          <SelectField label={t('members.changeRole')} value={role} onChange={(e) => setRole(e.target.value as GroupRole)}>
            {ASSIGNABLE.filter((r) => r === member.role || !occupied.has(r)).map((r) => (
              <option key={r} value={r}>
                {t(`roles.${r}`)}
              </option>
            ))}
          </SelectField>
        </div>
        <Button variant="secondary" disabled={role === member.role} busy={update.isPending} onClick={() => update.mutate({ role })}>
          {t('common.save')}
        </Button>
      </div>
      <p className="text-sm text-muted">{t('members.officeNote')}</p>
      <div className="flex flex-wrap gap-2">
        {member.status === 'ACTIVE' ? (
          <Button variant="secondary" busy={update.isPending} onClick={() => update.mutate({ status: 'SUSPENDED' })}>
            {t('members.suspend')}
          </Button>
        ) : (
          <Button variant="secondary" busy={update.isPending} onClick={() => update.mutate({ status: 'ACTIVE' })}>
            {t('members.reactivate')}
          </Button>
        )}
      </div>
      <div className="flex flex-col gap-2">
        <TextField label={t('members.removeReason')} value={reason} onChange={(e) => setReason(e.target.value)} maxLength={500} />
        <Button
          variant="danger"
          disabled={!reason.trim()}
          busy={update.isPending}
          onClick={() => update.mutate({ status: 'REMOVED', reason: reason.trim() })}
        >
          {t('members.removeConfirm')}
        </Button>
      </div>
      <Button variant="ghost" onClick={onDone}>
        {t('common.close')}
      </Button>
    </div>
  );
}
