import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import { get } from '../../api/client';
import type { MyGroup } from '../../api/types';
import { Badge, Card, ErrorMessage, Loading, PageTitle } from '../../components/ui';

export function MyGroupsPage() {
  const { t } = useTranslation();
  const groups = useQuery({ queryKey: ['myGroups'], queryFn: () => get<MyGroup[]>('/me/groups') });

  return (
    <>
      <PageTitle>{t('groups.mine.title')}</PageTitle>
      {groups.isPending && <Loading />}
      <ErrorMessage error={groups.error} />
      {groups.data?.length === 0 && (
        <Card>
          <p className="text-muted">{t('groups.mine.empty')}</p>
        </Card>
      )}
      <ul className="flex flex-col gap-3">
        {groups.data?.map((group) => (
          <li key={group.groupId}>
            <Link to={`/groups/${group.groupId}`} className="block rounded-xl border border-line bg-card p-4 shadow-sm hover:border-brand">
              <span className="block text-lg font-semibold text-ink">{group.name}</span>
              <span className="mt-1 flex flex-wrap items-center gap-2 text-sm text-muted">
                <Badge tone="brand">{t(`roles.${group.role}`)}</Badge>
                {t('groups.mine.memberNumber', { number: group.memberNumber })}
              </span>
            </Link>
          </li>
        ))}
      </ul>
      <Link
        to="/groups/new"
        className="mt-6 flex min-h-11 items-center justify-center rounded-lg bg-brand px-4 font-semibold text-on-brand hover:bg-brand-strong"
      >
        {t('groups.mine.create')}
      </Link>
    </>
  );
}
