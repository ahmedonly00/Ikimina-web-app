import React, { useState, useEffect } from 'react';
import { useGetSavingsLedgerQuery, useGetUsersQuery } from '../savingsApi';
import { FaCalendarAlt, FaDownload, FaFilter, FaFileExcel } from 'react-icons/fa';
import Button from '../../../components/ui/Button';
import toast from 'react-hot-toast';
import * as XLSX from 'xlsx';

const SavingsLedger = () => {
  const [startDate, setStartDate] = useState(
    new Date(new Date().getFullYear(), new Date().getMonth(), 1).toISOString().split('T')[0]
  );
  const [endDate, setEndDate] = useState(
    new Date(new Date().getFullYear(), new Date().getMonth() + 1, 0).toISOString().split('T')[0]
  );
  const [selectedUsers, setSelectedUsers] = useState([]);
  const { data: users, isLoading: usersLoading } = useGetUsersQuery();

  const {
    data: ledgerData,
    isLoading,
    error,
  } = useGetSavingsLedgerQuery(
    {
      userIds: selectedUsers.length > 0 ? selectedUsers : users?.map(u => u.id) || [],
      startDate,
      endDate,
    },
    {
      skip: !users || users.length === 0,
    }
  );

  useEffect(() => {
    if (users && users.length > 0) {
      setSelectedUsers(users.map(u => u.id));
    }
  }, [users]);

  const handleUserToggle = userId => {
    setSelectedUsers(prev =>
      prev.includes(userId) ? prev.filter(id => id !== userId) : [...prev, userId]
    );
  };

  const handleSelectAll = () => {
    if (selectedUsers.length === users?.length) {
      setSelectedUsers([]);
    } else {
      setSelectedUsers(users?.map(u => u.id) || []);
    }
  };

  const getWeeksInRange = () => {
    const weeks = [];
    const start = new Date(startDate);
    const end = new Date(endDate);

    // Find the first Monday (or use start date if it's Monday)
    const firstMonday = new Date(start);
    const dayOfWeek = firstMonday.getDay();
    const daysUntilMonday = dayOfWeek === 0 ? 6 : dayOfWeek - 1;
    firstMonday.setDate(firstMonday.getDate() + daysUntilMonday);

    // Generate weeks
    let currentWeek = new Date(firstMonday);
    let weekNumber = 1;

    while (currentWeek <= end) {
      const weekStart = new Date(currentWeek);
      const weekEnd = new Date(currentWeek);
      weekEnd.setDate(weekEnd.getDate() + 6);

      weeks.push({
        weekNumber,
        startDate: weekStart.toISOString().split('T')[0],
        endDate: weekEnd.toISOString().split('T')[0],
        label: `Week ${weekNumber}`,
      });

      currentWeek.setDate(currentWeek.getDate() + 7);
      weekNumber++;
    }

    return weeks;
  };

  const getWeekSavings = (member, weekStart, weekEnd) => {
    let ubwizigame = 0;
    let ingoboka = 0;

    Object.entries(member.dailySavings).forEach(([date, savings]) => {
      if (date >= weekStart && date <= weekEnd) {
        ubwizigame += savings.ubwizigame || 0;
        ingoboka += savings.ingoboka || 0;
      }
    });

    return { ubwizigame, ingoboka };
  };

  const exportToCSV = () => {
    if (!ledgerData || ledgerData.length === 0) {
      toast.error('No data to export');
      return;
    }

    const weeks = getWeeksInRange();
    const headers = [
      'No',
      'AMAZINA',
      ...weeks.flatMap(week => [`UBWIZIGAME ${week.label}`, `INGOBOKA ${week.label}`]),
      'MONTHLY TOTAL UBWIZIGAME',
      'MONTHLY TOTAL INGOBOKA',
      'MONTHLY GRAND TOTAL',
    ];

    const csvContent = [
      headers.join(','),
      ...ledgerData.map((member, index) => {
        const row = [index + 1, `"${member.memberName}"`];

        let monthlyUbwizigame = 0;
        let monthlyIngoboka = 0;

        weeks.forEach(week => {
          const weekSavings = getWeekSavings(member, week.startDate, week.endDate);
          const ubwizigame = weekSavings.ubwizigame || 0;
          const ingoboka = weekSavings.ingoboka || 0;

          row.push(ubwizigame);
          row.push(ingoboka);

          monthlyUbwizigame += ubwizigame;
          monthlyIngoboka += ingoboka;
        });

        row.push(monthlyUbwizigame);
        row.push(monthlyIngoboka);
        row.push(monthlyUbwizigame + monthlyIngoboka);

        return row.join(',');
      }),
    ].join('\n');

    const blob = new Blob([csvContent], { type: 'text/csv' });
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `savings-ledger-${startDate}-to-${endDate}.csv`;
    a.click();
    window.URL.revokeObjectURL(url);
  };

  const exportToExcel = () => {
    if (!ledgerData || ledgerData.length === 0) {
      toast.error('No data to export');
      return;
    }

    const weeks = getWeeksInRange();

    // Prepare data for Excel
    const excelData = [];

    // Add headers
    const headers = ['No', 'AMAZINA'];
    weeks.forEach(week => {
      headers.push(`UBWIZIGAME ${week.label}`);
      headers.push(`INGOBOKA ${week.label}`);
    });
    headers.push('MONTHLY TOTAL UBWIZIGAME', 'MONTHLY TOTAL INGOBOKA', 'MONTHLY GRAND TOTAL');
    excelData.push(headers);

    // Add data rows
    ledgerData.forEach((member, index) => {
      const row = [index + 1, member.memberName];

      let monthlyUbwizigame = 0;
      let monthlyIngoboka = 0;

      weeks.forEach(week => {
        const weekSavings = getWeekSavings(member, week.startDate, week.endDate);
        const ubwizigame = weekSavings.ubwizigame || 0;
        const ingoboka = weekSavings.ingoboka || 0;

        row.push(ubwizigame);
        row.push(ingoboka);

        monthlyUbwizigame += ubwizigame;
        monthlyIngoboka += ingoboka;
      });

      row.push(monthlyUbwizigame);
      row.push(monthlyIngoboka);
      row.push(monthlyUbwizigame + monthlyIngoboka);

      excelData.push(row);
    });

    // Add totals row
    const totalsRow = ['TOTALS', ''];
    let totalMonthlyUbwizigame = 0;
    let totalMonthlyIngoboka = 0;

    weeks.forEach(week => {
      let weekUbwizigameTotal = 0;
      let weekIngobokaTotal = 0;

      ledgerData.forEach(member => {
        const weekSavings = getWeekSavings(member, week.startDate, week.endDate);
        weekUbwizigameTotal += weekSavings.ubwizigame || 0;
        weekIngobokaTotal += weekSavings.ingoboka || 0;
      });

      totalsRow.push(weekUbwizigameTotal);
      totalsRow.push(weekIngobokaTotal);
    });

    // Calculate monthly totals
    ledgerData.forEach(member => {
      weeks.forEach(week => {
        const weekSavings = getWeekSavings(member, week.startDate, week.endDate);
        totalMonthlyUbwizigame += weekSavings.ubwizigame || 0;
        totalMonthlyIngoboka += weekSavings.ingoboka || 0;
      });
    });

    totalsRow.push(totalMonthlyUbwizigame);
    totalsRow.push(totalMonthlyIngoboka);
    totalsRow.push(totalMonthlyUbwizigame + totalMonthlyIngoboka);

    excelData.push(totalsRow);

    // Create workbook
    const ws = XLSX.utils.aoa_to_sheet(excelData);

    // Set column widths
    const colWidths = [
      { wch: 8 }, // No
      { wch: 30 }, // AMAZINA
    ];
    weeks.forEach(() => {
      colWidths.push({ wch: 18 }); // UBWIZIGAME
      colWidths.push({ wch: 18 }); // INGOBOKA
    });
    colWidths.push({ wch: 20 }, { wch: 20 }, { wch: 20 }); // Monthly totals

    ws['!cols'] = colWidths;

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
    XLSX.utils.book_append_sheet(wb, ws, 'Savings Ledger');

    XLSX.writeFile(wb, `savings-ledger-${startDate}-to-${endDate}.xlsx`);
    toast.success('Excel file downloaded successfully!');
  };

  const weeks = getWeeksInRange();

  if (usersLoading) {
    return <div className='flex justify-center items-center h-64'>Loading users...</div>;
  }

  return (
    <div className='space-y-6'>
      {/* Filters */}
      <div className='bg-surface p-6 rounded-lg shadow'>
        <h3 className='text-lg font-medium text-fg mb-4 flex items-center'>
          <FaFilter className='mr-2' />
          Filters
        </h3>
        <div className='grid grid-cols-1 md:grid-cols-4 gap-4'>
          <div>
            <label htmlFor='ledger-start-date' className='block text-sm font-medium text-fg mb-1'>
              <FaCalendarAlt className='inline mr-1' />
              Start Date
            </label>
            <input
              id='ledger-start-date'
              type='date'
              value={startDate}
              onChange={e => setStartDate(e.target.value)}
              className='w-full px-3 py-2 border border-border rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500'
            />
          </div>
          <div>
            <label htmlFor='ledger-end-date' className='block text-sm font-medium text-fg mb-1'>
              <FaCalendarAlt className='inline mr-1' />
              End Date
            </label>
            <input
              id='ledger-end-date'
              type='date'
              value={endDate}
              onChange={e => setEndDate(e.target.value)}
              className='w-full px-3 py-2 border border-border rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500'
            />
          </div>
          <div className='md:col-span-2'>
            {/* Labels a group of toggle buttons rather than one control, so it
                is exposed as a labelled group instead of a form label. */}
            <span id='ledger-members-label' className='block text-sm font-medium text-fg mb-1'>
              Members
            </span>
            <div
              role='group'
              aria-labelledby='ledger-members-label'
              className='flex flex-wrap gap-2 max-h-32 overflow-y-auto'
            >
              <button
                type='button'
                onClick={handleSelectAll}
                className={`px-3 py-1 rounded text-sm ${
                  selectedUsers.length === users?.length
                    ? 'bg-blue-600 text-white'
                    : 'bg-surface-3 text-fg'
                }`}
              >
                {selectedUsers.length === users?.length ? 'Deselect All' : 'Select All'}
              </button>
              {users?.map(user => (
                <button
                  key={user.id}
                  type='button'
                  onClick={() => handleUserToggle(user.id)}
                  className={`px-3 py-1 rounded text-sm ${
                    selectedUsers.includes(user.id)
                      ? 'bg-blue-600 text-white'
                      : 'bg-surface-3 text-fg'
                  }`}
                >
                  {user.firstName} {user.lastName}
                </button>
              ))}
            </div>
          </div>
        </div>
      </div>

      {/* Export Buttons */}
      <div className='flex justify-end space-x-2'>
        <Button
          onClick={exportToCSV}
          className='bg-green-600 hover:bg-green-700'
          disabled={isLoading || !ledgerData || ledgerData.length === 0}
        >
          <FaDownload className='mr-2' />
          Export to CSV
        </Button>
        <Button
          onClick={exportToExcel}
          className='bg-green-600 hover:bg-green-700'
          disabled={isLoading || !ledgerData || ledgerData.length === 0}
        >
          <FaFileExcel className='mr-2' />
          Export to Excel
        </Button>
      </div>

      {/* Ledger Table */}
      <div className='bg-surface shadow-lg rounded-lg overflow-hidden'>
        <div className='overflow-x-auto'>
          <table className='min-w-full divide-y divide-border'>
            <thead className='bg-bg'>
              <tr>
                <th className='px-4 py-3 text-left text-xs font-medium text-fg-muted uppercase tracking-wider w-16'>
                  No
                </th>
                <th className='px-4 py-3 text-left text-xs font-medium text-fg-muted uppercase tracking-wider min-w-48'>
                  AMAZINA
                </th>
                {weeks.map(week => (
                  <React.Fragment key={week.weekNumber}>
                    <th className='px-4 py-3 text-center text-xs font-medium text-fg-muted uppercase tracking-wider'>
                      UBWIZIGAME
                      <div className='text-xs font-normal'>{week.label}</div>
                      <div className='text-xs text-fg-subtle'>
                        {week.startDate} - {week.endDate}
                      </div>
                    </th>
                    <th className='px-4 py-3 text-center text-xs font-medium text-fg-muted uppercase tracking-wider'>
                      INGOBOKA
                      <div className='text-xs font-normal'>{week.label}</div>
                      <div className='text-xs text-fg-subtle'>
                        {week.startDate} - {week.endDate}
                      </div>
                    </th>
                  </React.Fragment>
                ))}
                <th className='px-4 py-3 text-center text-xs font-medium text-fg-muted uppercase tracking-wider bg-info-subtle'>
                  MONTHLY
                  <br />
                  TOTAL UBWIZIGAME
                </th>
                <th className='px-4 py-3 text-center text-xs font-medium text-fg-muted uppercase tracking-wider bg-info-subtle'>
                  MONTHLY
                  <br />
                  TOTAL INGOBOKA
                </th>
                <th className='px-4 py-3 text-center text-xs font-medium text-fg-muted uppercase tracking-wider bg-info-subtle'>
                  MONTHLY
                  <br />
                  GRAND TOTAL
                </th>
              </tr>
            </thead>
            <tbody className='bg-surface divide-y divide-border'>
              {isLoading ? (
                <tr>
                  <td colSpan={3 + weeks.length * 2 + 3} className='px-4 py-8 text-center'>
                    Loading ledger data...
                  </td>
                </tr>
              ) : error ? (
                <tr>
                  <td
                    colSpan={3 + weeks.length * 2 + 3}
                    className='px-4 py-8 text-center text-danger'
                  >
                    Error loading ledger data
                  </td>
                </tr>
              ) : ledgerData && ledgerData.length > 0 ? (
                ledgerData.map((member, index) => {
                  let monthlyUbwizigame = 0;
                  let monthlyIngoboka = 0;

                  return (
                    <tr key={member.memberId} className='hover:bg-bg'>
                      <td className='px-4 py-4 text-sm text-fg'>{index + 1}</td>
                      <td className='px-4 py-4 text-sm font-medium text-fg'>{member.memberName}</td>
                      {weeks.map(week => {
                        const weekSavings = getWeekSavings(member, week.startDate, week.endDate);
                        monthlyUbwizigame += weekSavings.ubwizigame || 0;
                        monthlyIngoboka += weekSavings.ingoboka || 0;

                        return (
                          <React.Fragment key={week.weekNumber}>
                            <td className='px-4 py-4 text-sm text-fg text-center'>
                              {weekSavings.ubwizigame || '-'}
                            </td>
                            <td className='px-4 py-4 text-sm text-fg text-center'>
                              {weekSavings.ingoboka || '-'}
                            </td>
                          </React.Fragment>
                        );
                      })}
                      <td className='px-4 py-4 text-sm font-bold text-fg text-center bg-info-subtle'>
                        {monthlyUbwizigame || 0}
                      </td>
                      <td className='px-4 py-4 text-sm font-bold text-fg text-center bg-info-subtle'>
                        {monthlyIngoboka || 0}
                      </td>
                      <td className='px-4 py-4 text-sm font-bold text-fg text-center bg-info-subtle'>
                        {monthlyUbwizigame + monthlyIngoboka || 0}
                      </td>
                    </tr>
                  );
                })
              ) : (
                <tr>
                  <td
                    colSpan={3 + weeks.length * 2 + 3}
                    className='px-4 py-8 text-center text-fg-muted'
                  >
                    No savings data found for the selected period
                  </td>
                </tr>
              )}

              {/* Totals Row */}
              {ledgerData && ledgerData.length > 0 && (
                <tr className='bg-surface-2 font-bold'>
                  <td className='px-4 py-4 text-sm text-fg' colSpan='2'>
                    TOTALS
                  </td>
                  {weeks.map(week => {
                    let weekUbwizigameTotal = 0;
                    let weekIngobokaTotal = 0;

                    ledgerData.forEach(member => {
                      const weekSavings = getWeekSavings(member, week.startDate, week.endDate);
                      weekUbwizigameTotal += weekSavings.ubwizigame || 0;
                      weekIngobokaTotal += weekSavings.ingoboka || 0;
                    });

                    return (
                      <React.Fragment key={`total-${week.weekNumber}`}>
                        <td className='px-4 py-4 text-sm text-fg text-center'>
                          {weekUbwizigameTotal}
                        </td>
                        <td className='px-4 py-4 text-sm text-fg text-center'>
                          {weekIngobokaTotal}
                        </td>
                      </React.Fragment>
                    );
                  })}

                  {/* Monthly Totals */}
                  {(() => {
                    let totalMonthlyUbwizigame = 0;
                    let totalMonthlyIngoboka = 0;

                    ledgerData.forEach(member => {
                      weeks.forEach(week => {
                        const weekSavings = getWeekSavings(member, week.startDate, week.endDate);
                        totalMonthlyUbwizigame += weekSavings.ubwizigame || 0;
                        totalMonthlyIngoboka += weekSavings.ingoboka || 0;
                      });
                    });

                    return (
                      <>
                        <td className='px-4 py-4 text-sm text-fg text-center bg-info-subtle'>
                          {totalMonthlyUbwizigame}
                        </td>
                        <td className='px-4 py-4 text-sm text-fg text-center bg-info-subtle'>
                          {totalMonthlyIngoboka}
                        </td>
                        <td className='px-4 py-4 text-sm text-fg text-center bg-info-subtle'>
                          {totalMonthlyUbwizigame + totalMonthlyIngoboka}
                        </td>
                      </>
                    );
                  })()}
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};

export default SavingsLedger;
