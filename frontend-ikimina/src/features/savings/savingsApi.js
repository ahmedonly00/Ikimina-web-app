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
  }),
});

export const {
  useGetSavingsQuery,
  useGetSavingByIdQuery,
  useCreateSavingMutation,
  useUpdateSavingMutation,
  useDeleteSavingMutation,
} = savingsApi;
