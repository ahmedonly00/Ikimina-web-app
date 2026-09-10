import React from 'react';
import { FaExclamationCircle } from 'react-icons/fa';

/**
 * Renders a failed data load.
 *
 * Query errors used to be destructured and dropped, so a member whose request
 * failed saw an empty table and assumed their money was missing. Saying "we
 * could not load this" is very different from showing nothing.
 */
const QueryError = ({ error, onRetry, title = 'Could not load this data' }) => {
  if (!error) return null;

  const detail =
    (error.data && (error.data.detail || error.data.message)) ||
    (error.status === 'FETCH_ERROR' ? 'The server could not be reached.' : null) ||
    (typeof error.status === 'number' ? `Request failed with status ${error.status}.` : null) ||
    'An unexpected error occurred.';

  return (
    <div className='mb-4 rounded-md border-l-4 border-red-400 bg-red-50 p-4' role='alert'>
      <div className='flex items-start'>
        <FaExclamationCircle className='mt-0.5 mr-3 flex-shrink-0 text-red-500' />
        <div className='flex-1'>
          <p className='text-sm font-medium text-red-800'>{title}</p>
          <p className='mt-1 text-sm text-red-700'>{detail}</p>
          {onRetry && (
            <button
              type='button'
              onClick={onRetry}
              className='mt-2 rounded-md border border-red-300 bg-surface px-3 py-1.5 text-sm font-medium text-red-800 hover:bg-red-100'
            >
              Try again
            </button>
          )}
        </div>
      </div>
    </div>
  );
};

export default QueryError;
