import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { get, patch } from '../../api/client';
import type { Locale, Profile } from '../../api/types';
import { Alert, Button, Card, ErrorMessage, Loading, PageTitle, SelectField, TextField } from '../../components/ui';
import { changeLanguage } from '../../i18n';
import { displayPhone } from '../../lib/phone';

export function ProfilePage() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const profile = useQuery({ queryKey: ['me'], queryFn: () => get<Profile>('/me') });

  if (profile.isPending) {
    return <Loading />;
  }
  if (profile.error || !profile.data) {
    return <ErrorMessage error={profile.error} />;
  }
  return (
    <>
      <PageTitle>{t('profile.title')}</PageTitle>
      <ProfileForm
        profile={profile.data}
        onSaved={(updated) => {
          queryClient.setQueryData(['me'], updated);
          changeLanguage(updated.locale);
        }}
      />
    </>
  );
}

function ProfileForm({ profile, onSaved }: { profile: Profile; onSaved: (profile: Profile) => void }) {
  const { t } = useTranslation();
  const [fullName, setFullName] = useState(profile.fullName);
  const [locale, setLocale] = useState<Locale>(profile.locale);
  const save = useMutation({
    mutationFn: () => patch<Profile>('/me', { fullName: fullName.trim(), locale }),
    onSuccess: onSaved,
  });

  return (
    <Card>
      <form
        onSubmit={(event) => {
          event.preventDefault();
          save.mutate();
        }}
        className="flex flex-col gap-4"
      >
        {save.isSuccess && <Alert tone="success">{t('profile.saved')}</Alert>}
        <ErrorMessage error={save.error} />
        <TextField label={t('profile.phone')} value={displayPhone(profile.phone)} readOnly />
        <TextField label={t('profile.fullName')} value={fullName} onChange={(e) => setFullName(e.target.value)} required maxLength={200} autoComplete="name" />
        <SelectField label={t('profile.language')} value={locale} onChange={(e) => setLocale(e.target.value as Locale)}>
          <option value="rw">{t('lang.rw')}</option>
          <option value="en">{t('lang.en')}</option>
        </SelectField>
        <Button type="submit" busy={save.isPending} disabled={!fullName.trim()}>
          {t('profile.save')}
        </Button>
      </form>
    </Card>
  );
}
