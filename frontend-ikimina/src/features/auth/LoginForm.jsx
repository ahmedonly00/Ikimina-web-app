import React, { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useLoginMutation, useGetPublicGroupsQuery } from '../../app/api/apiSlice';
import { setCredentials } from './authSlice';
import { useAppDispatch } from '../../app/hooks';
import toast from 'react-hot-toast';
import {
  FiMail,
  FiLock,
  FiArrowLeft,
  FiUsers,
  FiEye,
  FiEyeOff,
  FiTrendingUp,
  FiShield,
  FiPieChart,
} from 'react-icons/fi';
import { motion } from 'framer-motion';

/*
 * The brand panel is a fixed indigo gradient in both themes, so its text is
 * set with explicit white rather than a theme token - a token that flips with
 * the theme would put dark text on the gradient in one of the two modes.
 */
const FEATURES = [
  {
    icon: FiPieChart,
    title: 'Every figure from the ledger',
    body: 'Contributions, loans and fines are recorded once, append-only, and reconciled.',
  },
  {
    icon: FiTrendingUp,
    title: 'Built for group savings',
    body: 'Cycles, payouts and member balances the way an ikimina actually runs.',
  },
  {
    icon: FiShield,
    title: 'Records stay yours',
    body: 'Ikimina keeps the books. Money moves between members directly, never through us.',
  },
];

export const LoginForm = () => {
  const [formData, setFormData] = useState({
    email: '',
    password: '',
    savingsGroupId: '',
  });
  const [showPassword, setShowPassword] = useState(false);

  const {
    data: groups = [],
    isLoading: isLoadingGroups,
    error: groupsError,
  } = useGetPublicGroupsQuery();

  const [errors, setErrors] = useState({});
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [login, { isLoading }] = useLoginMutation();
  const dispatch = useAppDispatch();
  const navigate = useNavigate();

  // Clear validation errors as soon as the user edits the form. The functional
  // setter means this effect never reads the current errors value, so formData
  // is genuinely the only dependency.
  useEffect(() => {
    setErrors(prev => (Object.keys(prev).length > 0 ? {} : prev));
  }, [formData]);

  const handleChange = e => {
    const { name, value } = e.target;
    setFormData(prev => ({ ...prev, [name]: value }));
  };

  const validateForm = () => {
    const newErrors = {};

    if (!formData.email) {
      newErrors.email = 'Email is required';
    } else if (!/\S+@\S+\.\S+/.test(formData.email)) {
      newErrors.email = 'Email is invalid';
    }

    if (!formData.password) {
      newErrors.password = 'Password is required';
    }

    /*
     * The group is deliberately NOT required here. A super admin does not
     * belong to a group, and the backend accepts a group-less login for that
     * role - but this form rejected it before the request was ever sent, so a
     * super admin could not sign in through the UI at all.
     *
     * Whether a group is needed depends on the role, which is only known
     * server-side, so let the server decide: it answers 409 "Savings group is
     * required" for the roles that do need one, and that message is surfaced
     * above the form.
     */
    return newErrors;
  };

  const handleSubmit = async e => {
    e.preventDefault();
    const validationErrors = validateForm();

    if (Object.keys(validationErrors).length > 0) {
      setErrors(validationErrors);
      return;
    }

    try {
      setIsSubmitting(true);

      const userData = await login({
        email: formData.email,
        password: formData.password,
        savingsGroupId: formData.savingsGroupId,
      }).unwrap();

      dispatch(
        setCredentials({
          user: userData.user,
          token: userData.token,
          savingsGroupId: userData.user.savingsGroupId,
          savingsGroupName: groups.find(g => g.id === userData.user.savingsGroupId)?.name || '',
        })
      );

      toast.success('Signed in');
      navigate('/dashboard');
    } catch (err) {
      const errorMessage =
        err?.data?.message || err?.data || 'Login failed. Please check your credentials.';
      toast.error(errorMessage);
      setErrors({ submit: errorMessage });
    } finally {
      setIsSubmitting(false);
    }
  };

  const busy = isLoading || isSubmitting;

  return (
    <div className='flex min-h-screen bg-bg'>
      {/* Brand panel - fixed gradient, explicit white text (see FEATURES note) */}
      <div className='relative hidden w-1/2 flex-col justify-center overflow-hidden bg-gradient-to-br from-indigo-700 via-indigo-600 to-blue-600 p-12 text-white lg:flex xl:p-16'>
        {/* Soft light source, purely decorative */}
        <div
          className='pointer-events-none absolute -right-24 -top-24 h-96 w-96 rounded-full bg-white/10 blur-3xl'
          aria-hidden='true'
        />
        <div
          className='pointer-events-none absolute -bottom-32 -left-16 h-80 w-80 rounded-full bg-blue-400/20 blur-3xl'
          aria-hidden='true'
        />

        <motion.div
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.6 }}
          className='relative max-w-md'
        >
          <span className='inline-flex items-center rounded-full border border-white/25 bg-white/10 px-3 py-1 text-xs font-medium tracking-wide'>
            Ikimina
          </span>

          <h2 className='mt-6 text-4xl font-semibold leading-tight tracking-tight'>
            Savings groups,
            <br />
            kept straight.
          </h2>
          <p className='mt-4 text-lg leading-relaxed text-indigo-100'>
            One shared record of who paid what, and when — for the whole group.
          </p>

          <ul className='mt-12 space-y-7'>
            {FEATURES.map(({ icon: Icon, title, body }) => (
              <li key={title} className='flex gap-4'>
                <span
                  className='flex h-10 w-10 shrink-0 items-center justify-center rounded-lg border border-white/20 bg-white/10'
                  aria-hidden='true'
                >
                  <Icon className='h-5 w-5' />
                </span>
                <div>
                  <h3 className='font-medium'>{title}</h3>
                  <p className='mt-1 text-sm leading-relaxed text-indigo-100'>{body}</p>
                </div>
              </li>
            ))}
          </ul>
        </motion.div>
      </div>

      {/* Sign-in form */}
      <div className='flex w-full items-center justify-center px-4 py-10 sm:px-8 lg:w-1/2'>
        <motion.div
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.45, delay: 0.1 }}
          className='w-full max-w-md'
        >
          <Link
            to='/'
            className='mb-6 inline-flex items-center gap-2 text-sm text-fg-muted transition hover:text-fg'
          >
            <FiArrowLeft className='h-4 w-4' aria-hidden='true' /> Back to home
          </Link>

          <div className='card p-6 sm:p-8'>
            <div className='mb-7'>
              <h1 className='text-2xl font-semibold tracking-tight text-fg'>Welcome back</h1>
              <p className='mt-1.5 text-sm text-fg-muted'>Sign in to your Ikimina account.</p>
            </div>

            {/* A failed group lookup is stated, not left as an empty dropdown */}
            {groupsError && (
              <div
                className='mb-5 rounded-lg border border-danger/40 bg-danger-subtle px-4 py-3 text-sm text-danger'
                role='alert'
              >
                Could not load savings groups. Check your connection and reload.
              </div>
            )}

            {errors.submit && (
              <div
                className='mb-5 rounded-lg border border-danger/40 bg-danger-subtle px-4 py-3 text-sm text-danger'
                role='alert'
              >
                {String(errors.submit)}
              </div>
            )}

            <form onSubmit={handleSubmit} className='space-y-5' noValidate>
              <div>
                <label htmlFor='savingsGroupId' className='label'>
                  Savings group{' '}
                  <span className='font-normal text-fg-subtle'>
                    &mdash; leave blank if you are a super admin
                  </span>
                </label>
                <div className='relative'>
                  <FiUsers
                    className='pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-fg-subtle'
                    aria-hidden='true'
                  />
                  <select
                    id='savingsGroupId'
                    name='savingsGroupId'
                    value={formData.savingsGroupId}
                    onChange={handleChange}
                    className={`input appearance-none pl-9 pr-9 ${
                      errors.savingsGroupId ? 'input-error' : ''
                    }`}
                    disabled={isLoadingGroups || busy}
                    aria-invalid={Boolean(errors.savingsGroupId)}
                  >
                    <option value=''>
                      {isLoadingGroups ? 'Loading groups…' : 'Select a savings group'}
                    </option>
                    {groups.map(group => (
                      <option key={group.id} value={group.id}>
                        {group.name}
                      </option>
                    ))}
                  </select>
                  {/* Own chevron, since appearance-none removes the native one */}
                  <svg
                    className='pointer-events-none absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 text-fg-subtle'
                    viewBox='0 0 20 20'
                    fill='currentColor'
                    aria-hidden='true'
                  >
                    <path
                      fillRule='evenodd'
                      d='M5.23 7.21a.75.75 0 011.06.02L10 11.06l3.71-3.83a.75.75 0 111.08 1.04l-4.25 4.39a.75.75 0 01-1.08 0L5.21 8.27a.75.75 0 01.02-1.06z'
                      clipRule='evenodd'
                    />
                  </svg>
                </div>
                {errors.savingsGroupId && <p className='field-error'>{errors.savingsGroupId}</p>}
              </div>

              <div>
                <label htmlFor='email' className='label'>
                  Email address
                </label>
                <div className='relative'>
                  <FiMail
                    className='pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-fg-subtle'
                    aria-hidden='true'
                  />
                  <input
                    type='email'
                    id='email'
                    name='email'
                    autoComplete='email'
                    value={formData.email}
                    onChange={handleChange}
                    placeholder='you@example.com'
                    className={`input pl-9 ${errors.email ? 'input-error' : ''}`}
                    disabled={busy}
                    aria-invalid={Boolean(errors.email)}
                  />
                </div>
                {errors.email && <p className='field-error'>{errors.email}</p>}
              </div>

              <div>
                <label htmlFor='password' className='label'>
                  Password
                </label>
                <div className='relative'>
                  <FiLock
                    className='pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-fg-subtle'
                    aria-hidden='true'
                  />
                  <input
                    type={showPassword ? 'text' : 'password'}
                    id='password'
                    name='password'
                    autoComplete='current-password'
                    value={formData.password}
                    onChange={handleChange}
                    placeholder='Enter your password'
                    className={`input pl-9 pr-10 ${errors.password ? 'input-error' : ''}`}
                    disabled={busy}
                    aria-invalid={Boolean(errors.password)}
                  />
                  <button
                    type='button'
                    onClick={() => setShowPassword(v => !v)}
                    className='absolute right-2 top-1/2 -translate-y-1/2 rounded p-1.5 text-fg-subtle transition hover:text-fg'
                    aria-label={showPassword ? 'Hide password' : 'Show password'}
                  >
                    {showPassword ? (
                      <FiEyeOff className='h-4 w-4' />
                    ) : (
                      <FiEye className='h-4 w-4' />
                    )}
                  </button>
                </div>
                {errors.password && <p className='field-error'>{errors.password}</p>}
              </div>

              <div className='flex items-center justify-between'>
                <label htmlFor='remember-me' className='flex items-center gap-2 text-sm text-fg'>
                  <input
                    id='remember-me'
                    name='remember-me'
                    type='checkbox'
                    className='h-4 w-4 rounded border-border text-primary focus:ring-primary/30'
                  />
                  Remember me
                </label>
              </div>

              <button type='submit' disabled={busy} className='btn-primary w-full py-2.5'>
                {busy && (
                  <svg
                    className='h-4 w-4 animate-spin'
                    viewBox='0 0 24 24'
                    fill='none'
                    aria-hidden='true'
                  >
                    <circle
                      className='opacity-25'
                      cx='12'
                      cy='12'
                      r='10'
                      stroke='currentColor'
                      strokeWidth='4'
                    />
                    <path
                      className='opacity-75'
                      fill='currentColor'
                      d='M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z'
                    />
                  </svg>
                )}
                {busy ? 'Signing in…' : 'Sign in'}
              </button>

              <p className='text-center text-sm text-fg-muted'>
                Don&apos;t have an account?{' '}
                <Link to='/register' className='font-medium text-primary hover:underline'>
                  Sign up
                </Link>
              </p>
            </form>
          </div>

          <p className='mt-6 text-center text-xs leading-relaxed text-fg-subtle'>
            By signing in you agree to our{' '}
            <button type='button' className='text-fg-muted underline hover:text-fg'>
              Terms of Service
            </button>{' '}
            and{' '}
            <button type='button' className='text-fg-muted underline hover:text-fg'>
              Privacy Policy
            </button>
            .
          </p>
        </motion.div>
      </div>
    </div>
  );
};

export default LoginForm;
