import { createSlice, createSelector } from '@reduxjs/toolkit';

const initialState = {
  selectedSaving: null,
  isAddModalOpen: false,
  isEditModalOpen: false,
  isDeleteModalOpen: false,
  currentPage: 1,
  itemsPerPage: 10,
  searchTerm: '',
  sortBy: 'date',
  sortOrder: 'desc',
};

const savingsSlice = createSlice({
  name: 'savings',
  initialState,
  reducers: {
    setSelectedSaving: (state, action) => {
      state.selectedSaving = action.payload;
    },
    openAddModal: state => {
      state.isAddModalOpen = true;
    },
    closeAddModal: state => {
      state.isAddModalOpen = false;
    },
    openEditModal: (state, action) => {
      state.selectedSaving = action.payload;
      state.isEditModalOpen = true;
    },
    closeEditModal: state => {
      state.selectedSaving = null;
      state.isEditModalOpen = false;
    },
    openDeleteModal: (state, action) => {
      state.selectedSaving = action.payload;
      state.isDeleteModalOpen = true;
    },
    closeDeleteModal: state => {
      state.selectedSaving = null;
      state.isDeleteModalOpen = false;
    },
    setCurrentPage: (state, action) => {
      state.currentPage = action.payload;
    },
    setItemsPerPage: (state, action) => {
      state.itemsPerPage = action.payload;
      state.currentPage = 1; // Reset to first page when changing items per page
    },
    setSearchTerm: (state, action) => {
      state.searchTerm = action.payload;
      state.currentPage = 1; // Reset to first page when searching
    },
    setSort: (state, action) => {
      const { field, order } = action.payload;
      state.sortBy = field;
      state.sortOrder = order;
    },
  },
});

// Selectors
const selectSavingsState = state => state.savings;

export const selectSelectedSaving = createSelector(
  [selectSavingsState],
  savings => savings.selectedSaving
);

export const selectIsAddModalOpen = createSelector(
  [selectSavingsState],
  savings => savings.isAddModalOpen
);

export const selectIsEditModalOpen = createSelector(
  [selectSavingsState],
  savings => savings.isEditModalOpen
);

export const selectIsDeleteModalOpen = createSelector(
  [selectSavingsState],
  savings => savings.isDeleteModalOpen
);

export const selectCurrentPage = createSelector(
  [selectSavingsState],
  savings => savings.currentPage
);

export const selectItemsPerPage = createSelector(
  [selectSavingsState],
  savings => savings.itemsPerPage
);

export const selectSearchTerm = createSelector([selectSavingsState], savings => savings.searchTerm);

export const selectSort = createSelector([selectSavingsState], savings => ({
  sortBy: savings.sortBy,
  sortOrder: savings.sortOrder,
}));

// Export actions
export const {
  setSelectedSaving,
  openAddModal,
  closeAddModal,
  openEditModal,
  closeEditModal,
  openDeleteModal,
  closeDeleteModal,
  setCurrentPage,
  setItemsPerPage,
  setSearchTerm,
  setSort,
} = savingsSlice.actions;

export default savingsSlice.reducer;
