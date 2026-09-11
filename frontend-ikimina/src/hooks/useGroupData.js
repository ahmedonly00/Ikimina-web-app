import { useEffect, useState } from 'react';
import { useGroup } from '../contexts/GroupContext';

/**
 * Custom hook to fetch and manage group-specific data
 * @param {Function} fetchFunction - The function to fetch data (should accept groupId as parameter)
 * @param {Array} dependencies - Additional dependencies to watch for refetching
 * @returns {Object} - { data, loading, error, refetch }
 */
export const useGroupData = (fetchFunction, dependencies = []) => {
  const { currentGroup, isChangingGroup } = useGroup();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const fetchData = async () => {
    if (!currentGroup?.id) return;

    try {
      setLoading(true);
      setError(null);
      const result = await fetchFunction(currentGroup.id);
      setData(result);
    } catch (err) {
      console.error('Error fetching group data:', err);
      setError(err.message || 'Failed to fetch data');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (currentGroup?.id && !isChangingGroup) {
      fetchData();
    }
    // fetchData is redefined every render, so it is intentionally omitted; the
    // spread of caller-supplied dependencies also cannot be checked statically.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentGroup?.id, isChangingGroup, ...dependencies]);

  return {
    data,
    loading,
    error,
    refetch: fetchData,
    currentGroup,
  };
};

export default useGroupData;
