import { createSlice } from '@reduxjs/toolkit';

const initialState = {
  user: null,
  token: null,
  isAuthenticated: false,
  loading: false,
  error: null,
  currentGroup: null, // Current savings group info
};

const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    setCredentials: (state, action) => {
      const { user, token, savingsGroupId, savingsGroupName } = action.payload;
      state.user = user || {}; // Handle case where user might be undefined
      state.token = token;
      state.isAuthenticated = !!token;
      state.error = null;
      
      // Set current group info if available
      if (savingsGroupId) {
        state.currentGroup = {
          id: savingsGroupId,
          name: savingsGroupName || 'My Savings Group'
        };
      }
    },
    
    // Update current group info
    setCurrentGroup: (state, action) => {
      state.currentGroup = action.payload;
    },
    logout: (state) => {
      state.user = null;
      state.token = null;
      state.isAuthenticated = false;
      state.error = null;
    },
    setLoading: (state, action) => {
      state.loading = action.payload;
    },
    setError: (state, action) => {
      state.error = action.payload;
    },
  },
});

export const { setCredentials, logout, setLoading, setError, setCurrentGroup } = authSlice.actions;

export const selectCurrentUser = (state) => state.auth.user;
export const selectIsAuthenticated = (state) => state.auth.isAuthenticated;
export const selectCurrentGroup = (state) => state.auth.currentGroup;

export default authSlice.reducer;
