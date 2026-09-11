import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';
import { logout } from '../../features/auth/authSlice';

// Requests go through the Vite dev proxy (see vite.config.js), which forwards
// /api straight through to the backend. Every controller is mapped under /api.
const API_URL = '/api';

// Paginated backend endpoints return a Spring Data Page envelope
// ({ content, totalElements, totalPages, number, size }). Components here work
// with plain arrays, so unwrap in one place rather than at every call site.
// Attach the metadata to the array so callers that need totals can read it.
export const unwrapPage = response => {
  if (response && Array.isArray(response.content)) {
    const items = response.content;
    items.page = {
      number: response.number,
      size: response.size,
      totalElements: response.totalElements,
      totalPages: response.totalPages,
    };
    return items;
  }
  return response;
};

const rawBaseQuery = fetchBaseQuery({
  baseUrl: API_URL,
  prepareHeaders: headers => {
    const token = localStorage.getItem('token');
    if (token) {
      headers.set('Authorization', `Bearer ${token}`);
    }
    return headers;
  },
});

// Centralised handling for expired / rejected credentials. Dispatches logout so
// the Redux store and localStorage stay in sync instead of hard-navigating.
const baseQueryWithAuth = async (args, api, extraOptions) => {
  const result = await rawBaseQuery(args, api, extraOptions);

  if (result.error?.status === 401) {
    api.dispatch(logout());
  }

  return result;
};

export const apiSlice = createApi({
  reducerPath: 'api',
  baseQuery: baseQueryWithAuth,
  // Every tag referenced by any injected endpoint must be declared here, or
  // RTK Query drops the invalidation silently.
  tagTypes: [
    'User',
    'Users',
    'Savings',
    'SavingsCycles',
    'MemberPayouts',
    'Loans',
    'Fines',
    'Reports',
    'SavingsGroups',
    'Groups',
    'Subscriptions',
    'Payments',
    'Plans',
    'AuditLogs',
  ],
  endpoints: builder => ({
    // ---- Authentication ----
    login: builder.mutation({
      query: credentials => ({
        url: '/auth/login',
        method: 'POST',
        body: credentials,
      }),
      transformResponse: response => {
        // Backend returns { token, type, user: { id, email, firstName, lastName,
        // role, memberNumber, savingsGroupId } }
        if (response?.token) {
          return {
            token: response.token,
            user: {
              id: response.user?.id,
              email: response.user?.email,
              firstName: response.user?.firstName,
              lastName: response.user?.lastName,
              role: response.user?.role,
              memberNumber: response.user?.memberNumber,
              savingsGroupId: response.user?.savingsGroupId,
            },
          };
        }
        return response;
      },
    }),

    register: builder.mutation({
      query: userData => ({
        url: '/auth/register',
        method: 'POST',
        body: userData,
      }),
    }),

    // ---- Savings groups ----
    // Unauthenticated: id + name only. Used by the login/register group picker
    // and by GroupContext, which mounts for every role.
    getPublicGroups: builder.query({
      query: () => '/savings-groups/public',
      providesTags: ['SavingsGroups'],
    }),

    // Full listing - super admin only on the backend.
    getAllGroups: builder.query({
      query: () => '/savings-groups',
      providesTags: ['SavingsGroups'],
    }),

    getGroupById: builder.query({
      query: groupId => `/savings-groups/${groupId}`,
      providesTags: (result, error, groupId) => [{ type: 'SavingsGroups', id: groupId }],
    }),

    createSavingsGroup: builder.mutation({
      query: groupData => ({
        url: '/savings-groups',
        method: 'POST',
        body: groupData,
      }),
      invalidatesTags: ['SavingsGroups'],
    }),

    deleteGroup: builder.mutation({
      query: groupId => ({
        url: `/savings-groups/${groupId}`,
        method: 'DELETE',
      }),
      invalidatesTags: ['SavingsGroups'],
    }),

    // ---- Savings ----
    getSavings: builder.query({
      query: userId => `/savings/user/${userId}`,
      transformResponse: unwrapPage,
      providesTags: ['Savings'],
    }),

    // Every member's savings for one group. The admin savings screen used
    // getSavings (one user) and so showed an admin only their own entries.
    // `from`/`to` are pushed to the server so a summed total covers the whole
    // range rather than whatever fitted on the first page.
    getGroupSavings: builder.query({
      query: ({ groupId, from, to } = {}) => ({
        url: `/savings/groups/${groupId}`,
        params: { ...(from ? { from } : {}), ...(to ? { to } : {}) },
      }),
      transformResponse: unwrapPage,
      providesTags: ['Savings'],
    }),

    createSaving: builder.mutation({
      query: savingData => ({
        url: '/savings',
        method: 'POST',
        body: savingData,
      }),
      invalidatesTags: ['Savings'],
    }),

    // ---- Loans ----
    getLoans: builder.query({
      query: userId => `/loans/user/${userId}`,
      transformResponse: unwrapPage,
      providesTags: ['Loans'],
    }),

    requestLoan: builder.mutation({
      query: loanData => ({
        url: '/loans/request',
        method: 'POST',
        body: loanData,
      }),
      invalidatesTags: ['Loans'],
    }),

    // Approving or rejecting a loan. There is deliberately no delete endpoint:
    // financial records are not removed, their status changes.
    updateLoanStatus: builder.mutation({
      query: ({ loanId, status }) => ({
        url: `/loans/${loanId}/status`,
        method: 'PUT',
        body: status,
      }),
      invalidatesTags: ['Loans'],
    }),

    // ---- Fines ----
    getFines: builder.query({
      query: userId => `/fines/user/${userId}`,
      transformResponse: unwrapPage,
      providesTags: ['Fines'],
    }),

    // ---- Users ----
    getUserProfile: builder.query({
      query: userId => `/users/${userId}`,
      providesTags: ['User'],
    }),

    // The group's roster. Deliberately unpaged server-side: the caller is
    // looking at a whole membership list, not a feed.
    getGroupMembers: builder.query({
      query: groupId => `/users/group/${groupId}`,
      providesTags: ['Users'],
    }),

    // Creating a member goes through register, which is the only creation
    // path the backend exposes.
    createMember: builder.mutation({
      query: member => ({
        url: '/auth/register',
        method: 'POST',
        body: member,
      }),
      invalidatesTags: ['Users'],
    }),

    setMemberActive: builder.mutation({
      query: ({ userId, active }) => ({
        url: `/users/${userId}/status`,
        method: 'PUT',
        params: { active },
      }),
      invalidatesTags: ['Users'],
    }),

    // ---- Reports ----
    generateReport: builder.mutation({
      query: ({ userId, period }) => ({
        url: `/reports/generate/${userId}`,
        method: 'POST',
        params: { period },
      }),
      invalidatesTags: ['Reports'],
    }),

    // Real group figures for the dashboard, derived from the ledger.
    getGroupSummary: builder.query({
      query: groupId => `/ledger/groups/${groupId}/summary`,
      providesTags: ['Savings', 'SavingsGroups'],
    }),

    getUserReports: builder.query({
      query: userId => `/reports/user/${userId}`,
      transformResponse: unwrapPage,
      providesTags: ['Reports'],
    }),
  }),
});

export const {
  useLoginMutation,
  useRegisterMutation,
  useGetPublicGroupsQuery,
  useGetAllGroupsQuery,
  useGetGroupByIdQuery,
  useCreateSavingsGroupMutation,
  useDeleteGroupMutation,
  useGetSavingsQuery,
  useGetGroupSavingsQuery,
  useCreateSavingMutation,
  useGetLoansQuery,
  useRequestLoanMutation,
  useUpdateLoanStatusMutation,
  useGetFinesQuery,
  useGetUserProfileQuery,
  useGetGroupMembersQuery,
  useCreateMemberMutation,
  useSetMemberActiveMutation,
  useGenerateReportMutation,
  useGetUserReportsQuery,
  useGetGroupSummaryQuery,
} = apiSlice;

export default apiSlice;
