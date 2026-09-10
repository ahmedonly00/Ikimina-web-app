import React, { useState, useEffect } from 'react';
import {
  useGetCyclesByGroupQuery,
  useGetCurrentCycleQuery,
  useStartNewCycleMutation,
  useCalculatePayoutsMutation,
} from '../savingsCycleApi';
import {
  FaCalendarAlt,
  FaPlus,
  FaCalculator,
  FaMoneyBillWave,
  FaUsers,
  FaClock,
} from 'react-icons/fa';
import Button from '../../../components/ui/Button';
import toast from 'react-hot-toast';
import { formatCurrency } from '../../../i18n';

const SavingsCycleManagement = ({ groupId, groupName, onCycleSelect }) => {
  const [selectedCycle, setSelectedCycle] = useState(null);
  const { data: cycles, isLoading: cyclesLoading } = useGetCyclesByGroupQuery(groupId);
  const { data: currentCycle, refetch: refetchCurrent } = useGetCurrentCycleQuery(groupId);
  const [startNewCycle, { isLoading: isStarting }] = useStartNewCycleMutation();
  const [calculatePayouts, { isLoading: isCalculating }] = useCalculatePayoutsMutation();

  useEffect(() => {
    if (cycles && cycles.length > 0 && !selectedCycle) {
      setSelectedCycle(cycles[0]);
    }
  }, [cycles, selectedCycle]);

  const handleStartNewCycle = async () => {
    try {
      await startNewCycle(groupId).unwrap();
      toast.success('New savings cycle started successfully!');
      refetchCurrent();
    } catch (err) {
      toast.error('Failed to start new cycle: ' + (err.data?.message || err.message));
    }
  };

  const handleCalculatePayouts = async cycleId => {
    try {
      await calculatePayouts(cycleId).unwrap();
      toast.success('Payouts calculated successfully!');
    } catch (err) {
      toast.error('Failed to calculate payouts: ' + (err.data?.message || err.message));
    }
  };

  const getStatusColor = status => {
    switch (status) {
      case 'ACTIVE':
        return 'text-green-600 bg-green-100';
      case 'COMPLETED':
        return 'text-yellow-600 bg-yellow-100';
      case 'DISTRIBUTED':
        return 'text-blue-600 bg-blue-100';
      default:
        return 'text-fg-muted bg-surface-2';
    }
  };

  const formatDate = dateString => {
    return new Date(dateString).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
    });
  };

  const getDaysRemaining = endDate => {
    const today = new Date();
    const end = new Date(endDate);
    const diffTime = end - today;
    const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));
    return diffDays > 0 ? diffDays : 0;
  };

  if (cyclesLoading) {
    return <div className='flex justify-center items-center h-64'>Loading cycles...</div>;
  }

  return (
    <div className='space-y-6'>
      {/* Header */}
      <div className='bg-surface p-6 rounded-lg shadow'>
        <div className='flex justify-between items-center'>
          <div>
            <h2 className='text-2xl font-bold text-fg'>{groupName} - Savings Cycles</h2>
            <p className='text-sm text-fg-muted mt-1'>
              Manage 6-month savings cycles and member payouts
            </p>
          </div>
          {!currentCycle && (
            <Button
              onClick={handleStartNewCycle}
              disabled={isStarting}
              className='bg-green-600 hover:bg-green-700'
            >
              <FaPlus className='mr-2' />
              Start New Cycle
            </Button>
          )}
        </div>
      </div>

      {/* Current Cycle Alert */}
      {currentCycle && (
        <div className='bg-blue-50 border-l-4 border-blue-400 p-4 rounded-lg'>
          <div className='flex'>
            <div className='flex-shrink-0'>
              <FaClock className='h-5 w-5 text-blue-400' />
            </div>
            <div className='ml-3'>
              <p className='text-sm text-blue-700'>
                <strong>Active Cycle:</strong> {formatDate(currentCycle.startDate)} -{' '}
                {formatDate(currentCycle.endDate)}
                <span className='ml-2'>
                  ({getDaysRemaining(currentCycle.endDate)} days remaining)
                </span>
              </p>
            </div>
          </div>
        </div>
      )}

      {/* Cycle Tabs */}
      <div className='bg-surface rounded-lg shadow'>
        <div className='border-b border-border'>
          <nav className='-mb-px flex space-x-8 px-6' aria-label='Tabs'>
            {cycles?.map(cycle => (
              <button
                key={cycle.id}
                onClick={() => {
                  setSelectedCycle(cycle);
                  if (onCycleSelect) {
                    onCycleSelect(cycle);
                  }
                }}
                className={`py-4 px-1 border-b-2 font-medium text-sm ${
                  selectedCycle?.id === cycle.id
                    ? 'border-blue-500 text-blue-600'
                    : 'border-transparent text-fg-muted hover:text-fg hover:border-border'
                }`}
              >
                <div className='flex items-center space-x-2'>
                  <FaCalendarAlt className='h-4 w-4' />
                  <span>{formatDate(cycle.startDate)}</span>
                  <span
                    className={`px-2 py-1 text-xs rounded-full ${getStatusColor(cycle.status)}`}
                  >
                    {cycle.status}
                  </span>
                </div>
              </button>
            ))}
          </nav>
        </div>

        {/* Cycle Details */}
        {selectedCycle && (
          <div className='p-6'>
            <div className='grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-6'>
              <div className='bg-bg rounded-lg p-4'>
                <div className='flex items-center'>
                  <div className='flex-shrink-0 bg-green-100 rounded-md p-3'>
                    <FaMoneyBillWave className='h-6 w-6 text-green-600' />
                  </div>
                  <div className='ml-4'>
                    <p className='text-sm font-medium text-fg-muted'>Total Ubwizigame</p>
                    <p className='text-2xl font-semibold text-fg'>
                      {formatCurrency(selectedCycle.totalUbwizigameCollected)}
                    </p>
                  </div>
                </div>
              </div>

              <div className='bg-bg rounded-lg p-4'>
                <div className='flex items-center'>
                  <div className='flex-shrink-0 bg-blue-100 rounded-md p-3'>
                    <FaMoneyBillWave className='h-6 w-6 text-blue-600' />
                  </div>
                  <div className='ml-4'>
                    <p className='text-sm font-medium text-fg-muted'>Total Ingoboka</p>
                    <p className='text-2xl font-semibold text-fg'>
                      {formatCurrency(selectedCycle.totalIngobokaCollected)}
                    </p>
                  </div>
                </div>
              </div>

              <div className='bg-bg rounded-lg p-4'>
                <div className='flex items-center'>
                  <div className='flex-shrink-0 bg-purple-100 rounded-md p-3'>
                    <FaMoneyBillWave className='h-6 w-6 text-purple-600' />
                  </div>
                  <div className='ml-4'>
                    <p className='text-sm font-medium text-fg-muted'>To Distribute</p>
                    <p className='text-2xl font-semibold text-fg'>
                      $
                      {(
                        selectedCycle.totalUbwizigameCollected -
                        selectedCycle.totalUbwizigameDistributed
                      ).toLocaleString()}
                    </p>
                  </div>
                </div>
              </div>

              <div className='bg-bg rounded-lg p-4'>
                <div className='flex items-center'>
                  <div className='flex-shrink-0 bg-yellow-100 rounded-md p-3'>
                    <FaUsers className='h-6 w-6 text-yellow-600' />
                  </div>
                  <div className='ml-4'>
                    <p className='text-sm font-medium text-fg-muted'>Members</p>
                    <p className='text-2xl font-semibold text-fg'>
                      {selectedCycle.memberPayouts?.length || 0}
                    </p>
                  </div>
                </div>
              </div>
            </div>

            {/* Action Buttons */}
            <div className='flex justify-end space-x-3'>
              {selectedCycle.status === 'COMPLETED' && (
                <Button
                  onClick={() => handleCalculatePayouts(selectedCycle.id)}
                  disabled={isCalculating}
                  className='bg-blue-600 hover:bg-blue-700'
                >
                  <FaCalculator className='mr-2' />
                  Calculate Payouts
                </Button>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default SavingsCycleManagement;
