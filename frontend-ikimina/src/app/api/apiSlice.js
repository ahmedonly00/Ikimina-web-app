import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';

const API_URL = 'http://localhost:8082/api';

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
        // The backend returns { token: 'jwt-token', type: 'Bearer' }
        if (response && response.token) {
          localStorage.setItem('token', response.token);
          return {
            token: response.token,
            user: { email: response.email } // Add any other user data you want to store
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
        url: '/api/savings-groups',
        method: 'POST',
        body: groupData,
      }),
      invalidatesTags: ['SavingsGroups'],
    }),
    
    getAllGroups: builder.query({
      query: () => '/api/savings-groups',
      providesTags: ['SavingsGroups'],
    }),
    
    getGroupById: builder.query({
      query: (groupId) => `/api/savings-groups/${groupId}`,
      providesTags: (result, error, groupId) => [{ type: 'SavingsGroups', id: groupId }],
    }),
    
    updateGroup: builder.mutation({
      query: ({ groupId, ...updates }) => ({
        url: `/api/savings-groups/${groupId}`,
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
        url: `/api/savings-groups/${groupId}`,
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
} = apiSlice;

export default apiSlice;
