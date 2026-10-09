import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router';
import { z } from 'zod';
import { post } from '../../api/client';
import type { GroupView } from '../../api/types';
import { Button, Card, ErrorMessage, PageTitle, TextField } from '../../components/ui';
import { fieldError, requiredText } from '../../lib/forms';

const schema = z.object({
  name: requiredText(200),
  district: z.string().trim().max(80).optional(),
  sector: z.string().trim().max(80).optional(),
});
type Values = z.infer<typeof schema>;

export function CreateGroupPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { register, handleSubmit, formState } = useForm<Values>({ resolver: zodResolver(schema) });

  const create = useMutation({
    mutationFn: (values: Values) =>
      post<GroupView>('/groups', {
        name: values.name,
        district: values.district || undefined,
        sector: values.sector || undefined,
      }),
    onSuccess: (group) => {
      void queryClient.invalidateQueries({ queryKey: ['myGroups'] });
      navigate(`/groups/${group.groupId}`);
    },
  });

  return (
    <>
      <PageTitle subtitle={t('groups.create.hint')}>{t('groups.create.title')}</PageTitle>
      <Card>
        <form noValidate onSubmit={handleSubmit((values) => create.mutate(values))} className="flex flex-col gap-4">
          <ErrorMessage error={create.error} />
          <TextField label={t('groups.create.name')} error={fieldError(t, formState.errors.name)} {...register('name')} />
          <TextField label={`${t('groups.create.district')} (${t('common.optional')})`} {...register('district')} />
          <TextField label={`${t('groups.create.sector')} (${t('common.optional')})`} {...register('sector')} />
          <Button type="submit" block busy={create.isPending}>
            {t('groups.create.submit')}
          </Button>
        </form>
      </Card>
    </>
  );
}
