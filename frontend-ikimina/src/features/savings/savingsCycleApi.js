import { apiSlice } from '../../app/api/apiSlice';

export const savingsCycleApi = apiSlice.injectEndpoints({
  endpoints: builder => ({
    // Start a new savings cycle
    startNewCycle: builder.mutation({
      query: groupId => ({
        url: `/savings-cycles/groups/${groupId}/start`,
        method: 'POST',
      }),
      invalidatesTags: ['SavingsCycles'],
    }),

    // Calculate payouts for a cycle
    calculatePayouts: builder.mutation({
      query: cycleId => ({
        url: `/savings-cycles/${cycleId}/calculate-payouts`,
        method: 'POST',
      }),
      invalidatesTags: ['SavingsCycles', 'MemberPayouts'],
    }),

    // Get all cycles for a group
    getCyclesByGroup: builder.query({
      query: groupId => `/savings-cycles/groups/${groupId}`,
      providesTags: ['SavingsCycles'],
    }),

    // Get current active cycle
    getCurrentCycle: builder.query({
      query: groupId => `/savings-cycles/groups/${groupId}/current`,
      providesTags: ['SavingsCycles'],
    }),

    // Get member payouts for a cycle
    getPayouts: builder.query({
      query: cycleId => `/savings-cycles/${cycleId}/payouts`,
      providesTags: ['MemberPayouts'],
    }),

    // Mark payout as paid
    markPayoutAsPaid: builder.mutation({
      query: payoutId => ({
        url: `/savings-cycles/payouts/${payoutId}/mark-paid`,
        method: 'POST',
      }),
      invalidatesTags: ['MemberPayouts', 'SavingsCycles'],
    }),
  }),
});

export const {
  useStartNewCycleMutation,
  useCalculatePayoutsMutation,
  useGetCyclesByGroupQuery,
  useGetCurrentCycleQuery,
  useGetPayoutsQuery,
  useMarkPayoutAsPaidMutation,
} = savingsCycleApi;
