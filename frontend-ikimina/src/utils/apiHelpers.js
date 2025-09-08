import { store } from '../app/store';
import { selectCurrentGroup } from '../features/auth/authSlice';

/**
 * Helper function to add the current group ID to request parameters
 * @param {Object} params - The original request parameters
 * @returns {Object} - Parameters with groupId added if available
 */
export const withGroupId = (params = {}) => {
  const state = store.getState();
  const currentGroup = selectCurrentGroup(state);
  
  if (!currentGroup?.id) {
    return params;
  }
  
  return {
    ...params,
    groupId: currentGroup.id
  };
};

/**
 * Helper function to create URL with group ID for API endpoints
 * @param {string} baseUrl - The base URL of the endpoint
 * @returns {string} - URL with group ID if available
 */
export const createGroupedUrl = (baseUrl) => {
  const state = store.getState();
  const currentGroup = selectCurrentGroup(state);
  
  if (!currentGroup?.id) {
    return baseUrl;
  }
  
  // Handle URLs with existing query parameters
  const separator = baseUrl.includes('?') ? '&' : '?';
  return `${baseUrl}${separator}groupId=${currentGroup.id}`;
};

/**
 * Helper function to add group ID to request body
 * @param {Object} data - The request body
 * @returns {Object} - Request body with group ID added if available
 */
export const withGroupData = (data = {}) => {
  const state = store.getState();
  const currentGroup = selectCurrentGroup(state);
  
  if (!currentGroup?.id) {
    return data;
  }
  
  return {
    ...data,
    savingsGroupId: currentGroup.id
  };
};
