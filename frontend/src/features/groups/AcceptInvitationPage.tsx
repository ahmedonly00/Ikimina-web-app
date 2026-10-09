import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { useNavigate, useSearchParams } from 'react-router';
import { post } from '../../api/client';
import { Alert, Button, Card, ErrorMessage, PageTitle } from '../../components/ui';

/** Opened from the SMS link: /invitations/accept?group=<id>&token=<token>. */
export function AcceptInvitationPage() {
  const { t } = useTranslation();
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const groupId = params.get('group');
  const token = params.get('token');

  const accept = useMutation({
    mutationFn: () => post<{ groupId: string; memberId: string }>(`/groups/${groupId}/invitations/accept`, { token }),
    onSuccess: (joined) => {
      void queryClient.invalidateQueries({ queryKey: ['myGroups'] });
      navigate(`/groups/${joined.groupId}`, { replace: true });
    },
  });

  return (
    <>
      <PageTitle>{t('accept.title')}</PageTitle>
      <Card>
        {!groupId || !token ? (
          <Alert tone="danger">{t('accept.incomplete')}</Alert>
        ) : (
          <div className="flex flex-col gap-4">
            <p className="text-muted">{t('accept.intro')}</p>
            <ErrorMessage error={accept.error} />
            <Button block busy={accept.isPending} onClick={() => accept.mutate()}>
              {t('accept.submit')}
            </Button>
          </div>
        )}
      </Card>
    </>
  );
}
