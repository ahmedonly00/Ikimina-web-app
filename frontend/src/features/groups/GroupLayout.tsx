import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { NavLink, Outlet, useOutletContext, useParams } from 'react-router';
import { get } from '../../api/client';
import { canApproveMoney, canManageMembers, canRecordContributions, canViewMembers, type GroupView } from '../../api/types';
import { Badge, ErrorMessage, Loading } from '../../components/ui';

/** The group being viewed, shared with the tab pages. */
export function useGroup(): GroupView {
  return useOutletContext<GroupView>();
}

/**
 * A group's pages. Tabs follow the caller's role (spec 18.1: role-aware navigation); hiding
 * a tab is a convenience only - the server refuses anything the role does not allow.
 */
export function GroupLayout() {
  const { t } = useTranslation();
  const { groupId = '' } = useParams();
  const group = useQuery({ queryKey: ['group', groupId], queryFn: () => get<GroupView>(`/groups/${groupId}`) });

  if (group.isPending) {
    return <Loading />;
  }
  if (group.error || !group.data) {
    return <ErrorMessage error={group.error} />;
  }

  const role = group.data.myRole;
  const tabs = [
    { to: '', label: t('groups.nav.overview'), end: true, show: true },
    { to: 'savings', label: t('groups.nav.savings'), show: true },
    { to: 'record', label: t('groups.nav.record'), show: canRecordContributions(role) },
    { to: 'loans', label: t('groups.nav.loans'), show: true },
    { to: 'funds', label: t('groups.nav.funds'), show: true },
    { to: 'loan-products', label: t('groups.nav.loanProducts'), show: true },
    { to: 'reversals', label: t('groups.nav.reversals'), show: canApproveMoney(role) },
    { to: 'members', label: t('groups.nav.members'), show: canViewMembers(role) },
    { to: 'invite', label: t('groups.nav.invite'), show: canManageMembers(role) },
    { to: 'rules', label: t('groups.nav.rules'), show: true },
    { to: 'offices', label: t('groups.nav.offices'), show: true },
  ].filter((tab) => tab.show);

  return (
    <>
      <header className="mb-4">
        <h1 className="text-2xl font-bold text-ink">{group.data.name}</h1>
        <p className="mt-1 flex items-center gap-2 text-sm text-muted">
          {t('groups.home.yourRole')}: <Badge tone="brand">{t(`roles.${role}`)}</Badge>
        </p>
      </header>
      <nav aria-label={group.data.name} className="-mx-4 mb-4 overflow-x-auto px-4">
        <ul className="flex gap-1 border-b border-line">
          {tabs.map((tab) => (
            <li key={tab.to}>
              <NavLink
                to={tab.to}
                end={tab.end}
                className={({ isActive }) =>
                  `inline-flex min-h-11 items-center whitespace-nowrap border-b-2 px-3 font-semibold ${
                    isActive ? 'border-brand text-brand' : 'border-transparent text-muted hover:text-ink'
                  }`
                }
              >
                {tab.label}
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>
      <Outlet context={group.data} />
    </>
  );
}

export function GroupOverview() {
  const { t } = useTranslation();
  const group = useGroup();
  const location = [group.village, group.cell, group.sector, group.district, group.province].filter(Boolean).join(', ');
  return (
    <div className="flex flex-col gap-4">
      <dl className="grid grid-cols-2 gap-3">
        <div className="rounded-xl border border-line bg-card p-4">
          <dt className="text-sm text-muted">{t('groups.home.activeMembers')}</dt>
          <dd className="text-2xl font-bold">{group.activeMembers}</dd>
        </div>
        <div className="rounded-xl border border-line bg-card p-4">
          <dt className="text-sm text-muted">{t('groups.home.yourRole')}</dt>
          <dd className="text-lg font-bold">{t(`roles.${group.myRole}`)}</dd>
        </div>
        {location && (
          <div className="col-span-2 rounded-xl border border-line bg-card p-4">
            <dt className="text-sm text-muted">{t('groups.home.location')}</dt>
            <dd className="font-semibold">{location}</dd>
          </div>
        )}
      </dl>
      {canManageMembers(group.myRole) && (
        <section className="rounded-xl border border-line bg-card p-4">
          <h2 className="mb-2 font-bold">{t('groups.home.nextSteps')}</h2>
          <ul className="list-inside list-disc text-muted">
            <li>{t('groups.home.stepInvite')}</li>
            <li>{t('groups.home.stepOffices')}</li>
            <li>{t('groups.home.stepRules')}</li>
          </ul>
        </section>
      )}
    </div>
  );
}
