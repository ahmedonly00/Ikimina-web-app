import React, { createContext, useContext, useEffect, useState } from 'react';
import { useGetPublicGroupsQuery } from '../app/api/apiSlice';
import { useAppSelector } from '../app/hooks';
import { selectCurrentGroup, setCurrentGroup } from '../features/auth/authSlice';
import { useDispatch } from 'react-redux';

const GroupContext = createContext();

export const GroupProvider = ({ children }) => {
  const dispatch = useDispatch();
  const currentGroup = useAppSelector(selectCurrentGroup);
  const { data: groups = [], isLoading, isError } = useGetPublicGroupsQuery();
  const [isChangingGroup, setIsChangingGroup] = useState(false);

  // Set the first group as default if none is selected
  useEffect(() => {
    if (!isLoading && !isError && groups.length > 0 && !currentGroup) {
      dispatch(setCurrentGroup(groups[0]));
    }
  }, [groups, currentGroup, isLoading, isError, dispatch]);

  const changeGroup = groupId => {
    const group = groups.find(g => g.id === groupId);
    if (group) {
      setIsChangingGroup(true);
      // In a real app, you might want to save the selected group to localStorage
      // and update any group-specific data here
      dispatch(setCurrentGroup(group));

      // Simulate loading time
      setTimeout(() => {
        setIsChangingGroup(false);
      }, 500);
    }
  };

  const value = {
    currentGroup,
    groups,
    isLoading,
    isError,
    isChangingGroup,
    changeGroup,
  };

  return <GroupContext.Provider value={value}>{children}</GroupContext.Provider>;
};

export const useGroup = () => {
  const context = useContext(GroupContext);
  if (context === undefined) {
    throw new Error('useGroup must be used within a GroupProvider');
  }
  return context;
};

export default GroupContext;
