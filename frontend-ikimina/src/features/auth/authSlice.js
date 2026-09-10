import { createSlice } from '@reduxjs/toolkit';

const TOKEN_KEY = 'token';
const USER_KEY = 'user';
const GROUP_KEY = 'currentGroup';

// Reading the persisted session is the only way a hard refresh can keep the
// user signed in - Redux state itself does not survive a page load.
const readStored = key => {
  try {
    const raw = localStorage.getItem(key);
    return raw ? JSON.parse(raw) : null;
  } catch {
    // Corrupt or unavailable storage - fall back to a signed-out session.
    return null;
  }
};

const writeStored = (key, value) => {
  try {
    if (value === null || value === undefined) {
      localStorage.removeItem(key);
    } else {
      localStorage.setItem(key, JSON.stringify(value));
    }
  } catch {
    // Storage can be unavailable (private mode, quota). Non-fatal.
  }
};

const loadInitialState = () => {
  let token = null;
  try {
    token = localStorage.getItem(TOKEN_KEY);
  } catch {
    token = null;
  }
  const user = readStored(USER_KEY);

  // Only treat the session as authenticated when both halves are present.
  if (!token || !user) {
    return {
      user: null,
      token: null,
      isAuthenticated: false,
      loading: false,
      error: null,
      currentGroup: null,
    };
  }

  return {
    user,
    token,
    isAuthenticated: true,
    loading: false,
    error: null,
    currentGroup: readStored(GROUP_KEY),
  };
};

// Handsets are commonly shared in these groups, so signing out must also drop
// any contributions queued offline by the outgoing member - otherwise the next
// person to sign in could replay them, or simply see them.
const clearOfflineQueue = () => {
  try {
    if (typeof indexedDB !== 'undefined') {
      indexedDB.deleteDatabase('ikimina-offline');
    }
  } catch {
    // Nothing to clear, or storage unavailable.
  }
};

const clearStoredSession = () => {
  try {
    localStorage.removeItem(TOKEN_KEY);
  } catch {
    // ignore
  }
  writeStored(USER_KEY, null);
  writeStored(GROUP_KEY, null);
  clearOfflineQueue();
};

const authSlice = createSlice({
  name: 'auth',
  initialState: loadInitialState(),
  reducers: {
    setCredentials: (state, action) => {
      const { user, token, savingsGroupId, savingsGroupName } = action.payload;

      state.user = user;
      state.token = token;
      state.isAuthenticated = true;
      state.error = null;

      if (savingsGroupId) {
        state.currentGroup = { id: savingsGroupId, name: savingsGroupName };
      }

      try {
        localStorage.setItem(TOKEN_KEY, token);
      } catch {
        // ignore
      }
      writeStored(USER_KEY, user);
      writeStored(GROUP_KEY, state.currentGroup);
    },

    setCurrentGroup: (state, action) => {
      state.currentGroup = action.payload;
      writeStored(GROUP_KEY, action.payload);
    },

    logout: state => {
      state.user = null;
      state.token = null;
      state.isAuthenticated = false;
      state.error = null;
      state.currentGroup = null;
      clearStoredSession();
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

export const selectCurrentUser = state => state.auth.user;
export const selectCurrentToken = state => state.auth.token;
export const selectIsAuthenticated = state => state.auth.isAuthenticated;
export const selectCurrentGroup = state => state.auth.currentGroup;
export const selectUserRole = state => state.auth.user?.role ?? null;

export default authSlice.reducer;
