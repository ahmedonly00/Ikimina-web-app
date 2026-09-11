import { apiSlice, unwrapPage } from '../../app/api/apiSlice';

// Savings endpoints that are not part of the base apiSlice. Endpoint names must
// stay unique across every injectEndpoints call - RTK Query keeps the first
// registration and silently ignores later duplicates.
export const savingsApi = apiSlice.injectEndpoints({
  endpoints: builder => ({
    createBulkSavings: builder.mutation({
      query: bulkData => ({
        url: '/savings/bulk',
        method: 'POST',
        body: bulkData,
      }),
      invalidatesTags: ['Savings'],
    }),

    // Backend: GET /api/savings/ledger?userIds=..&startDate=..&endDate=..
    getSavingsLedger: builder.query({
      query: ({ userIds, startDate, endDate }) => ({
        url: '/savings/ledger',
        params: { userIds, startDate, endDate },
      }),
      providesTags: ['Savings'],
    }),

    getUsers: builder.query({
      query: () => '/users/active',
      transformResponse: unwrapPage,
      providesTags: ['Users'],
    }),
  }),
});

export const { useCreateBulkSavingsMutation, useGetSavingsLedgerQuery, useGetUsersQuery } =
  savingsApi;
