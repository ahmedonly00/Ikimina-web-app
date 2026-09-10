import React, { useEffect } from 'react';
import { useForm, Controller } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';

const schema = yup.object().shape({
  memberId: yup.string().required('Member is required'),
  amount: yup
    .number()
    .required('Amount is required')
    .positive('Amount must be positive')
    .typeError('Amount must be a number'),
  type: yup
    .string()
    .oneOf(['UBWIZIGAME', 'INGABOKE', 'BOTH', 'DEPOSIT', 'WITHDRAWAL'])
    .required('Type is required'),
  description: yup.string(),
  savingDate: yup.date().required('Date is required').default(new Date()),
});

export const SavingForm = ({ initialData = {}, onSubmit, isSubmitting = false, onCancel }) => {
  const {
    control,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm({
    resolver: yupResolver(schema),
    defaultValues: {
      memberId: '',
      amount: '',
      type: 'UBWIZIGAME',
      description: '',
      savingDate: new Date().toISOString().split('T')[0],
      ...initialData,
    },
  });

  useEffect(() => {
    if (initialData) {
      reset({
        memberId: initialData.memberId || '',
        amount: initialData.amount || '',
        type: initialData.type || 'UBWIZIGAME',
        description: initialData.description || '',
        savingDate: initialData.savingDate || new Date().toISOString().split('T')[0],
      });
    }
  }, [initialData, reset]);

  const handleFormSubmit = data => {
    // Convert amount to number before submitting
    const formData = {
      ...data,
      amount: parseFloat(data.amount),
    };
    onSubmit(formData);
  };

  return (
    <form onSubmit={handleSubmit(handleFormSubmit)} className='space-y-6'>
      <div>
        <label htmlFor='memberId' className='block text-sm font-medium text-gray-700'>
          Select Member
        </label>
        <Controller
          name='memberId'
          control={control}
          render={({ field }) => (
            <select
              {...field}
              id='memberId'
              className={`mt-1 block w-full pl-3 pr-10 py-2 text-base border ${
                errors.memberId ? 'border-red-300' : 'border-gray-300'
              } focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm rounded-md`}
              disabled={isSubmitting}
            >
              <option value=''>Select a member...</option>
              {/* Replace with actual member options from API */}
              <option value='1'>John Doe - ID: MEM001</option>
              <option value='2'>Jane Smith - ID: MEM002</option>
              <option value='3'>Alice Johnson - ID: MEM003</option>
              <option value='4'>David Wilson - ID: MEM004</option>
            </select>
          )}
        />
        {errors.memberId && <p className='mt-1 text-sm text-red-600'>{errors.memberId.message}</p>}
      </div>

      <div>
        <label htmlFor='amount' className='block text-sm font-medium text-gray-700'>
          Amount
        </label>
        <div className='mt-1 relative rounded-md shadow-sm'>
          <div className='absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none'>
            <span className='text-gray-500 sm:text-sm'>$</span>
          </div>
          <Controller
            name='amount'
            control={control}
            render={({ field }) => (
              <input
                {...field}
                type='text'
                id='amount'
                className={`block w-full pl-7 pr-12 sm:text-sm border ${
                  errors.amount ? 'border-red-300' : 'border-gray-300'
                } rounded-md focus:ring-indigo-500 focus:border-indigo-500`}
                placeholder='0.00'
                disabled={isSubmitting}
              />
            )}
          />
        </div>
        {errors.amount && <p className='mt-1 text-sm text-red-600'>{errors.amount.message}</p>}
      </div>

      <div>
        <label htmlFor='savingDate' className='block text-sm font-medium text-gray-700'>
          Saving Date
        </label>
        <Controller
          name='savingDate'
          control={control}
          render={({ field }) => (
            <input
              {...field}
              type='date'
              id='savingDate'
              max={new Date().toISOString().split('T')[0]}
              className={`mt-1 block w-full pl-3 pr-10 py-2 text-base border ${
                errors.savingDate ? 'border-red-300' : 'border-gray-300'
              } focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm rounded-md`}
              disabled={isSubmitting}
            />
          )}
        />
        {errors.savingDate && (
          <p className='mt-1 text-sm text-red-600'>{errors.savingDate.message}</p>
        )}
      </div>

      <div>
        <label htmlFor='type' className='block text-sm font-medium text-gray-700'>
          Saving Type
        </label>
        <Controller
          name='type'
          control={control}
          render={({ field }) => (
            <select
              {...field}
              id='type'
              className={`mt-1 block w-full pl-3 pr-10 py-2 text-base border ${
                errors.type ? 'border-red-300' : 'border-gray-300'
              } focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm rounded-md`}
              disabled={isSubmitting}
            >
              <option value='UBWIZIGAME'>Ubwizigame (Regular Savings)</option>
              <option value='INGABOKE'>Ingaboke (Emergency Savings)</option>
              <option value='BOTH'>Both Types</option>
              <option value='DEPOSIT'>General Deposit</option>
              <option value='WITHDRAWAL'>Withdrawal</option>
            </select>
          )}
        />
        {errors.type && <p className='mt-1 text-sm text-red-600'>{errors.type.message}</p>}
        <p className='mt-1 text-xs text-gray-500'>
          Select the type of savings to record for this member
        </p>
      </div>

      <div>
        <label htmlFor='description' className='block text-sm font-medium text-gray-700'>
          Description (Optional)
        </label>
        <div className='mt-1'>
          <Controller
            name='description'
            control={control}
            render={({ field }) => (
              <textarea
                {...field}
                id='description'
                rows={3}
                className={`shadow-sm focus:ring-indigo-500 focus:border-indigo-500 block w-full sm:text-sm border ${
                  errors.description ? 'border-red-300' : 'border-gray-300'
                } rounded-md`}
                placeholder='Add a description...'
                disabled={isSubmitting}
              />
            )}
          />
        </div>
        {errors.description && (
          <p className='mt-1 text-sm text-red-600'>{errors.description.message}</p>
        )}
      </div>

      <div className='flex justify-end space-x-3'>
        <button
          type='button'
          onClick={onCancel}
          disabled={isSubmitting}
          className='bg-white py-2 px-4 border border-gray-300 rounded-md shadow-sm text-sm font-medium text-gray-700 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 disabled:opacity-50'
        >
          Cancel
        </button>
        <button
          type='submit'
          disabled={isSubmitting}
          className='inline-flex justify-center py-2 px-4 border border-transparent shadow-sm text-sm font-medium rounded-md text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 disabled:opacity-50'
        >
          {isSubmitting ? (
            <>
              <svg
                className='animate-spin -ml-1 mr-2 h-4 w-4 text-white'
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
              Saving...
            </>
          ) : (
            'Save'
          )}
        </button>
      </div>
    </form>
  );
};

export default SavingForm;
