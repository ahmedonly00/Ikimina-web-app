import React, { useState, useEffect } from 'react';
import { useGetPublicGroupsQuery } from '../../app/api/apiSlice';
import { useAppDispatch, useAppSelector } from '../../app/hooks';
import { setCurrentGroup, selectCurrentGroup } from '../auth/authSlice';
import { FaUsers, FaChevronDown, FaCheck } from 'react-icons/fa';

export const GroupSelector = () => {
  const dispatch = useAppDispatch();
  const currentGroup = useAppSelector(selectCurrentGroup);
  const { data: groups = [], isLoading } = useGetPublicGroupsQuery();
  const [isOpen, setIsOpen] = useState(false);

  // Set initial group if not set
  useEffect(() => {
    if (!currentGroup && groups.length > 0) {
      dispatch(setCurrentGroup(groups[0]));
    }
  }, [groups, currentGroup, dispatch]);

  if (isLoading) {
    return (
      <div className='flex items-center px-4 py-2 text-fg-muted'>
        <div className='animate-pulse flex space-x-2'>
          <div className='h-4 w-4 bg-surface-3 rounded'></div>
          <div className='h-4 w-24 bg-surface-3 rounded'></div>
        </div>
      </div>
    );
  }

  if (groups.length === 0) {
    return null;
  }

  return (
    <div className='relative'>
      <button
        onClick={() => setIsOpen(!isOpen)}
        className='flex items-center space-x-2 px-4 py-2 text-sm font-medium text-fg hover:bg-surface-2 rounded-md transition-colors duration-200'
      >
        <FaUsers className='text-primary' />
        <span className='truncate max-w-xs'>{currentGroup?.name || 'Select Group'}</span>
        <FaChevronDown
          className={`text-xs text-fg-muted transition-transform duration-200 ${isOpen ? 'transform rotate-180' : ''}`}
        />
      </button>

      {isOpen && (
        <div className='absolute right-0 mt-2 w-56 bg-surface rounded-md shadow-lg overflow-hidden z-50'>
          <div className='py-1'>
            <div className='px-4 py-2 text-xs font-semibold text-fg-muted uppercase tracking-wider'>
              Switch Group
            </div>
            {groups.map(group => (
              <button
                key={group.id}
                onClick={() => {
                  dispatch(setCurrentGroup(group));
                  setIsOpen(false);
                }}
                className={`w-full text-left px-4 py-2 text-sm flex items-center justify-between ${
                  currentGroup?.id === group.id
                    ? 'bg-primary-subtle text-primary'
                    : 'text-fg hover:bg-surface-2'
                }`}
              >
                <span className='truncate'>{group.name}</span>
                {currentGroup?.id === group.id && <FaCheck className='text-primary' />}
              </button>
            ))}
            <div className='border-t border-border'>
              <button
                onClick={() => {
                  // Handle create new group
                  setIsOpen(false);
                }}
                className='w-full text-left px-4 py-2 text-sm text-primary hover:bg-primary-subtle'
              >
                + Create New Group
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default GroupSelector;
