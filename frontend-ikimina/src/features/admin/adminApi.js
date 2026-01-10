import { apiSlice } from '../../app/api/apiSlice';

export const adminApi = apiSlice.injectEndpoints({
  endpoints: (builder) => ({
    // Group Management
    getAllGroups: builder.query({
      query: () => '/super-admin/groups',
      providesTags: ['Groups'],
    }),
    
    activateGroup: builder.mutation({
      query: ({ groupId, reason }) => ({
        url: `/super-admin/groups/${groupId}/activate`,
        method: 'POST',
        body: reason,
      }),
      invalidatesTags: ['Groups'],
    }),
    
    suspendGroup: builder.mutation({
      query: ({ groupId, reason }) => ({
        url: `/super-admin/groups/${groupId}/suspend`,
        method: 'POST',
        body: reason,
      }),
      invalidatesTags: ['Groups'],
    }),
    
    // User Management
    getAllUsers: builder.query({
      query: () => '/super-admin/users',
      providesTags: ['Users'],
    }),
    
    promoteToAdmin: builder.mutation({
      query: (userId) => ({
        url: `/super-admin/users/${userId}/promote-admin`,
        method: 'POST',
      }),
      invalidatesTags: ['Users'],
    }),
    
    demoteFromAdmin: builder.mutation({
      query: (userId) => ({
        url: `/super-admin/users/${userId}/demote-admin`,
        method: 'POST',
      }),
      invalidatesTags: ['Users'],
    }),
    
    suspendUser: builder.mutation({
      query: ({ userId, reason }) => ({
        url: `/super-admin/users/${userId}/suspend`,
        method: 'POST',
        body: reason,
      }),
      invalidatesTags: ['Users'],
    }),
    
    // Subscription Management
    getAllSubscriptions: builder.query({
      query: () => '/super-admin/subscriptions',
      providesTags: ['Subscriptions'],
    }),
    
    manuallyActivateSubscription: builder.mutation({
      query: ({ subscriptionId, reason }) => ({
        url: `/super-admin/subscriptions/${subscriptionId}/activate`,
        method: 'POST',
        body: reason,
      }),
      invalidatesTags: ['Subscriptions'],
    }),
    
    getPaymentHistory: builder.query({
      query: (groupId) => `/super-admin/groups/${groupId}/payments`,
      providesTags: ['Payments'],
    }),
    
    // Subscription Plans
    getAllPlans: builder.query({
      query: () => '/super-admin/plans',
      providesTags: ['Plans'],
    }),
    
    createPlan: builder.mutation({
      query: (plan) => ({
        url: '/super-admin/plans',
        method: 'POST',
        body: plan,
      }),
      invalidatesTags: ['Plans'],
    }),
    
    // Audit Logs
    getAuditLogs: builder.query({
      query: ({ entityType, since }) => ({
        url: '/super-admin/audit',
        params: { entityType, since },
      }),
      providesTags: ['AuditLogs'],
    }),
    
    // Financial Reports
    getFinancialReport: builder.query({
      query: () => '/super-admin/reports/financial',
      providesTags: ['Reports'],
    }),
  }),
});

export const {
  useGetAllGroupsQuery,
  useActivateGroupMutation,
  useSuspendGroupMutation,
  useGetAllUsersQuery,
  usePromoteToAdminMutation,
  useDemoteFromAdminMutation,
  useSuspendUserMutation,
  useGetAllSubscriptionsQuery,
  useManuallyActivateSubscriptionMutation,
  useGetPaymentHistoryQuery,
  useGetAllPlansQuery,
  useCreatePlanMutation,
  useGetAuditLogsQuery,
  useGetFinancialReportQuery,
} = adminApi;
