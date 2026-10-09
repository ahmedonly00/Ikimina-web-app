import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { Link, useLocation, useNavigate } from 'react-router';
import { z } from 'zod';
import { post } from '../../api/client';
import { Button, Card, ErrorMessage, PageTitle, SelectField, TextField } from '../../components/ui';
import { fieldError, passwordField, phoneField, requiredText } from '../../lib/forms';
import { normalisePhone } from '../../lib/phone';

const schema = z.object({
  fullName: requiredText(200),
  phone: phoneField,
  password: passwordField,
  locale: z.enum(['rw', 'en']),
  acceptTerms: z.literal(true, { message: 'validation.terms' }),
});
type Values = z.infer<typeof schema>;

/** Step 1 of registration: the account is created only once the SMS code comes back (step 2). */
export function RegisterPage() {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const { register, handleSubmit, formState } = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { locale: i18n.language === 'en' ? 'en' : 'rw' },
  });

  const start = useMutation({
    mutationFn: (values: Values) => post('/auth/register', { ...values, phone: normalisePhone(values.phone) }),
    onSuccess: (_, values) =>
      navigate('/verify', { state: { phone: normalisePhone(values.phone), from: (location.state as { from?: string } | null)?.from } }),
  });

  return (
    <>
      <PageTitle>{t('auth.register.title')}</PageTitle>
      <Card>
        <form noValidate onSubmit={handleSubmit((values) => start.mutate(values))} className="flex flex-col gap-4">
          <ErrorMessage error={start.error} />
          <TextField label={t('auth.register.fullName')} autoComplete="name" error={fieldError(t, formState.errors.fullName)} {...register('fullName')} />
          <TextField
            label={t('auth.register.phone')}
            type="tel"
            inputMode="tel"
            autoComplete="tel"
            placeholder="0788 123 456"
            error={fieldError(t, formState.errors.phone)}
            {...register('phone')}
          />
          <TextField
            label={t('auth.register.password')}
            type="password"
            autoComplete="new-password"
            hint={t('auth.register.passwordHint')}
            error={fieldError(t, formState.errors.password)}
            {...register('password')}
          />
          <SelectField label={t('auth.register.language')} {...register('locale')}>
            <option value="rw">{t('lang.rw')}</option>
            <option value="en">{t('lang.en')}</option>
          </SelectField>
          <div className="flex flex-col gap-1">
            <label className="flex min-h-11 items-start gap-3">
              <input type="checkbox" className="mt-1 size-5 accent-brand" {...register('acceptTerms')} />
              <span>{t('auth.register.acceptTerms')}</span>
            </label>
            <p className="text-sm text-muted">{t('auth.register.termsDraft')}</p>
            {formState.errors.acceptTerms && (
              <p role="alert" className="text-sm font-medium text-danger">
                {fieldError(t, formState.errors.acceptTerms)}
              </p>
            )}
          </div>
          <Button type="submit" block busy={start.isPending}>
            {t('auth.register.submit')}
          </Button>
        </form>
      </Card>
      <p className="mt-4 text-center">
        {t('auth.register.haveAccount')}{' '}
        <Link to="/login" state={location.state} className="font-semibold text-brand underline">
          {t('auth.register.signIn')}
        </Link>
      </p>
    </>
  );
}
