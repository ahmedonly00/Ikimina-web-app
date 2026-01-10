import { apiSlice } from '../../app/api/apiSlice';

export const savingsApi = apiSlice.injectEndpoints({
  endpoints: (builder) => ({
    getSavings: builder.query({
      query: () => '/savings',
      providesTags: ['Savings'],
    }),
    getSavingById: builder.query({
      query: (id) => `/savings/${id}`,
      providesTags: (result, error, id) => [{ type: 'Savings', id }],
    }),
    createSaving: builder.mutation({
      query: (saving) => ({
        url: '/savings',
        method: 'POST',
        body: saving,
      }),
      invalidatesTags: ['Savings'],
    }),
    updateSaving: builder.mutation({
      query: ({ id, ...updates }) => ({
        url: `/savings/${id}`,
        method: 'PATCH',
        body: updates,
      }),
      invalidatesTags: (result, error, { id }) => [
        { type: 'Savings', id },
        'Savings',
      ],
    }),
    deleteSaving: builder.mutation({
      query: (id) => ({
        url: `/savings/${id}`,
        method: 'DELETE',
      }),
      invalidatesTags: ['Savings'],
    }),
    createBulkSavings: builder.mutation({
      query: (bulkData) => ({
        url: '/savings/bulk',
        method: 'POST',
        body: bulkData,
      }),
      invalidatesTags: ['Savings'],
    }),
    getSavingsLedger: builder.query({
      query: ({ userIds, startDate, endDate }) => ({
        url: '/savings/ledger',
        params: { userIds, startDate, endDate },
      }),
      providesTags: ['Savings'],
    }),
    getUsers: builder.query({
      query: () => '/users/active',
      providesTags: ['Users'],
    }),
  }),
});

export const {
  useGetSavingsQuery,
  useGetSavingByIdQuery,
  useCreateSavingMutation,
  useUpdateSavingMutation,
  useDeleteSavingMutation,
  useCreateBulkSavingsMutation,
  useGetSavingsLedgerQuery,
  useGetUsersQuery,
} = savingsApi;
