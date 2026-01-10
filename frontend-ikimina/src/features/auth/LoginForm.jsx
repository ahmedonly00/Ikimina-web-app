import React, { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useLoginMutation, useGetAllGroupsQuery } from '../../app/api/apiSlice';
import { setCredentials } from './authSlice';
import { useAppDispatch } from '../../app/hooks';
import { toast } from 'react-toastify';
import { FaEnvelope, FaLock, FaArrowLeft, FaUserPlus, FaMoneyBillWave, FaChartLine, FaUsers } from 'react-icons/fa';
import { motion } from 'framer-motion';
import loginAnimation from '../../assets/login-animation.svg';

export const LoginForm = () => {
  const [formData, setFormData] = useState({
    email: '',
    password: '',
    savingsGroupId: ''
  });
  
  // Fetch all savings groups
  const { data: groups = [], isLoading: isLoadingGroups } = useGetAllGroupsQuery();
  
  const [errors, setErrors] = useState({});
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [login, { isLoading }] = useLoginMutation();
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  
  // Clear errors when form data changes
  useEffect(() => {
    if (Object.keys(errors).length > 0) {
      setErrors({});
    }
  }, [formData]);

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: value
    }));
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
    
    if (!formData.savingsGroupId) {
      newErrors.savingsGroupId = 'Please select a savings group';
    }
    
    return newErrors;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    const validationErrors = validateForm();
    
    if (Object.keys(validationErrors).length > 0) {
      setErrors(validationErrors);
      return;
    }

    try {
      setIsSubmitting(true);
      
      // Use real login
      const loginData = {
        email: formData.email,
        password: formData.password,
        savingsGroupId: formData.savingsGroupId
      };
      const userData = await login(loginData).unwrap();
      
      dispatch(setCredentials({
        user: userData.user,
        token: userData.token,
        savingsGroupId: userData.user.savingsGroupId,
        savingsGroupName: groups.find(g => g.id === userData.user.savingsGroupId)?.name || ''
      }));
      
      toast.success('Login successful!');
      navigate('/dashboard');
    } catch (err) {
      const errorMessage = err?.data?.message || err?.data || 'Login failed. Please check your credentials.';
      toast.error(errorMessage);
      setErrors({ submit: errorMessage });
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen bg-white flex">
      {/* Left side with animation */}
      <div className="hidden lg:flex flex-col justify-center items-center w-1/2 bg-gradient-to-br from-indigo-600 to-blue-600 p-12 text-white">
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.8 }}
          className="max-w-md"
        >
          <h2 className="text-4xl font-bold mb-6">Welcome to Ikimina</h2>
          <p className="text-xl mb-8 text-indigo-100">Your financial journey starts here. Manage your savings and loans with ease.</p>
          
          <div className="space-y-6">
            <div className="flex items-start">
              <div className="bg-indigo-500 p-3 rounded-full mr-4">
                <FaMoneyBillWave className="h-6 w-6" />
              </div>
              <div>
                <h3 className="font-semibold text-lg">Track Savings</h3>
                <p className="text-indigo-100">Monitor your savings growth and set financial goals.</p>
              </div>
            </div>
            
            <div className="flex items-start">
              <div className="bg-indigo-500 p-3 rounded-full mr-4">
                <FaChartLine className="h-6 w-6" />
              </div>
              <div>
                <h3 className="font-semibold text-lg">Smart Analytics</h3>
                <p className="text-indigo-100">Get insights into your financial health.</p>
              </div>
            </div>
            
            <div className="flex items-start">
              <div className="bg-indigo-500 p-3 rounded-full mr-4">
                <FaUserPlus className="h-6 w-6" />
              </div>
              <div>
                <h3 className="font-semibold text-lg">Join Our Community</h3>
                <p className="text-indigo-100">Be part of a growing financial community.</p>
              </div>
            </div>
          </div>
          
          <div className="mt-12">
            <img 
              src={loginAnimation} 
              alt="Financial growth illustration" 
              className="w-full h-auto"
            />
          </div>
        </motion.div>
      </div>
      
      {/* Right side with login form */}
      <div className="w-full lg:w-1/2 flex items-center justify-center p-4 sm:p-8">
        <motion.div 
          initial={{ opacity: 0, x: 20 }}
          animate={{ opacity: 1, x: 0 }}
          transition={{ duration: 0.5, delay: 0.2 }}
          className="w-full max-w-md"
        >
          <div className="bg-white rounded-xl shadow-2xl p-8">
          <div className="mb-8 text-center">
            <Link to="/" className="inline-flex items-center text-indigo-600 hover:text-indigo-800 mb-4">
              <FaArrowLeft className="mr-2" /> Back to Home
            </Link>
            <h1 className="text-3xl font-bold text-gray-900 mb-2">Welcome Back</h1>
            <p className="text-gray-600">Sign in to access your Ikimina account</p>
          </div>
          
          <form onSubmit={handleSubmit} className="space-y-6">
            <div className="space-y-4">
              <div>
                <label htmlFor="savingsGroupId" className="block text-sm font-medium text-gray-700 mb-1">
                  Savings Group
                </label>
                <div className="relative">
                  <div className="absolute inset-y-0 left-0 pl-5 flex items-center pointer-events-none">
                    <FaUsers className="h-5 w-5 text-gray-400" />
                  </div>
                  <select
                    id="savingsGroupId"
                    name="savingsGroupId"
                    value={formData.savingsGroupId}
                    onChange={handleChange}
                    className={`block w-full pl-10 pr-10 py-2.5 border ${errors.savingsGroupId ? 'border-red-500' : 'border-gray-300'} rounded-lg shadow-sm bg-white focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 appearance-none`}
                    disabled={isLoadingGroups}
                  >
                    <option value="">Select a savings group</option>
                    {groups.map(group => (
                      <option key={group.id} value={group.id}>
                        {group.name}
                      </option>
                    ))}
                  </select>
                </div>
                {errors.savingsGroupId && (
                  <p className="mt-1 text-sm text-red-600">{errors.savingsGroupId}</p>
                )}
              </div>

              <div>
                <label htmlFor="email" className="block text-sm font-medium text-gray-700 mb-1">
                  Email Address
                </label>
                <div className="relative">
                  <div className="absolute inset-y-0 left-0 pl-5 flex items-center pointer-events-none">
                    <FaEnvelope className="h-5 w-5 text-gray-400" />
                  </div>
                  <input
                    type="email"
                    id="email"
                    name="email"
                    value={formData.email || ''}
                    onChange={handleChange}
                    placeholder="Enter your email"
                    className={`block w-full pl-10 pr-10 py-2.5 border ${errors.email ? 'border-red-500' : 'border-gray-300'} rounded-lg shadow-sm placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500`}
                    disabled={isSubmitting}
                  />
                </div>
                {errors.email && (
                  <p className="mt-1 text-sm text-red-600">{errors.email}</p>
                )}
              </div>

              <div>
                <label htmlFor="password" className="block text-sm font-medium text-gray-700 mb-1">
                  Password
                </label>
                <div className="relative">
                  <div className="absolute inset-y-0 left-0 pl-5 flex items-center pointer-events-none">
                    <FaLock className="h-5 w-5 text-gray-400" />
                  </div>
                  <input
                    type="password"
                    id="password"
                    name="password"
                    value={formData.password || ''}
                    onChange={handleChange}
                    className={`block w-full pl-10 pr-10 py-2.5 border ${errors.password ? 'border-red-500' : 'border-gray-300'} rounded-lg shadow-sm placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500`}
                    placeholder="Enter your password"
                    disabled={isSubmitting}
                  />
                </div>
                {errors.password && (
                  <p className="mt-1 text-sm text-red-600">{errors.password}</p>
                )}
              </div>
            </div>
            
            <div className="flex items-center">
              <input
                id="remember-me"
                name="remember-me"
                type="checkbox"
                className="h-4 w-4 text-indigo-600 focus:ring-indigo-500 border-gray-300 rounded"
              />
              <label htmlFor="remember-me" className="ml-2 block text-sm text-gray-700">
                Remember me
              </label>
            </div>
            
            <div className="pt-2">
              <button
                type="submit"
                disabled={isLoading || isSubmitting}
                className={`w-full flex justify-center py-3 px-4 border border-transparent rounded-xl shadow-sm text-sm font-medium text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 transition-colors duration-200 ${(isLoading || isSubmitting) ? 'opacity-70 cursor-not-allowed' : ''}`}
              >
                {isLoading || isSubmitting ? (
                  <>
                    <svg className="animate-spin -ml-1 mr-3 h-5 w-5 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                    </svg>
                    Signing in...
                  </>
                ) : 'Sign in'}
              </button>
            </div>
            
            <div className="text-center">
              <p className="text-sm text-gray-600">
                Don't have an account?{' '}
                <Link to="/register" className="font-medium text-indigo-600 hover:text-indigo-500">
                  Sign up
                </Link>
              </p>
            </div>
          </form>
          
          <div className="mt-6 text-center text-sm text-gray-600">
            <p>By signing in, you agree to our <a href="#" className="text-indigo-600 hover:text-indigo-500">Terms of Service</a> and <a href="#" className="text-indigo-600 hover:text-indigo-500">Privacy Policy</a>.</p>
          </div>
          </div>
        </motion.div>
      </div>
    </div>
  );
};

export default LoginForm;
