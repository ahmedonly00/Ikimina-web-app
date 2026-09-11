import React, { useState } from 'react';
import SavingsCycleManagement from './SavingsCycleManagement';
import MemberPayouts from './MemberPayouts';
import { FaChartPie, FaUsers, FaHistory } from 'react-icons/fa';
import { useGroup } from '../../../contexts/GroupContext';

const SavingsDistributionPage = () => {
  const [activeTab, setActiveTab] = useState('cycles');
  const [selectedCycle, setSelectedCycle] = useState(null);
  const { currentGroup } = useGroup();

  const handleCycleSelect = cycle => {
    setSelectedCycle(cycle);
    setActiveTab('payouts');
  };

  // If no group is selected, show a message
  if (!currentGroup) {
    return (
      <div className='max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8'>
        <div className='bg-warning-subtle border-l-4 border-yellow-400 p-4 rounded-lg'>
          <p className='text-warning'>Please select a group to manage savings distribution.</p>
        </div>
      </div>
    );
  }

  return (
    <div className='max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8'>
      {/* Page Header */}
      <div className='mb-8'>
        <h1 className='text-3xl font-bold text-fg'>Savings Distribution Management</h1>
        <p className='mt-2 text-fg-muted'>
          Manage 6-month savings cycles and calculate member payouts for Ubwizigame distributions
        </p>
      </div>

      {/* Business Rules Reminder */}
      <div className='bg-info-subtle border-l-4 border-blue-400 p-4 mb-8 rounded-lg'>
        <div className='flex'>
          <div className='flex-shrink-0'>
            <FaChartPie className='h-5 w-5 text-blue-400' />
          </div>
          <div className='ml-3'>
            <h3 className='text-sm font-medium text-info'>Important Business Rules</h3>
            <div className='mt-2 text-sm text-info'>
              <ul className='list-disc list-inside space-y-1'>
                <li>Each savings cycle runs for 6 months</li>
                <li>
                  Only <strong>Ubwizigame</strong> (personal savings) are distributed to members
                </li>
                <li>
                  <strong>Ingoboka</strong> (solidarity fund) remains in the group account
                </li>
                <li>Members may contribute different amounts over time</li>
              </ul>
            </div>
          </div>
        </div>
      </div>

      {/* Tabs */}
      <div className='border-b border-border mb-8'>
        <nav className='-mb-px flex space-x-8'>
          <button
            onClick={() => setActiveTab('cycles')}
            className={`py-2 px-1 border-b-2 font-medium text-sm ${
              activeTab === 'cycles'
                ? 'border-blue-500 text-info'
                : 'border-transparent text-fg-muted hover:text-fg hover:border-border'
            }`}
          >
            <div className='flex items-center space-x-2'>
              <FaHistory className='h-4 w-4' />
              <span>Savings Cycles</span>
            </div>
          </button>
          <button
            onClick={() => setActiveTab('payouts')}
            className={`py-2 px-1 border-b-2 font-medium text-sm ${
              activeTab === 'payouts'
                ? 'border-blue-500 text-info'
                : 'border-transparent text-fg-muted hover:text-fg hover:border-border'
            }`}
            disabled={!selectedCycle}
          >
            <div className='flex items-center space-x-2'>
              <FaUsers className='h-4 w-4' />
              <span>Member Payouts</span>
            </div>
          </button>
        </nav>
      </div>

      {/* Tab Content */}
      {activeTab === 'cycles' && (
        <SavingsCycleManagement
          groupId={currentGroup.id}
          groupName={currentGroup.name}
          onCycleSelect={handleCycleSelect}
        />
      )}

      {activeTab === 'payouts' && selectedCycle && (
        <MemberPayouts cycleId={selectedCycle.id} cycleStatus={selectedCycle.status} />
      )}
    </div>
  );
};

export default SavingsDistributionPage;
