import React, { useEffect } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { useGetSavingsQuery, useCreateSavingMutation, useUpdateSavingMutation, useDeleteSavingMutation } from './savingsApi';
import { SavingsList } from './components/SavingsList';
import { SavingForm } from './components/SavingForm';
import {
  openAddModal,
  closeAddModal,
  openEditModal,
  closeEditModal,
  openDeleteModal,
  closeDeleteModal,
  setSelectedSaving,
  selectSelectedSaving,
  selectIsAddModalOpen,
  selectIsEditModalOpen,
  selectIsDeleteModalOpen,
} from './savingsSlice';
import { toast } from 'react-hot-toast';
import { Modal } from '../../components/ui/Modal';
import { Button } from '../../components/ui/Button';

export const SavingsPage = () => {
  const dispatch = useDispatch();
  const selectedSaving = useSelector(selectSelectedSaving);
  const isAddModalOpen = useSelector(selectIsAddModalOpen);
  const isEditModalOpen = useSelector(selectIsEditModalOpen);
  const isDeleteModalOpen = useSelector(selectIsDeleteModalOpen);

  // RTK Query hooks
  const { data: savings = [], isLoading, isError, error, refetch } = useGetSavingsQuery();
  const [createSaving, { isLoading: isCreating }] = useCreateSavingMutation();
  const [updateSaving, { isLoading: isUpdating }] = useUpdateSavingMutation();
  const [deleteSaving, { isLoading: isDeleting }] = useDeleteSavingMutation();

  // Handle form submission for adding a new saving
  const handleAddSaving = async (savingData) => {
    try {
      await createSaving(savingData).unwrap();
      toast.success('Saving added successfully');
      dispatch(closeAddModal());
      refetch();
    } catch (err) {
      toast.error('Failed to add saving');
      console.error('Failed to add saving:', err);
    }
  };

  // Handle form submission for updating a saving
  const handleUpdateSaving = async (savingData) => {
    if (!selectedSaving) return;
    
    try {
      await updateSaving({ id: selectedSaving.id, ...savingData }).unwrap();
      toast.success('Saving updated successfully');
      dispatch(closeEditModal());
      refetch();
    } catch (err) {
      toast.error('Failed to update saving');
      console.error('Failed to update saving:', err);
    }
  };

  // Handle delete confirmation
  const handleConfirmDelete = async () => {
    if (!selectedSaving) return;
    
    try {
      await deleteSaving(selectedSaving.id).unwrap();
      toast.success('Saving deleted successfully');
      dispatch(closeDeleteModal());
      refetch();
    } catch (err) {
      toast.error('Failed to delete saving');
      console.error('Failed to delete saving:', err);
    }
  };

  // Handle edit button click
  const handleEdit = (saving) => {
    dispatch(setSelectedSaving(saving));
    dispatch(openEditModal());
  };

  // Handle delete button click
  const handleDelete = (saving) => {
    dispatch(setSelectedSaving(saving));
    dispatch(openDeleteModal());
  };

  // Handle add button click
  const handleAddClick = () => {
    dispatch(openAddModal());
  };

  // Display error message if there's an error
  if (isError) {
    return (
      <div className="p-4 text-red-600">
        Error loading savings: {error?.data?.message || 'Unknown error'}
      </div>
    );
  }

  return (
    <div className="p-4">
      <div className="flex justify-between items-center mb-6">
        <h1 className="text-2xl font-bold">Savings</h1>
        <Button onClick={handleAddClick} disabled={isCreating || isUpdating || isDeleting}>
          Add Saving
        </Button>
      </div>

      {/* Savings List */}
      <SavingsList 
        savings={savings} 
        isLoading={isLoading} 
        onEdit={handleEdit}
        onDelete={handleDelete}
      />

      {/* Add Saving Modal */}
      <Modal
        isOpen={isAddModalOpen}
        onClose={() => dispatch(closeAddModal())}
        title="Add New Saving"
      >
        <SavingForm
          onSubmit={handleAddSaving}
          onCancel={() => dispatch(closeAddModal())}
          isLoading={isCreating}
        />
      </Modal>

      {/* Edit Saving Modal */}
      <Modal
        isOpen={isEditModalOpen}
        onClose={() => dispatch(closeEditModal())}
        title="Edit Saving"
      >
        <SavingForm
          initialValues={selectedSaving}
          onSubmit={handleUpdateSaving}
          onCancel={() => dispatch(closeEditModal())}
          isLoading={isUpdating}
        />
      </Modal>

      {/* Delete Confirmation Modal */}
      <Modal
        isOpen={isDeleteModalOpen}
        onClose={() => dispatch(closeDeleteModal())}
        title="Delete Saving"
        size="sm"
      >
        <div className="p-4">
          <p className="mb-4">Are you sure you want to delete this saving?</p>
          <div className="flex justify-end space-x-2">
            <Button
              variant="secondary"
              onClick={() => dispatch(closeDeleteModal())}
              disabled={isDeleting}
            >
              Cancel
            </Button>
            <Button
              variant="danger"
              onClick={handleConfirmDelete}
              isLoading={isDeleting}
            >
              Delete
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
};

export default SavingsPage;
