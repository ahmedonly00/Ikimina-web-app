import React from 'react';
import { format } from 'date-fns';
import { PencilIcon, TrashIcon, PlusIcon } from '@heroicons/react/24/outline';

export const SavingsList = ({ savings = [], onAdd, onEdit, onDelete, isLoading = false }) => {
  if (isLoading) {
    return (
      <div className='flex justify-center items-center h-64'>
        <div className='animate-spin rounded-full h-12 w-12 border-t-2 border-b-2 border-indigo-500'></div>
      </div>
    );
  }

  if (!savings || savings.length === 0) {
    return (
      <div className='text-center py-12'>
        <svg
          className='mx-auto h-12 w-12 text-gray-400'
          fill='none'
          viewBox='0 0 24 24'
          stroke='currentColor'
          aria-hidden='true'
        >
          <path
            strokeLinecap='round'
            strokeLinejoin='round'
            strokeWidth='2'
            d='M9 13h6m-3-3v6m-9 1V7a2 2 0 012-2h6l2 2h6a2 2 0 012 2v8a2 2 0 01-2 2H5a2 2 0 01-2-2z'
          />
        </svg>
        <h3 className='mt-2 text-sm font-medium text-gray-900'>No savings</h3>
        <p className='mt-1 text-sm text-gray-500'>Get started by creating a new saving.</p>
        <div className='mt-6'>
          <button
            type='button'
            onClick={onAdd}
            className='inline-flex items-center px-4 py-2 border border-transparent shadow-sm text-sm font-medium rounded-md text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500'
          >
            <PlusIcon className='-ml-1 mr-2 h-5 w-5' aria-hidden='true' />
            New Saving
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className='flex flex-col'>
      <div className='-my-2 overflow-x-auto sm:-mx-6 lg:-mx-8'>
        <div className='py-2 align-middle inline-block min-w-full sm:px-6 lg:px-8'>
          <div className='shadow overflow-hidden border-b border-gray-200 sm:rounded-lg'>
            <table className='min-w-full divide-y divide-gray-200'>
              <thead className='bg-gray-50'>
                <tr>
                  <th
                    scope='col'
                    className='px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider'
                  >
                    Member
                  </th>
                  <th
                    scope='col'
                    className='px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider'
                  >
                    Date
                  </th>
                  <th
                    scope='col'
                    className='px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider'
                  >
                    Ubwizigame
                  </th>
                  <th
                    scope='col'
                    className='px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider'
                  >
                    Ingoboka
                  </th>
                  <th
                    scope='col'
                    className='px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider'
                  >
                    Total
                  </th>
                  <th
                    scope='col'
                    className='px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider'
                  >
                    Description
                  </th>
                  <th scope='col' className='relative px-6 py-3'>
                    <span className='sr-only'>Actions</span>
                  </th>
                </tr>
              </thead>
              <tbody className='bg-white divide-y divide-gray-200'>
                {savings.map(saving => (
                  <tr key={saving.id}>
                    <td className='px-6 py-4 whitespace-nowrap'>
                      <div>
                        <div className='text-sm font-medium text-gray-900'>{saving.memberName}</div>
                        <div className='text-xs text-gray-500'>{saving.memberNumber}</div>
                      </div>
                    </td>
                    <td className='px-6 py-4 whitespace-nowrap text-sm text-gray-500'>
                      {format(new Date(saving.savingDate), 'MMM dd, yyyy')}
                    </td>
                    <td className='px-6 py-4 whitespace-nowrap'>
                      <div className='text-sm font-medium text-purple-600'>
                        ${saving.ubwizigameAmount.toLocaleString()}
                      </div>
                    </td>
                    <td className='px-6 py-4 whitespace-nowrap'>
                      <div className='text-sm font-medium text-blue-600'>
                        ${saving.ingobokaAmount.toLocaleString()}
                      </div>
                    </td>
                    <td className='px-6 py-4 whitespace-nowrap'>
                      <div className='text-sm font-semibold text-gray-900'>
                        ${(saving.ubwizigameAmount + saving.ingobokaAmount).toLocaleString()}
                      </div>
                    </td>
                    <td className='px-6 py-4 text-sm text-gray-500'>
                      {saving.description || 'No description'}
                    </td>
                    <td className='px-6 py-4 whitespace-nowrap text-right text-sm font-medium'>
                      <button
                        onClick={() => onEdit(saving)}
                        className='text-indigo-600 hover:text-indigo-900 mr-4'
                      >
                        <PencilIcon className='h-5 w-5' aria-hidden='true' />
                      </button>
                      <button
                        onClick={() => onDelete(saving.id)}
                        className='text-red-600 hover:text-red-900'
                      >
                        <TrashIcon className='h-5 w-5' aria-hidden='true' />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  );
};

export default SavingsList;
