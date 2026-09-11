import React, { useState } from 'react';
import { useGetPayoutsQuery, useMarkPayoutAsPaidMutation } from '../savingsCycleApi';
import { FaMoneyBillWave, FaUser, FaCheck, FaFileExcel } from 'react-icons/fa';
import Button from '../../../components/ui/Button';
import toast from 'react-hot-toast';
import * as XLSX from 'xlsx';
import { formatCurrency } from '../../../i18n';

const MemberPayouts = ({ cycleId, cycleStatus: _cycleStatus }) => {
  const [selectedMembers, setSelectedMembers] = useState([]);
  const { data: payouts, isLoading } = useGetPayoutsQuery(cycleId, {
    skip: !cycleId,
  });
  const [markAsPaid, { isLoading: isMarkingPaid }] = useMarkPayoutAsPaidMutation();

  const handleSelectAll = () => {
    if (selectedMembers.length === payouts?.length) {
      setSelectedMembers([]);
    } else {
      setSelectedMembers(payouts?.map(p => p.id) || []);
    }
  };

  const handleSelectMember = payoutId => {
    setSelectedMembers(prev =>
      prev.includes(payoutId) ? prev.filter(id => id !== payoutId) : [...prev, payoutId]
    );
  };

  const handleMarkAsPaid = async payoutId => {
    try {
      await markAsPaid(payoutId).unwrap();
      toast.success('Payout marked as paid!');
    } catch (err) {
      toast.error('Failed to mark payout as paid: ' + (err.data?.message || err.message));
    }
  };

  const handleBulkMarkAsPaid = async () => {
    try {
      await Promise.all(selectedMembers.map(id => markAsPaid(id).unwrap()));
      toast.success(`${selectedMembers.length} payouts marked as paid!`);
      setSelectedMembers([]);
    } catch (err) {
      toast.error('Failed to mark some payouts as paid');
    }
  };

  const exportToExcel = () => {
    if (!payouts || payouts.length === 0) {
      toast.error('No data to export');
      return;
    }

    // Prepare data for Excel
    const excelData = [];

    // Add headers
    excelData.push([
      'Member Number',
      'Member Name',
      'Total Ubwizigame',
      'Total Ingoboka',
      'Payout Amount',
      'Status',
      'Paid Date',
    ]);

    // Add data rows
    payouts.forEach(payout => {
      excelData.push([
        payout.memberNumber,
        payout.memberName,
        payout.totalUbwizigame || 0,
        payout.totalIngoboka || 0,
        payout.payoutAmount || 0,
        payout.status,
        payout.paidAt || 'Not paid',
      ]);
    });

    // Add totals row
    const totalUbwizigame = payouts.reduce((sum, p) => sum + (p.totalUbwizigame || 0), 0);
    const totalIngoboka = payouts.reduce((sum, p) => sum + (p.totalIngoboka || 0), 0);
    const totalPayouts = payouts.reduce((sum, p) => sum + (p.payoutAmount || 0), 0);

    excelData.push(['TOTALS', '', totalUbwizigame, totalIngoboka, totalPayouts, '', '']);

    // Create workbook
    const ws = XLSX.utils.aoa_to_sheet(excelData);

    // Set column widths
    ws['!cols'] = [
      { wch: 15 }, // Member Number
      { wch: 30 }, // Member Name
      { wch: 18 }, // Total Ubwizigame
      { wch: 18 }, // Total Ingoboka
      { wch: 18 }, // Payout Amount
      { wch: 12 }, // Status
      { wch: 15 }, // Paid Date
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
    XLSX.utils.book_append_sheet(wb, ws, 'Member Payouts');

    XLSX.writeFile(wb, `member-payouts-cycle-${cycleId}.xlsx`);
    toast.success('Excel file downloaded successfully!');
  };

  const getStatusColor = status => {
    switch (status) {
      case 'PENDING':
        return 'text-warning bg-warning-subtle';
      case 'PAID':
        return 'text-success bg-success-subtle';
      case 'FAILED':
        return 'text-danger bg-danger-subtle';
      default:
        return 'text-fg-muted bg-surface-2';
    }
  };

  if (isLoading) {
    return <div className='flex justify-center items-center h-64'>Loading payouts...</div>;
  }

  if (!payouts || payouts.length === 0) {
    return (
      <div className='bg-surface p-6 rounded-lg shadow text-center'>
        <p className='text-fg-muted'>No payouts available for this cycle</p>
      </div>
    );
  }

  return (
    <div className='space-y-6'>
      {/* Header with Actions */}
      <div className='bg-surface p-6 rounded-lg shadow'>
        <div className='flex justify-between items-center mb-4'>
          <h3 className='text-lg font-medium text-fg'>Member Payouts</h3>
          <div className='flex space-x-2'>
            {selectedMembers.length > 0 && (
              <Button
                onClick={handleBulkMarkAsPaid}
                disabled={isMarkingPaid}
                className='bg-green-600 hover:bg-green-700'
              >
                <FaCheck className='mr-2' />
                Mark {selectedMembers.length} as Paid
              </Button>
            )}
            <Button onClick={exportToExcel} className='bg-blue-600 hover:bg-blue-700'>
              <FaFileExcel className='mr-2' />
              Export to Excel
            </Button>
          </div>
        </div>

        {/* Summary Cards */}
        <div className='grid grid-cols-1 md:grid-cols-3 gap-4 mb-6'>
          <div className='bg-success-subtle rounded-lg p-4'>
            <div className='flex items-center'>
              <FaMoneyBillWave className='h-8 w-8 text-success mr-3' />
              <div>
                <p className='text-sm font-medium text-success'>Total to Distribute</p>
                <p className='text-2xl font-bold text-success'>
                  {formatCurrency(payouts.reduce((sum, p) => sum + (p.payoutAmount || 0), 0))}
                </p>
              </div>
            </div>
          </div>

          <div className='bg-info-subtle rounded-lg p-4'>
            <div className='flex items-center'>
              <FaMoneyBillWave className='h-8 w-8 text-info mr-3' />
              <div>
                <p className='text-sm font-medium text-info'>Group Retains (Ingoboka)</p>
                <p className='text-2xl font-bold text-info'>
                  {formatCurrency(payouts.reduce((sum, p) => sum + (p.totalIngoboka || 0), 0))}
                </p>
              </div>
            </div>
          </div>

          <div className='bg-warning-subtle rounded-lg p-4'>
            <div className='flex items-center'>
              <FaUser className='h-8 w-8 text-warning mr-3' />
              <div>
                <p className='text-sm font-medium text-warning'>Pending Payouts</p>
                <p className='text-2xl font-bold text-warning'>
                  {payouts.filter(p => p.status === 'PENDING').length}
                </p>
              </div>
            </div>
          </div>
        </div>

        {/* Payouts Table */}
        <div className='overflow-x-auto'>
          <table className='min-w-full divide-y divide-border'>
            <thead className='bg-bg'>
              <tr>
                <th className='px-4 py-3 text-left'>
                  <input
                    type='checkbox'
                    checked={selectedMembers.length === payouts.length}
                    onChange={handleSelectAll}
                    className='rounded border-border text-info focus:ring-blue-500'
                  />
                </th>
                <th className='px-4 py-3 text-left text-xs font-medium text-fg-muted uppercase tracking-wider'>
                  Member
                </th>
                <th className='px-4 py-3 text-left text-xs font-medium text-fg-muted uppercase tracking-wider'>
                  Total Ubwizigame
                </th>
                <th className='px-4 py-3 text-left text-xs font-medium text-fg-muted uppercase tracking-wider'>
                  Total Ingoboka
                </th>
                <th className='px-4 py-3 text-left text-xs font-medium text-fg-muted uppercase tracking-wider'>
                  Payout Amount
                </th>
                <th className='px-4 py-3 text-left text-xs font-medium text-fg-muted uppercase tracking-wider'>
                  Status
                </th>
                <th className='px-4 py-3 text-left text-xs font-medium text-fg-muted uppercase tracking-wider'>
                  Actions
                </th>
              </tr>
            </thead>
            <tbody className='bg-surface divide-y divide-border'>
              {payouts.map(payout => (
                <tr key={payout.id} className='hover:bg-bg'>
                  <td className='px-4 py-4'>
                    <input
                      type='checkbox'
                      checked={selectedMembers.includes(payout.id)}
                      onChange={() => handleSelectMember(payout.id)}
                      disabled={payout.status === 'PAID'}
                      className='rounded border-border text-info focus:ring-blue-500'
                    />
                  </td>
                  <td className='px-4 py-4'>
                    <div className='text-sm font-medium text-fg'>{payout.memberName}</div>
                    <div className='text-xs text-fg-muted'>{payout.memberNumber}</div>
                  </td>
                  <td className='px-4 py-4 text-sm text-fg'>
                    {formatCurrency(payout.totalUbwizigame)}
                  </td>
                  <td className='px-4 py-4 text-sm text-fg'>
                    {formatCurrency(payout.totalIngoboka)}
                  </td>
                  <td className='px-4 py-4 text-sm font-semibold text-success'>
                    {formatCurrency(payout.payoutAmount)}
                  </td>
                  <td className='px-4 py-4'>
                    <span
                      className={`px-2 py-1 text-xs rounded-full ${getStatusColor(payout.status)}`}
                    >
                      {payout.status}
                    </span>
                  </td>
                  <td className='px-4 py-4 text-sm'>
                    {payout.status === 'PENDING' && (
                      <Button
                        onClick={() => handleMarkAsPaid(payout.id)}
                        disabled={isMarkingPaid}
                        size='sm'
                        className='bg-green-600 hover:bg-green-700'
                      >
                        <FaCheck className='mr-1' />
                        Mark Paid
                      </Button>
                    )}
                    {payout.status === 'PAID' && (
                      <span className='text-xs text-fg-muted'>
                        Paid on {new Date(payout.paidAt).toLocaleDateString()}
                      </span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};

export default MemberPayouts;
