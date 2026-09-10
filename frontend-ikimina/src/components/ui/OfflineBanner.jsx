import React from 'react';
import { FaWifi, FaCloudUploadAlt, FaSpinner } from 'react-icons/fa';

/**
 * Shows connection state and anything still waiting to sync.
 *
 * Deliberately explicit about the difference between "saved on this phone" and
 * "recorded by the group". A member who is told their contribution is recorded
 * when it is only queued locally will believe the group has their money.
 */
const OfflineBanner = ({ isOnline, pendingCount, isReplaying, onRetry }) => {
  if (isOnline && pendingCount === 0) return null;

  if (!isOnline) {
    return (
      <div
        className='mb-4 rounded-md border-l-4 border-amber-400 bg-amber-50 p-3'
        role='status'
        aria-live='polite'
      >
        <div className='flex items-center'>
          <FaWifi className='mr-2 flex-shrink-0 text-amber-600' aria-hidden='true' />
          <p className='text-sm text-amber-900'>
            No connection. Entries are saved on this device and will be sent to the group
            automatically once you are back online
            {pendingCount > 0 ? ` (${pendingCount} waiting)` : ''}.
          </p>
        </div>
      </div>
    );
  }

  return (
    <div
      className='mb-4 rounded-md border-l-4 border-blue-400 bg-blue-50 p-3'
      role='status'
      aria-live='polite'
    >
      <div className='flex items-center justify-between gap-3'>
        <div className='flex items-center'>
          {isReplaying ? (
            <FaSpinner
              className='mr-2 flex-shrink-0 animate-spin text-blue-600'
              aria-hidden='true'
            />
          ) : (
            <FaCloudUploadAlt className='mr-2 flex-shrink-0 text-blue-600' aria-hidden='true' />
          )}
          <p className='text-sm text-blue-900'>
            {isReplaying
              ? `Sending ${pendingCount} saved entr${pendingCount === 1 ? 'y' : 'ies'}...`
              : `${pendingCount} entr${pendingCount === 1 ? 'y is' : 'ies are'} saved on this device and not yet recorded by the group.`}
          </p>
        </div>
        {!isReplaying && onRetry && (
          <button
            type='button'
            onClick={onRetry}
            className='flex-shrink-0 rounded-md border border-blue-300 bg-white px-3 py-1.5 text-sm font-medium text-blue-800 hover:bg-blue-100'
          >
            Send now
          </button>
        )}
      </div>
    </div>
  );
};

export default OfflineBanner;
