import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import toast from 'react-hot-toast';
import {
  FiArrowLeft,
  FiKey,
  FiUsers,
  FiCheckCircle,
  FiClock,
  FiEye,
  FiEyeOff,
} from 'react-icons/fi';
import { useRegisterMutation, useGetPublicGroupsQuery } from '../../app/api/apiSlice';

/**
 * Public registration.
 *
 * This form used to post a whole user object including `role` and
 * `savingsGroupId`, and the server honoured the group - so picking one from
 * the dropdown put you into that group's member roster whether or not anyone
 * there had ever heard of you.
 *
 * A registrant can no longer choose the group they land in. Either they hold
 * the group's invite code, or they ask and an administrator decides. The
 * server enforces this; the two routes below just reflect it.
 */
const EMPTY = {
  firstName: '',
  lastName: '',
  email: '',
  phoneNumber: '',
  password: '',
  confirmPassword: '',
  joinCode: '',
  requestGroupId: '',
};

const RegisterForm = () => {
  const navigate = useNavigate();
  const [mode, setMode] = useState('code'); // 'code' | 'request'
  const [form, setForm] = useState(EMPTY);
  const [errors, setErrors] = useState({});
  const [showPassword, setShowPassword] = useState(false);
  const [done, setDone] = useState(null);

  const { data: groups = [], isLoading: isLoadingGroups } = useGetPublicGroupsQuery();
  const [register, { isLoading }] = useRegisterMutation();

  const change = e => {
    const { name, value } = e.target;
    setForm(prev => ({ ...prev, [name]: value }));
    setErrors(prev => (Object.keys(prev).length ? {} : prev));
  };

  const validate = () => {
    const next = {};
    if (!form.firstName.trim()) next.firstName = 'Required';
    if (!form.lastName.trim()) next.lastName = 'Required';
    if (!form.email.trim()) next.email = 'Required';
    else if (!/\S+@\S+\.\S+/.test(form.email)) next.email = 'Not a valid email';
    // users.phone_number is NOT NULL, and for a savings group it is the
    // contact that matters.
    if (!form.phoneNumber.trim()) next.phoneNumber = 'Required';
    if (!form.password) next.password = 'Required';
    else if (form.password.length < 8) next.password = 'At least 8 characters';
    if (form.password !== form.confirmPassword) next.confirmPassword = 'Passwords do not match';

    if (mode === 'code' && !form.joinCode.trim()) next.joinCode = 'Enter the code you were given';
    if (mode === 'request' && !form.requestGroupId) next.requestGroupId = 'Choose a group';
    return next;
  };

  const submit = async e => {
    e.preventDefault();
    const found = validate();
    if (Object.keys(found).length) {
      setErrors(found);
      return;
    }

    // Exactly one of the two routes is sent; the server rejects both or neither.
    const payload = {
      firstName: form.firstName.trim(),
      lastName: form.lastName.trim(),
      email: form.email.trim(),
      phoneNumber: form.phoneNumber.trim(),
      password: form.password,
      ...(mode === 'code'
        ? { joinCode: form.joinCode.trim().toUpperCase() }
        : { requestGroupId: Number(form.requestGroupId) }),
    };

    try {
      const result = await register(payload).unwrap();
      setDone(result);
      if (result.outcome === 'JOINED') {
        toast.success(result.message);
      }
    } catch (err) {
      const message = err?.data?.detail || err?.data?.message || 'Registration failed';
      toast.error(message);
      setErrors({ submit: message });
    }
  };

  if (done) {
    const joined = done.outcome === 'JOINED';
    return (
      <div className='flex min-h-screen items-center justify-center bg-bg px-4'>
        <div className='card w-full max-w-md p-8 text-center'>
          <span
            className={`mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-full ${
              joined ? 'bg-success-subtle text-success' : 'bg-warning-subtle text-warning'
            }`}
            aria-hidden='true'
          >
            {joined ? <FiCheckCircle className='h-6 w-6' /> : <FiClock className='h-6 w-6' />}
          </span>
          <h1 className='text-xl font-semibold text-fg'>
            {joined ? 'You are in' : 'Request sent'}
          </h1>
          <p className='mt-2 text-sm text-fg-muted'>{done.message}</p>
          {joined ? (
            <button onClick={() => navigate('/login')} className='btn-primary mt-6 w-full'>
              Go to sign in
            </button>
          ) : (
            <Link to='/login' className='btn-secondary mt-6 w-full'>
              Back to sign in
            </Link>
          )}
        </div>
      </div>
    );
  }

  const field = (name, label, type = 'text', placeholder) => (
    <div>
      <label htmlFor={name} className='label'>
        {label}
      </label>
      <input
        id={name}
        name={name}
        type={type}
        value={form[name]}
        onChange={change}
        placeholder={placeholder}
        className={`input ${errors[name] ? 'input-error' : ''}`}
      />
      {errors[name] && <p className='field-error'>{errors[name]}</p>}
    </div>
  );

  return (
    <div className='flex min-h-screen items-center justify-center bg-bg px-4 py-10'>
      <motion.div
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.4 }}
        className='w-full max-w-lg'
      >
        <Link
          to='/login'
          className='mb-6 inline-flex items-center gap-2 text-sm text-fg-muted transition hover:text-fg'
        >
          <FiArrowLeft className='h-4 w-4' aria-hidden='true' /> Back to sign in
        </Link>

        <div className='card p-6 sm:p-8'>
          <h1 className='text-2xl font-semibold tracking-tight text-fg'>Join a savings group</h1>
          <p className='mt-1.5 text-sm text-fg-muted'>
            A group decides who belongs to it, so you will need an invite code or an
            administrator&apos;s approval.
          </p>

          {/* The two routes in */}
          <div className='mt-6 grid grid-cols-2 gap-2' role='tablist'>
            {[
              { id: 'code', icon: FiKey, label: 'I have a code' },
              { id: 'request', icon: FiUsers, label: 'Ask to join' },
            ].map(({ id, icon: Icon, label }) => (
              <button
                key={id}
                type='button'
                role='tab'
                aria-selected={mode === id}
                onClick={() => {
                  setMode(id);
                  setErrors({});
                }}
                className={`flex items-center justify-center gap-2 rounded-lg border px-3 py-2.5 text-sm font-medium transition ${
                  mode === id
                    ? 'border-primary bg-primary-subtle text-primary'
                    : 'border-border bg-surface text-fg-muted hover:bg-surface-2 hover:text-fg'
                }`}
              >
                <Icon className='h-4 w-4' aria-hidden='true' />
                {label}
              </button>
            ))}
          </div>

          {errors.submit && (
            <div
              className='mt-5 rounded-lg border border-danger/40 bg-danger-subtle px-4 py-3 text-sm text-danger'
              role='alert'
            >
              {errors.submit}
            </div>
          )}

          <form onSubmit={submit} className='mt-5 space-y-4' noValidate>
            {mode === 'code' ? (
              <div>
                <label htmlFor='joinCode' className='label'>
                  Invite code
                </label>
                <input
                  id='joinCode'
                  name='joinCode'
                  value={form.joinCode}
                  onChange={change}
                  placeholder='e.g. B356Q4TQUN'
                  autoCapitalize='characters'
                  className={`input tracking-widest ${errors.joinCode ? 'input-error' : ''}`}
                />
                {errors.joinCode ? (
                  <p className='field-error'>{errors.joinCode}</p>
                ) : (
                  <p className='mt-1 text-xs text-fg-subtle'>
                    Ask your group&apos;s administrator for this. You join straight away.
                  </p>
                )}
              </div>
            ) : (
              <div>
                <label htmlFor='requestGroupId' className='label'>
                  Group you want to join
                </label>
                <select
                  id='requestGroupId'
                  name='requestGroupId'
                  value={form.requestGroupId}
                  onChange={change}
                  disabled={isLoadingGroups}
                  className={`input ${errors.requestGroupId ? 'input-error' : ''}`}
                >
                  <option value=''>{isLoadingGroups ? 'Loading groups…' : 'Select a group'}</option>
                  {groups.map(g => (
                    <option key={g.id} value={g.id}>
                      {g.name}
                    </option>
                  ))}
                </select>
                {errors.requestGroupId ? (
                  <p className='field-error'>{errors.requestGroupId}</p>
                ) : (
                  <p className='mt-1 text-xs text-fg-subtle'>
                    An administrator has to approve you before you can sign in.
                  </p>
                )}
              </div>
            )}

            <div className='grid grid-cols-1 gap-4 sm:grid-cols-2'>
              {field('firstName', 'First name')}
              {field('lastName', 'Last name')}
            </div>

            {field('email', 'Email address', 'email', 'you@example.com')}
            {field('phoneNumber', 'Phone number', 'tel', '+250 7xx xxx xxx')}

            <div>
              <label htmlFor='password' className='label'>
                Password
              </label>
              <div className='relative'>
                <input
                  id='password'
                  name='password'
                  type={showPassword ? 'text' : 'password'}
                  value={form.password}
                  onChange={change}
                  className={`input pr-10 ${errors.password ? 'input-error' : ''}`}
                />
                <button
                  type='button'
                  onClick={() => setShowPassword(v => !v)}
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                  className='absolute right-2 top-1/2 -translate-y-1/2 rounded p-1.5 text-fg-subtle transition hover:text-fg'
                >
                  {showPassword ? <FiEyeOff className='h-4 w-4' /> : <FiEye className='h-4 w-4' />}
                </button>
              </div>
              {errors.password && <p className='field-error'>{errors.password}</p>}
            </div>

            {field('confirmPassword', 'Confirm password', showPassword ? 'text' : 'password')}

            <button type='submit' disabled={isLoading} className='btn-primary w-full py-2.5'>
              {isLoading ? 'Submitting…' : mode === 'code' ? 'Join group' : 'Send request'}
            </button>

            <p className='text-center text-sm text-fg-muted'>
              Already have an account?{' '}
              <Link to='/login' className='font-medium text-primary hover:underline'>
                Sign in
              </Link>
            </p>
          </form>
        </div>
      </motion.div>
    </div>
  );
};

export { RegisterForm };
export default RegisterForm;
