// src/features/loans/loansApi.js
import { apiSlice } from '../../app/api/apiSlice';

export const loansApiSlice = apiSlice.injectEndpoints({
  endpoints: builder => ({
    getLoans: builder.query({
      query: () => 'loans',
      providesTags: ['Loans']
    }),
    getLoan: builder.query({
      query: id => `loans/${id}`,
      providesTags: (result, error, id) => [{ type: 'Loan', id }]
    }),
    createLoan: builder.mutation({
      query: loan => ({
        url: 'loans',
        method: 'POST',
        body: loan
      }),
      invalidatesTags: ['Loans']
    }),
    updateLoan: builder.mutation({
      query: ({ id, ...updates }) => ({
        url: `loans/${id}`,
        method: 'PATCH',
        body: updates
      }),
      invalidatesTags: (result, error, { id }) => [{ type: 'Loan', id }, 'Loans']
    }),
    deleteLoan: builder.mutation({
      query: id => ({
        url: `loans/${id}`,
        method: 'DELETE'
      }),
      invalidatesTags: ['Loans']
    })
  })
});

export const {
  useGetLoansQuery,
  useGetLoanQuery,
  useCreateLoanMutation,
  useUpdateLoanMutation,
  useDeleteLoanMutation
} = loansApiSlice;