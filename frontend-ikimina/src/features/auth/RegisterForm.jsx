import React, { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useRegisterMutation, useGetPublicGroupsQuery } from '../../app/api/apiSlice';
import { setCredentials } from './authSlice';
import { useAppDispatch } from '../../app/hooks';
import toast from 'react-hot-toast';
import {
  FaUser,
  FaEnvelope,
  FaLock,
  FaArrowLeft,
  FaUserShield,
  FaChartPie,
  FaHandshake,
  FaPhone,
  FaUsers,
} from 'react-icons/fa';
import { motion } from 'framer-motion';
import registerAnimation from '../../assets/register-animation.svg';

export const RegisterForm = () => {
  const [formData, setFormData] = useState({
    firstName: '',
    lastName: '',
    email: '',
    phoneNumber: '',
    role: 'MEMBER', // Fixed role for all signups
    password: '',
    confirmPassword: '',
    savingsGroupId: '',
  });

  // Fetch all savings groups
  const { data: groups = [], isLoading: isLoadingGroups } = useGetPublicGroupsQuery();

  const [errors, setErrors] = useState({});
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [register, { isLoading }] = useRegisterMutation({
    // This will automatically refetch data after a successful registration
    // and update the cache with the new user data
    refetchOnMountOrArgChange: true,
  });
  const dispatch = useAppDispatch();
  const navigate = useNavigate();

  // Clear errors when form data changes
  // See LoginForm: functional setter keeps errors out of the dependencies.
  useEffect(() => {
    setErrors(prev => (Object.keys(prev).length > 0 ? {} : prev));
  }, [formData]);

  const handleChange = e => {
    const { name, value } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: value,
    }));
  };

  const validateForm = () => {
    const newErrors = {};

    if (!formData.savingsGroupId) {
      newErrors.savingsGroupId = 'Please select a savings group';
    }

    if (!formData.firstName.trim()) {
      newErrors.firstName = 'First name is required';
    }

    if (!formData.lastName.trim()) {
      newErrors.lastName = 'Last name is required';
    }

    if (!formData.email) {
      newErrors.email = 'Email is required';
    } else if (!/\S+@\S+\.\S+/.test(formData.email)) {
      newErrors.email = 'Email is invalid';
    }

    if (!formData.phoneNumber.trim()) {
      newErrors.phoneNumber = 'Phone number is required';
    } else if (!/^[0-9+\s-]{10,}$/.test(formData.phoneNumber)) {
      newErrors.phoneNumber = 'Please enter a valid phone number';
    }

    if (!formData.role) {
      newErrors.role = 'Role is required';
    }

    if (!formData.password) {
      newErrors.password = 'Password is required';
    } else if (formData.password.length < 6) {
      newErrors.password = 'Password must be at least 6 characters';
    }

    if (formData.password !== formData.confirmPassword) {
      newErrors.confirmPassword = 'Passwords do not match';
    }

    return newErrors;
  };

  const handleSubmit = async e => {
    e.preventDefault();

    const formErrors = validateForm();
    if (Object.keys(formErrors).length > 0) {
      setErrors(formErrors);
      return;
    }

    try {
      setIsSubmitting(true);

      // Include savings group in registration data
      const registrationData = {
        ...formData,
        savingsGroupId: formData.savingsGroupId,
      };

      const userData = await register(registrationData).unwrap();

      // Include group info in credentials
      dispatch(
        setCredentials({
          ...userData,
          savingsGroupId: formData.savingsGroupId,
          savingsGroupName: groups.find(g => g.id === formData.savingsGroupId)?.name,
        })
      );

      toast.success('Registration successful!');
      navigate('/dashboard');
    } catch (err) {
      const errorMessage = err?.data?.message || 'Registration failed. Please try again.';
      toast.error(errorMessage);
      setErrors({ submit: errorMessage });
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className='min-h-screen bg-surface flex'>
      {/* Left side with animation */}
      <div className='hidden lg:flex flex-col justify-center items-center w-1/2 bg-gradient-to-br from-indigo-600 to-blue-600 p-12 text-white'>
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.8 }}
          className='max-w-md'
        >
          <h2 className='text-4xl font-bold mb-6'>Join Ikimina as a Member</h2>
          <p className='text-xl mb-8 text-sidebar-fg'>
            Start your journey towards better financial management and community support.
          </p>

          <div className='space-y-6'>
            <div className='flex items-start'>
              <div className='bg-primary p-3 rounded-full mr-4'>
                <FaUserShield className='h-6 w-6' />
              </div>
              <div>
                <h3 className='font-semibold text-lg'>Secure & Private</h3>
                <p className='text-sidebar-fg'>Your data is encrypted and protected.</p>
              </div>
            </div>

            <div className='flex items-start'>
              <div className='bg-primary p-3 rounded-full mr-4'>
                <FaChartPie className='h-6 w-6' />
              </div>
              <div>
                <h3 className='font-semibold text-lg'>Track Everything</h3>
                <p className='text-sidebar-fg'>Monitor your financial growth in real-time.</p>
              </div>
            </div>

            <div className='flex items-start'>
              <div className='bg-primary p-3 rounded-full mr-4'>
                <FaHandshake className='h-6 w-6' />
              </div>
              <div>
                <h3 className='font-semibold text-lg'>Community Support</h3>
                <p className='text-sidebar-fg'>Join a community that grows together.</p>
              </div>
            </div>
          </div>

          <div className='mt-12'>
            <img
              src={registerAnimation}
              alt='Financial community illustration'
              className='w-full h-auto'
            />
          </div>
        </motion.div>
      </div>

      {/* Right side with registration form */}
      <div className='w-full lg:w-1/2 flex items-center justify-center p-4 sm:p-8 overflow-y-auto'>
        <motion.div
          initial={{ opacity: 0, x: 20 }}
          animate={{ opacity: 1, x: 0 }}
          transition={{ duration: 0.5, delay: 0.2 }}
          className='w-full max-w-md'
        >
          <div className='bg-surface rounded-xl shadow-2xl p-8'>
            <div className='mb-8 text-center'>
              <Link
                to='/'
                className='inline-flex items-center text-primary hover:text-primary mb-4'
              >
                <FaArrowLeft className='mr-2' /> Back to Home
              </Link>
              <h1 className='text-3xl font-bold text-fg mb-2'>Create Member Account</h1>
              <p className='text-fg-muted'>
                Join your Ikimina savings group and manage your finances with ease
              </p>
            </div>

            <form onSubmit={handleSubmit} className='space-y-6'>
              <div className='space-y-4'>
                {/* Savings Group Selection */}
                <div>
                  <label
                    htmlFor='savingsGroupId'
                    className='block text-sm font-medium text-fg mb-1'
                  >
                    Savings Group
                  </label>
                  <div className='relative'>
                    <div className='absolute inset-y-0 left-0 pl-5 flex items-center pointer-events-none'>
                      <FaUsers className='h-5 w-5 text-fg-subtle' />
                    </div>
                    <select
                      id='savingsGroupId'
                      name='savingsGroupId'
                      value={formData.savingsGroupId}
                      onChange={handleChange}
                      className={`block w-full pl-14 pr-10 py-2.5 border ${errors.savingsGroupId ? 'border-red-500' : 'border-border'} rounded-lg shadow-sm bg-surface focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 appearance-none`}
                      disabled={isLoadingGroups}
                    >
                      <option value=''>Select a savings group</option>
                      {groups.map(group => (
                        <option key={group.id} value={group.id}>
                          {group.name}
                        </option>
                      ))}
                    </select>
                  </div>
                  {errors.savingsGroupId && (
                    <p className='mt-1 text-sm text-red-600'>{errors.savingsGroupId}</p>
                  )}
                </div>

                {/* First Name and Last Name */}
                <div className='grid grid-cols-1 md:grid-cols-2 gap-4'>
                  <div>
                    <label htmlFor='firstName' className='block text-sm font-medium text-fg mb-1'>
                      First Name
                    </label>
                    <div className='relative'>
                      <div className='absolute inset-y-0 left-0 pl-5 flex items-center pointer-events-none'>
                        <FaUser className='h-5 w-5 text-fg-subtle' />
                      </div>
                      <input
                        id='firstName'
                        name='firstName'
                        type='text'
                        value={formData.firstName}
                        onChange={handleChange}
                        className={`block w-full pl-14 pr-4 py-2.5 border ${
                          errors.firstName ? 'border-red-500' : 'border-border'
                        } rounded-lg shadow-sm placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500`}
                        placeholder='John'
                      />
                    </div>
                    {errors.firstName && (
                      <p className='mt-1 text-sm text-red-600'>{errors.firstName}</p>
                    )}
                  </div>

                  <div>
                    <label htmlFor='lastName' className='block text-sm font-medium text-fg mb-1'>
                      Last Name
                    </label>
                    <div className='relative'>
                      <div className='absolute inset-y-0 left-0 pl-5 flex items-center pointer-events-none'>
                        <FaUser className='h-5 w-5 text-fg-subtle' />
                      </div>
                      <input
                        id='lastName'
                        name='lastName'
                        type='text'
                        value={formData.lastName}
                        onChange={handleChange}
                        className={`block w-full pl-14 pr-4 py-2.5 border ${
                          errors.lastName ? 'border-red-500' : 'border-border'
                        } rounded-lg shadow-sm placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500`}
                        placeholder='Doe'
                      />
                    </div>
                    {errors.lastName && (
                      <p className='mt-1 text-sm text-red-600'>{errors.lastName}</p>
                    )}
                  </div>
                </div>

                {/* Email */}
                <div>
                  <label htmlFor='email' className='block text-sm font-medium text-fg mb-1'>
                    Email Address
                  </label>
                  <div className='relative'>
                    <div className='absolute inset-y-0 left-0 pl-5 flex items-center pointer-events-none'>
                      <FaEnvelope className='h-5 w-5 text-fg-subtle' />
                    </div>
                    <input
                      id='email'
                      name='email'
                      type='email'
                      autoComplete='email'
                      className={`pl-14 pr-4 py-3 block w-full rounded-lg border ${
                        errors.email ? 'border-red-300' : 'border-border'
                      } shadow-sm focus:ring-2 focus:ring-primary focus:border-primary`}
                      placeholder='you@example.com'
                      value={formData.email}
                      onChange={handleChange}
                    />
                  </div>
                  {errors.email && <p className='mt-1 text-sm text-red-600'>{errors.email}</p>}
                </div>

                {/* Phone Number - Now full width */}
                <div>
                  <label htmlFor='phoneNumber' className='block text-sm font-medium text-fg mb-1'>
                    Phone Number
                  </label>
                  <div className='relative'>
                    <div className='absolute inset-y-0 left-0 pl-5 flex items-center'>
                      <FaPhone className='h-5 w-5 text-sidebar-fg-muted' />
                    </div>
                    <input
                      id='phoneNumber'
                      name='phoneNumber'
                      type='tel'
                      value={formData.phoneNumber}
                      onChange={handleChange}
                      className={`pl-14 pr-4 py-3 block w-full rounded-lg border ${
                        errors.phoneNumber ? 'border-red-300' : 'border-border'
                      } shadow-sm focus:ring-2 focus:ring-primary focus:border-primary`}
                      placeholder='+250 700 000 000'
                    />
                  </div>
                  {errors.phoneNumber && (
                    <p className='mt-1 text-sm text-red-600'>{errors.phoneNumber}</p>
                  )}
                </div>

                {/* Password and Confirm Password */}
                <div className='grid grid-cols-1 md:grid-cols-2 gap-4'>
                  <div>
                    <label htmlFor='password' className='block text-sm font-medium text-fg mb-1'>
                      Password
                    </label>
                    <div className='relative'>
                      <div className='absolute inset-y-0 left-0 pl-5 flex items-center'>
                        <FaLock className='h-5 w-5 text-sidebar-fg-muted' />
                      </div>
                      <input
                        id='password'
                        name='password'
                        type='password'
                        autoComplete='new-password'
                        className={`pl-14 pr-4 py-3 block w-full rounded-lg border ${
                          errors.password ? 'border-red-300' : 'border-border'
                        } shadow-sm focus:ring-2 focus:ring-primary focus:border-primary`}
                        placeholder='••••••••'
                        value={formData.password}
                        onChange={handleChange}
                      />
                    </div>
                    {errors.password && (
                      <p className='mt-1 text-sm text-red-600'>{errors.password}</p>
                    )}
                  </div>

                  <div>
                    <label
                      htmlFor='confirmPassword'
                      className='block text-sm font-medium text-fg mb-1'
                    >
                      Confirm Password
                    </label>
                    <div className='relative'>
                      <div className='absolute inset-y-0 left-0 pl-5 flex items-center'>
                        <FaLock className='h-5 w-5 text-sidebar-fg-muted' />
                      </div>
                      <input
                        id='confirmPassword'
                        name='confirmPassword'
                        type='password'
                        autoComplete='new-password'
                        className={`pl-14 pr-4 py-3 block w-full rounded-lg border ${
                          errors.confirmPassword ? 'border-red-300' : 'border-border'
                        } shadow-sm focus:ring-2 focus:ring-primary focus:border-primary`}
                        placeholder='••••••••'
                        value={formData.confirmPassword}
                        onChange={handleChange}
                      />
                    </div>
                    {errors.confirmPassword && (
                      <p className='mt-1 text-sm text-red-600'>{errors.confirmPassword}</p>
                    )}
                  </div>
                </div>
              </div>

              <div className='pt-2'>
                <button
                  type='submit'
                  disabled={isLoading || isSubmitting}
                  className={`w-full flex justify-center py-3 px-4 border border-transparent rounded-xl shadow-sm text-sm font-medium text-white bg-primary hover:bg-primary-hover focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary transition-colors duration-200 ${
                    isLoading || isSubmitting ? 'opacity-70 cursor-not-allowed' : ''
                  }`}
                >
                  {isLoading || isSubmitting ? (
                    <>
                      <svg
                        className='animate-spin -ml-1 mr-3 h-5 w-5 text-white'
                        xmlns='http://www.w3.org/2000/svg'
                        fill='none'
                        viewBox='0 0 24 24'
                      >
                        <circle
                          className='opacity-25'
                          cx='12'
                          cy='12'
                          r='10'
                          stroke='currentColor'
                          strokeWidth='4'
                        ></circle>
                        <path
                          className='opacity-75'
                          fill='currentColor'
                          d='M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z'
                        ></path>
                      </svg>
                      Creating Account...
                    </>
                  ) : (
                    'Create Account'
                  )}
                </button>
              </div>

              <div className='text-center'>
                <p className='text-sm text-fg-muted'>
                  Already have an account?{' '}
                  <Link to='/login' className='font-medium text-primary hover:text-primary-hover'>
                    Sign in
                  </Link>
                </p>
              </div>
            </form>
          </div>
        </motion.div>
      </div>
    </div>
  );
};

export default RegisterForm;
