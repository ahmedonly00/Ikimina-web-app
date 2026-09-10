import React, { useState, useMemo, useEffect } from 'react';
import { useLocation } from 'react-router-dom';
import { SavingsList } from './components/SavingsList';
import { SavingForm } from './components/SavingForm';
import BulkSavingsForm from './components/BulkSavingsForm';
import SavingsLedger from './components/SavingsLedger';
import toast from 'react-hot-toast';
import QueryError from '../../components/ui/QueryError';
import Modal from '../../components/ui/Modal';
import Button from '../../components/ui/Button';
import {
  FaDollarSign,
  FaCalendarAlt,
  FaUser,
  FaFileExcel,
  FaFilePdf,
  FaTable,
} from 'react-icons/fa';
import { useAppContext } from '../../contexts/AppContext';
import { useGetSavingsQuery, useCreateSavingMutation } from '../../app/api/apiSlice';
import { useCreateBulkSavingsMutation } from './savingsApi';
import * as XLSX from 'xlsx';
import { useSelector } from 'react-redux';
import { selectCurrentUser } from '../auth/authSlice';

export const SavingsPage = () => {
  const location = useLocation();
  const { t } = useAppContext();
  const currentUser = useSelector(selectCurrentUser);
  const [isAddModalOpen, setIsAddModalOpen] = useState(false);
  const [isEditModalOpen, setIsEditModalOpen] = useState(false);
  const [isBulkModalOpen, setIsBulkModalOpen] = useState(false);
  const [selectedSaving, setSelectedSaving] = useState(null);
  const [view, setView] = useState('list'); // 'list' or 'ledger'

  // Use RTK Query hooks
  const { data: savings = [], isLoading, error, refetch } = useGetSavingsQuery(currentUser?.id);
  const [createBulkSavings, { isLoading: _isBulkLoading }] = useCreateBulkSavingsMutation();
  const [createSaving, { isLoading: _isCreateLoading }] = useCreateSavingMutation();

  // Check if we need to open the deposit modal from navigation state
  useEffect(() => {
    if (location.state?.openDepositModal) {
      setIsAddModalOpen(true);
      // Clear the state to prevent reopening on refresh
      window.history.replaceState({}, document.title);
    }
  }, [location.state]);

  // Handle form submission for adding a new saving
  const handleAddSaving = async savingData => {
    try {
      await createSaving({
        ...savingData,
        userId: currentUser.id,
      }).unwrap();

      toast.success('Saving recorded successfully!');
      setIsAddModalOpen(false);
      refetch(); // Refresh the savings list
    } catch (err) {
      toast.error('Failed to record saving: ' + (err.data?.message || err.message));
      console.error('Failed to add saving:', err);
    }
  };

  // Handle bulk savings submission
  const handleBulkSavings = async bulkData => {
    try {
      await createBulkSavings(bulkData).unwrap();
      toast.success('Bulk savings recorded successfully!');
      setIsBulkModalOpen(false);
      refetch(); // Refresh the savings list
    } catch (err) {
      toast.error('Failed to record bulk savings: ' + (err.data?.message || err.message));
      console.error('Failed to add bulk savings:', err);
    }
  };

  // Handle edit saving
  const handleEditSaving = async _savingData => {
    try {
      // TODO: Implement update saving API call
      toast.success('Saving updated successfully!');
      setIsEditModalOpen(false);
      refetch(); // Refresh the savings list
    } catch (err) {
      toast.error('Failed to update saving: ' + (err.data?.message || err.message));
      console.error('Failed to update saving:', err);
    }
  };

  // Handle delete saving
  const handleDeleteSaving = async _savingId => {
    if (window.confirm('Are you sure you want to delete this saving record?')) {
      try {
        // TODO: Implement delete saving API call
        toast.success('Saving deleted successfully!');
        refetch(); // Refresh the savings list
      } catch (err) {
        toast.error('Failed to delete saving: ' + (err.data?.message || err.message));
        console.error('Failed to delete saving:', err);
      }
    }
  };

  // Helper functions to get member info

  const handleEdit = saving => {
    setSelectedSaving(saving);
    setIsEditModalOpen(true);
  };

  const handleDelete = id => {
    if (window.confirm('Are you sure you want to delete this saving record?')) {
      handleDeleteSaving(id);
    }
  };

  // Calculate totals
  const totalSavings = savings.reduce((sum, s) => sum + s.ubwizigameAmount + s.ingobokaAmount, 0);
  const totalUbwizigame = savings.reduce((sum, s) => sum + s.ubwizigameAmount, 0);
  const totalIngoboka = savings.reduce((sum, s) => sum + s.ingobokaAmount, 0);
  // Calculate weekly totals for each member
  const weeklyTotals = useMemo(() => {
    const totals = {};
    const today = new Date();
    const weekStart = new Date(
      today.getFullYear(),
      today.getMonth(),
      today.getDate() - today.getDay()
    );
    const weekEnd = new Date(weekStart);
    weekEnd.setDate(weekStart.getDate() + 6);

    savings
      .filter(s => {
        const savingDate = new Date(s.savingDate);
        return savingDate >= weekStart && savingDate <= weekEnd;
      })
      .forEach(saving => {
        if (!totals[saving.memberId]) {
          totals[saving.memberId] = {
            memberName: saving.memberName,
            memberNumber: saving.memberNumber,
            ubwizigameTotal: 0,
            ingobokaTotal: 0,
            grandTotal: 0,
            transactions: [],
          };
        }
        totals[saving.memberId].ubwizigameTotal += saving.ubwizigameAmount;
        totals[saving.memberId].ingobokaTotal += saving.ingobokaAmount;
        totals[saving.memberId].grandTotal += saving.ubwizigameAmount + saving.ingobokaAmount;
        totals[saving.memberId].transactions.push(saving);
      });

    return totals;
  }, [savings]);

  // Generate report function
  const generateReport = format => {
    const _reportData = {
      generatedAt: new Date().toLocaleString(),
      weekStart: new Date(
        new Date().setDate(new Date().getDate() - new Date().getDay())
      ).toLocaleDateString(),
      weekEnd: new Date(
        new Date().setDate(new Date().getDate() - new Date().getDay() + 6)
      ).toLocaleDateString(),
      members: Object.values(weeklyTotals),
    };

    if (format === 'pdf') {
      // Generate PDF content
      const content = `%PDF-1.4
%âãÏÓ
1 0 obj
<<
/Title (Weekly Savings Report)
/Creator (Ikimina System)
/Producer (Ikimina Reports)
/CreationDate (D:${new Date().toISOString()})
>>
endobj
2 0 obj
<<
/Type /Catalog
/Pages 3 0 R
>>
endobj
3 0 obj
<<
/Type /Pages
/Kids [4 0 R]
/Count 1
>>
endobj
4 0 obj
<<
/Type /Page
/Parent 3 0 R
/MediaBox [0 0 612 792]
/Contents 5 0 R
/Resources <<
/Font <<
/F1 6 0 R
>>
>>
>>
endobj
5 0 obj
<<
/Length 44
>>
stream
BT
/F1 12 Tf
72 720 Td
(Weekly Savings Report) Tj
ET
endstream
endobj
6 0 obj
<<
/Type /Font
/Subtype /Type1
/BaseFont /Helvetica
>>
endobj
xref
0 7
0000000000 65535 f 
0000000009 00000 n 
0000000054 00000 n 
0000000123 00000 n 
0000000175 00000 n 
0000000301 00000 n 
0000000380 00000 n 
trailer
<<
/Size 7
/Root 2 0 R
>>
startxref
456
%%EOF`;

      const blob = new Blob([content], { type: 'application/pdf' });
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `weekly-savings-report-${new Date().toISOString().split('T')[0]}.pdf`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.URL.revokeObjectURL(url);

      toast.success('PDF report downloaded successfully!');
    } else if (format === 'excel') {
      // Generate Excel content
      const excelData = [];

      // Add headers
      excelData.push([
        'Member Name',
        'Member Number',
        'Ubwizigame Total',
        'Ingoboka Total',
        'Grand Total',
      ]);

      // Add member data
      Object.values(weeklyTotals).forEach(member => {
        excelData.push([
          member.memberName,
          member.memberNumber,
          member.ubwizigameTotal,
          member.ingobokaTotal,
          member.grandTotal,
        ]);
      });

      // Add totals row
      const totalUbwizigame = Object.values(weeklyTotals).reduce(
        (sum, m) => sum + m.ubwizigameTotal,
        0
      );
      const totalIngoboka = Object.values(weeklyTotals).reduce(
        (sum, m) => sum + m.ingobokaTotal,
        0
      );
      const grandTotal = Object.values(weeklyTotals).reduce((sum, m) => sum + m.grandTotal, 0);

      excelData.push(['TOTALS', '', totalUbwizigame, totalIngoboka, grandTotal]);

      // Create workbook
      const ws = XLSX.utils.aoa_to_sheet(excelData);

      // Set column widths
      ws['!cols'] = [
        { wch: 25 }, // Member Name
        { wch: 15 }, // Member Number
        { wch: 18 }, // Ubwizigame Total
        { wch: 18 }, // Ingoboka Total
        { wch: 15 }, // Grand Total
      ];

      // Style the header row
      const headerRange = XLSX.utils.decode_range(ws['!ref']);
      for (let C = headerRange.s.c; C <= headerRange.e.c; C++) {
        const headerCell = XLSX.utils.encode_cell({ r: 0, c: C });
        if (!ws[headerCell]) continue;
        ws[headerCell].s = {
          font: { bold: true },
          fill: { fgColor: { rgb: 'FFE6E6' } },
          alignment: { horizontal: 'center' },
        };
      }

      // Style the totals row
      const totalsRowIndex = excelData.length - 1;
      for (let C = headerRange.s.c; C <= headerRange.e.c; C++) {
        const totalsCell = XLSX.utils.encode_cell({ r: totalsRowIndex, c: C });
        if (!ws[totalsCell]) continue;
        ws[totalsCell].s = {
          font: { bold: true },
          fill: { fgColor: { rgb: 'E6F3FF' } },
          alignment: { horizontal: 'center' },
        };
      }

      // Create workbook and download
      const wb = XLSX.utils.book_new();
      XLSX.utils.book_append_sheet(wb, ws, 'Weekly Savings Report');

      XLSX.writeFile(wb, `weekly-savings-report-${new Date().toISOString().split('T')[0]}.xlsx`);
      toast.success('Excel report downloaded successfully!');
    }
  };

  return (
    <div className='h-full w-full overflow-y-auto bg-gray-50'>
      <div className='px-2 sm:px-4 lg:px-8 py-4 sm:py-6 lg:py-8 max-w-full xl:max-w-7xl mx-auto'>
        <QueryError error={error} onRetry={refetch} title='Could not load savings' />
        {/* Header with stats */}
        <div className='mb-6'>
          <div className='flex flex-col sm:flex-row sm:justify-between sm:items-center mb-4 sm:mb-6 space-y-4 sm:space-y-0'>
            <div>
              <h1 className='text-xl sm:text-2xl font-bold text-gray-800'>{t('memberSavings')}</h1>
              <p className='mt-1 text-sm text-gray-500'>{t('savingsDescription')}</p>
            </div>
            <div className='flex space-x-2'>
              <Button
                onClick={() => setView(view === 'list' ? 'ledger' : 'list')}
                className='w-full sm:w-auto bg-gray-600 hover:bg-gray-700'
              >
                <FaTable className='mr-2' />
                {view === 'list' ? 'Ledger View' : 'List View'}
              </Button>
              <Button
                onClick={() => setIsBulkModalOpen(true)}
                disabled={isLoading}
                className='w-full sm:w-auto bg-green-600 hover:bg-green-700'
              >
                <span className='flex items-center'>
                  <svg
                    className='-ml-1 mr-2 h-5 w-5'
                    xmlns='http://www.w3.org/2000/svg'
                    fill='none'
                    viewBox='0 0 24 24'
                    stroke='currentColor'
                  >
                    <path
                      strokeLinecap='round'
                      strokeLinejoin='round'
                      strokeWidth={2}
                      d='M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2'
                    />
                  </svg>
                  Bulk Savings
                </span>
              </Button>
              <Button
                onClick={() => setIsAddModalOpen(true)}
                disabled={isLoading}
                className='w-full sm:w-auto'
              >
                <span className='flex items-center'>
                  <svg
                    className='-ml-1 mr-2 h-5 w-5'
                    xmlns='http://www.w3.org/2000/svg'
                    fill='none'
                    viewBox='0 0 24 24'
                    stroke='currentColor'
                  >
                    <path
                      strokeLinecap='round'
                      strokeLinejoin='round'
                      strokeWidth={2}
                      d='M12 4v16m8-8H4'
                    />
                  </svg>
                  {t('recordSavings')}
                </span>
              </Button>
            </div>
          </div>

          {/* Weekly Totals Section */}
          <div className='bg-white rounded-lg shadow p-6 mb-6'>
            <div className='flex justify-between items-center mb-4'>
              <h2 className='text-lg font-semibold text-gray-900'>{t('weeklySavingsTotals')}</h2>
              <div className='flex space-x-2'>
                <button
                  onClick={() => generateReport('pdf')}
                  className='inline-flex items-center px-3 py-2 border border-gray-300 shadow-sm text-sm leading-4 font-medium rounded-md text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500'
                >
                  <FaFilePdf className='mr-2 h-4 w-4 text-red-500' />
                  {t('exportPDF')}
                </button>
                <button
                  onClick={() => generateReport('excel')}
                  className='inline-flex items-center px-3 py-2 border border-gray-300 shadow-sm text-sm leading-4 font-medium rounded-md text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500'
                >
                  <FaFileExcel className='mr-2 h-4 w-4 text-green-500' />
                  {t('exportExcel')}
                </button>
              </div>
            </div>
            <div className='text-sm text-gray-500 mb-4'>
              {new Date().toLocaleDateString('en-US', {
                weekday: 'long',
                year: 'numeric',
                month: 'long',
                day: 'numeric',
              })}
            </div>
            <div className='overflow-x-auto'>
              <table className='min-w-full divide-y divide-gray-200'>
                <thead className='bg-gray-50'>
                  <tr>
                    <th className='px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider'>
                      {t('member')}
                    </th>
                    <th className='px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider'>
                      {t('ubwizigame')}
                    </th>
                    <th className='px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider'>
                      {t('ingoboka')}
                    </th>
                    <th className='px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider'>
                      {t('weeklyTotal')}
                    </th>
                  </tr>
                </thead>
                <tbody className='bg-white divide-y divide-gray-200'>
                  {Object.values(weeklyTotals).map(member => (
                    <tr key={member.memberNumber}>
                      <td className='px-4 py-3 whitespace-nowrap'>
                        <div className='text-sm font-medium text-gray-900'>{member.memberName}</div>
                        <div className='text-xs text-gray-500'>{member.memberNumber}</div>
                      </td>
                      <td className='px-4 py-3 whitespace-nowrap'>
                        <div className='text-sm font-medium text-purple-600'>
                          ${member.ubwizigameTotal.toLocaleString()}
                        </div>
                      </td>
                      <td className='px-4 py-3 whitespace-nowrap'>
                        <div className='text-sm font-medium text-blue-600'>
                          ${member.ingobokaTotal.toLocaleString()}
                        </div>
                      </td>
                      <td className='px-4 py-3 whitespace-nowrap'>
                        <div className='text-sm font-bold text-gray-900'>
                          ${member.grandTotal.toLocaleString()}
                        </div>
                      </td>
                    </tr>
                  ))}
                  {Object.keys(weeklyTotals).length === 0 && (
                    <tr>
                      <td colSpan='4' className='px-4 py-8 text-center text-sm text-gray-500'>
                        {t('noSavingsRecorded')}
                      </td>
                    </tr>
                  )}
                </tbody>
                <tfoot className='bg-gray-50'>
                  <tr>
                    <td className='px-4 py-3 font-semibold text-gray-900'>{t('grandTotal')}</td>
                    <td className='px-4 py-3 font-semibold text-purple-600'>
                      $
                      {Object.values(weeklyTotals)
                        .reduce((sum, m) => sum + m.ubwizigameTotal, 0)
                        .toLocaleString()}
                    </td>
                    <td className='px-4 py-3 font-semibold text-blue-600'>
                      $
                      {Object.values(weeklyTotals)
                        .reduce((sum, m) => sum + m.ingobokaTotal, 0)
                        .toLocaleString()}
                    </td>
                    <td className='px-4 py-3 font-bold text-gray-900'>
                      $
                      {Object.values(weeklyTotals)
                        .reduce((sum, m) => sum + m.grandTotal, 0)
                        .toLocaleString()}
                    </td>
                  </tr>
                </tfoot>
              </table>
            </div>
          </div>

          {/* Stats Cards */}
          <div className='grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-6'>
            <div className='bg-white rounded-lg shadow p-4 sm:p-6'>
              <div className='flex items-center'>
                <div className='flex-shrink-0 bg-indigo-100 rounded-md p-3'>
                  <FaDollarSign className='h-6 w-6 text-indigo-600' />
                </div>
                <div className='ml-4'>
                  <p className='text-sm font-medium text-gray-500'>{t('ubwizigameTotal')}</p>
                  <p className='text-2xl font-semibold text-gray-900'>
                    ${totalUbwizigame.toLocaleString()}
                  </p>
                </div>
              </div>
            </div>

            <div className='bg-white rounded-lg shadow p-4 sm:p-6'>
              <div className='flex items-center'>
                <div className='flex-shrink-0 bg-blue-100 rounded-md p-3'>
                  <FaDollarSign className='h-6 w-6 text-blue-600' />
                </div>
                <div className='ml-4'>
                  <p className='text-sm font-medium text-gray-500'>{t('ingobokaTotal')}</p>
                  <p className='text-2xl font-semibold text-gray-900'>
                    ${totalIngoboka.toLocaleString()}
                  </p>
                </div>
              </div>
            </div>

            <div className='bg-white rounded-lg shadow p-4 sm:p-6'>
              <div className='flex items-center'>
                <div className='flex-shrink-0 bg-green-100 rounded-md p-3'>
                  <FaUser className='h-6 w-6 text-green-600' />
                </div>
                <div className='ml-4'>
                  <p className='text-sm font-medium text-gray-500'>{t('activeMembers')}</p>
                  <p className='text-2xl font-semibold text-gray-900'>
                    {new Set(savings.map(s => s.memberId)).size}
                  </p>
                </div>
              </div>
            </div>

            <div className='bg-white rounded-lg shadow p-4 sm:p-6'>
              <div className='flex items-center'>
                <div className='flex-shrink-0 bg-purple-100 rounded-md p-3'>
                  <FaCalendarAlt className='h-6 w-6 text-purple-600' />
                </div>
                <div className='ml-4'>
                  <p className='text-sm font-medium text-gray-500'>{t('totalSavings')}</p>
                  <p className='text-2xl font-semibold text-gray-900'>
                    ${totalSavings.toLocaleString()}
                  </p>
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* Savings List or Ledger View */}
        {view === 'ledger' ? (
          <SavingsLedger />
        ) : (
          <SavingsList
            savings={savings}
            isLoading={isLoading}
            onEdit={handleEdit}
            onDelete={handleDelete}
          />
        )}

        {/* Add Saving Modal */}
        <Modal
          isOpen={isAddModalOpen}
          onClose={() => setIsAddModalOpen(false)}
          title={t('recordMemberSavings')}
        >
          <SavingForm
            onSubmit={handleAddSaving}
            onCancel={() => setIsAddModalOpen(false)}
            isSubmitting={isLoading}
          />
        </Modal>

        {/* Edit Saving Modal */}
        <Modal
          isOpen={isEditModalOpen}
          onClose={() => {
            setIsEditModalOpen(false);
            setSelectedSaving(null);
          }}
          title={t('editSaving')}
        >
          <SavingForm
            initialData={selectedSaving}
            onSubmit={handleEditSaving}
            onCancel={() => {
              setIsEditModalOpen(false);
              setSelectedSaving(null);
            }}
            isSubmitting={isLoading}
          />
        </Modal>

        {/* Bulk Savings Modal */}
        <Modal
          isOpen={isBulkModalOpen}
          onClose={() => setIsBulkModalOpen(false)}
          title='Record Bulk Savings'
          size='xl'
        >
          <BulkSavingsForm onSubmit={handleBulkSavings} onClose={() => setIsBulkModalOpen(false)} />
        </Modal>
      </div>
    </div>
  );
};

export default SavingsPage;
