import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';

// Use relative URL to work with Vite proxy in development
const API_URL = '/api';

// Helper function to handle response errors
const baseQueryWithAuth = async (args, api, extraOptions) => {
  const result = await fetchBaseQuery({
    baseUrl: API_URL,
    credentials: 'include', // Important for CORS with credentials
    prepareHeaders: (headers) => {
      const token = localStorage.getItem('token');
      if (token) {
        headers.set('Authorization', `Bearer ${token}`);
      }
      return headers;
    },
  })(args, api, extraOptions);

  // Handle 401 Unauthorized
  if (result.error) {
    if (result.error.status === 401) {
      localStorage.removeItem('token');
      window.location.href = '/login';
    } else if (result.error.status === 403) {
      console.error('Access forbidden. Please check your permissions.');
    }
  }

  return result;
};

export const apiSlice = createApi({
  reducerPath: 'api',
  baseQuery: baseQueryWithAuth,
  tagTypes: ['User', 'Savings', 'Loans', 'Fines', 'Reports', 'Transactions', 'SavingsGroups'],
  endpoints: (builder) => ({
    // Authentication endpoints
    login: builder.mutation({
      query: (credentials) => ({
        url: '/auth/login',
        method: 'POST',
        body: credentials,
      }),
      transformResponse: (response) => {
        // The backend returns { token: 'jwt-token', type: 'Bearer', user: { email: '...', firstName: '...', lastName: '...', role: '...', id: ..., savingsGroupId: ... } }
        if (response && response.token) {
          localStorage.setItem('token', response.token);
          return {
            token: response.token,
            user: {
              id: response.user.id,
              email: response.user.email,
              firstName: response.user.firstName,
              lastName: response.user.lastName,
              role: response.user.role,
              memberNumber: response.user.memberNumber,
              savingsGroupId: response.user.savingsGroupId
            }
          };
        }
        return response;
      },
    }),
    
    register: builder.mutation({
      query: (userData) => ({
        url: '/auth/register',
        method: 'POST',
        body: userData,
      }),
    }),
    
    // Savings endpoints
    getSavings: builder.query({
      query: (userId) => `/savings/user/${userId}`,
      providesTags: ['Savings'],
    }),
    
    createSaving: builder.mutation({
      query: (savingData) => ({
        url: '/savings',
        method: 'POST',
        body: savingData,
      }),
      invalidatesTags: ['Savings'],
    }),
    
    // Loans endpoints
    getLoans: builder.query({
      query: (userId) => `/loans/user/${userId}`,
      providesTags: ['Loans'],
    }),
    
    requestLoan: builder.mutation({
      query: (loanData) => ({
        url: '/loans',
        method: 'POST',
        body: loanData,
      }),
      invalidatesTags: ['Loans'],
    }),
    
    // Fines endpoints
    getFines: builder.query({
      query: (userId) => `/fines/user/${userId}`,
      providesTags: ['Fines'],
    }),
    
    // Savings Groups endpoints
    createSavingsGroup: builder.mutation({
      query: (groupData) => ({
        url: '/savings-groups',
        method: 'POST',
        body: groupData,
      }),
      invalidatesTags: ['SavingsGroups'],
    }),
    
    getAllGroups: builder.query({
      query: () => '/savings-groups',
      providesTags: ['SavingsGroups'],
    }),
    
    getGroupById: builder.query({
      query: (groupId) => `/savings-groups/${groupId}`,
      providesTags: (result, error, groupId) => [{ type: 'SavingsGroups', id: groupId }],
    }),
    
    updateGroup: builder.mutation({
      query: ({ groupId, ...updates }) => ({
        url: `/savings-groups/${groupId}`,
        method: 'PUT',
        body: updates,
      }),
      invalidatesTags: (result, error, { groupId }) => [
        { type: 'SavingsGroups', id: groupId },
        'SavingsGroups'
      ],
    }),
    
    deleteGroup: builder.mutation({
      query: (groupId) => ({
        url: `/savings-groups/${groupId}`,
        method: 'DELETE',
      }),
      invalidatesTags: ['SavingsGroups'],
    }),
    
    // User endpoints
    getUserProfile: builder.query({
      query: (userId) => `/users/${userId}`,
      providesTags: ['User'],
    }),
    
    // Reports endpoints
    generateReport: builder.mutation({
      query: ({ userId, reportType, startDate, endDate }) => ({
        url: '/reports/generate',
        method: 'POST',
        body: { userId, reportType, startDate, endDate },
      }),
      invalidatesTags: ['Reports'],
    }),
    
    // Transaction history
    getTransactions: builder.query({
      query: (userId) => `/transactions/user/${userId}`,
      providesTags: ['Transactions'],
    }),
    
    // Savings Cycle endpoints
    getCurrentCycle: builder.query({
      query: (groupId) => `/savings-cycles/current/${groupId}`,
      providesTags: ['Savings'],
    }),
    
    startNewCycle: builder.mutation({
      query: (groupId) => ({
        url: `/savings-cycles/start/${groupId}`,
        method: 'POST',
      }),
      invalidatesTags: ['Savings'],
    }),
    
    distributeSavings: builder.mutation({
      query: ({ groupId, distributionData }) => ({
        url: `/savings-cycles/distribute/${groupId}`,
        method: 'POST',
        body: distributionData,
      }),
      invalidatesTags: ['Savings', 'Transactions'],
    }),
    
    // Subscription endpoints
    getSubscription: builder.query({
      query: (groupId) => `/subscriptions/group/${groupId}`,
      providesTags: ['User'],
    }),
    
    createSubscription: builder.mutation({
      query: (subscriptionData) => ({
        url: `/subscriptions`,
        method: 'POST',
        body: subscriptionData,
      }),
      invalidatesTags: ['User'],
    }),
    
    updateSubscription: builder.mutation({
      query: ({ subscriptionId, ...updates }) => ({
        url: `/subscriptions/${subscriptionId}`,
        method: 'PUT',
        body: updates,
      }),
      invalidatesTags: ['User'],
    }),
    
    cancelSubscription: builder.mutation({
      query: (subscriptionId) => ({
        url: `/subscriptions/${subscriptionId}/cancel`,
        method: 'POST',
      }),
      invalidatesTags: ['User'],
    }),
    
    // Super Admin endpoints
    getAllUsers: builder.query({
      query: () => `/admin/users`,
      providesTags: ['User'],
    }),
    
    getAllGroupsAdmin: builder.query({
      query: () => `/admin/groups`,
      providesTags: ['SavingsGroups'],
    }),
    
    getAuditLogs: builder.query({
      query: (filters) => ({
        url: `/admin/audit-logs`,
        method: 'POST',
        body: filters,
      }),
      providesTags: ['Reports'],
    }),
    
    updateUserStatus: builder.mutation({
      query: ({ userId, status }) => ({
        url: `/admin/users/${userId}/status`,
        method: 'PUT',
        body: { status },
      }),
      invalidatesTags: ['User'],
    }),
    
    // Bulk operations
    bulkCreateSavings: builder.mutation({
      query: (bulkData) => ({
        url: `/savings/bulk`,
        method: 'POST',
        body: bulkData,
      }),
      invalidatesTags: ['Savings'],
    }),
    
    getMemberSavingsLedger: builder.query({
      query: ({ userIds, startDate, endDate }) => ({
        url: `/savings/ledger`,
        method: 'POST',
        body: { userIds, startDate, endDate },
      }),
      providesTags: ['Savings', 'Transactions'],
    }),
  }),
});

export const {
  useLoginMutation,
  useRegisterMutation,
  useGetSavingsQuery,
  useCreateSavingMutation,
  useGetLoansQuery,
  useRequestLoanMutation,
  useGetFinesQuery,
  useGetUserProfileQuery,
  useGenerateReportMutation,
  useGetTransactionsQuery,
  useCreateSavingsGroupMutation,
  useGetAllGroupsQuery,
  useGetGroupByIdQuery,
  useUpdateGroupMutation,
  useDeleteGroupMutation,
  // Savings Cycle hooks
  useGetCurrentCycleQuery,
  useStartNewCycleMutation,
  useDistributeSavingsMutation,
  // Subscription hooks
  useGetSubscriptionQuery,
  useCreateSubscriptionMutation,
  useUpdateSubscriptionMutation,
  useCancelSubscriptionMutation,
  // Super Admin hooks
  useGetAllUsersQuery,
  useGetAllGroupsAdminQuery,
  useGetAuditLogsQuery,
  useUpdateUserStatusMutation,
  // Bulk operations
  useBulkCreateSavingsMutation,
  useGetMemberSavingsLedgerQuery,
} = apiSlice;

export default apiSlice;
