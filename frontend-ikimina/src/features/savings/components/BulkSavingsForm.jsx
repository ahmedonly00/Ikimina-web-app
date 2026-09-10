import React, { useState, useEffect } from 'react';
import toast from 'react-hot-toast';
import Button from '../../../components/ui/Button';
import { useGetUsersQuery } from '../savingsApi';
import { FaPlus, FaTrash, FaCalendarAlt, FaUser } from 'react-icons/fa';

const BulkSavingsForm = ({ onClose, onSubmit }) => {
  const [date, setDate] = useState(new Date().toISOString().split('T')[0]);
  const [memberSavings, setMemberSavings] = useState([]);
  const { data: users, isLoading: usersLoading } = useGetUsersQuery();
  // Seeds one empty row on mount. Deliberately runs once: adding
  // handleAddMember or memberSavings.length would re-seed a row every time the
  // user removed the last one.
  useEffect(() => {
    if (memberSavings.length === 0) {
      handleAddMember();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleAddMember = () => {
    setMemberSavings([
      ...memberSavings,
      {
        userId: '',
        userName: '',
        ubwizigameAmount: '',
        ingobokaAmount: '',
      },
    ]);
  };

  const handleRemoveMember = index => {
    setMemberSavings(memberSavings.filter((_, i) => i !== index));
  };

  const handleMemberChange = (index, field, value) => {
    const updated = [...memberSavings];
    if (field === 'userId') {
      const user = users?.find(u => u.id.toString() === value);
      updated[index].userId = value;
      updated[index].userName = user ? `${user.firstName} ${user.lastName}` : '';
    } else {
      updated[index][field] = value;
    }
    setMemberSavings(updated);
  };

  const handleSubmit = e => {
    e.preventDefault();

    const validMembers = memberSavings.filter(
      m => m.userId && (m.ubwizigameAmount || m.ingobokaAmount)
    );

    if (validMembers.length === 0) {
      toast.error('Please add at least one member with savings');
      return;
    }

    const bulkData = {
      date,
      members: validMembers.map(m => ({
        userId: parseInt(m.userId),
        ubwizigameAmount: m.ubwizigameAmount ? parseFloat(m.ubwizigameAmount) : 0,
        ingobokaAmount: m.ingobokaAmount ? parseFloat(m.ingobokaAmount) : 0,
      })),
    };

    onSubmit(bulkData);
  };

  const getAvailableUsers = () => {
    if (!users || !Array.isArray(users)) return [];
    const selectedIds = memberSavings.map(m => m.userId).filter(Boolean);
    return users.filter(u => !selectedIds.includes(u.id.toString()));
  };

  return (
    <form onSubmit={handleSubmit} className='space-y-6'>
      <div>
        <label htmlFor='bulk-savings-date' className='block text-sm font-medium text-gray-700 mb-2'>
          <FaCalendarAlt className='inline mr-2' />
          Date
        </label>
        <input
          id='bulk-savings-date'
          type='date'
          value={date}
          onChange={e => setDate(e.target.value)}
          className='w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500'
          required
        />
      </div>

      <div className='space-y-4'>
        <div className='flex justify-between items-center'>
          <h3 className='text-lg font-medium text-gray-900'>Member Savings</h3>
          <Button
            type='button'
            onClick={handleAddMember}
            className='bg-green-600 hover:bg-green-700'
            disabled={usersLoading || !users || getAvailableUsers().length === 0}
          >
            <FaPlus className='mr-2' />
            Add Member
          </Button>
        </div>

        {memberSavings.map((member, index) => (
          <div key={index} className='border border-gray-200 rounded-lg p-4 space-y-4'>
            <div className='flex justify-between items-start'>
              <div className='flex-1 grid grid-cols-1 md:grid-cols-4 gap-4'>
                <div>
                  <label
                    htmlFor={`bulk-member-${index}`}
                    className='block text-sm font-medium text-gray-700 mb-1'
                  >
                    <FaUser className='inline mr-1' />
                    Member
                  </label>
                  <select
                    id={`bulk-member-${index}`}
                    value={member.userId}
                    onChange={e => handleMemberChange(index, 'userId', e.target.value)}
                    className='w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500'
                    required
                  >
                    <option value=''>Select Member</option>
                    {usersLoading ? (
                      <option>Loading...</option>
                    ) : users && Array.isArray(users) ? (
                      (index === 0 ? users : getAvailableUsers()).map(user => (
                        <option key={user.id} value={user.id}>
                          {user.firstName} {user.lastName}
                        </option>
                      ))
                    ) : (
                      <option>No users available</option>
                    )}
                  </select>
                </div>

                <div>
                  <label
                    htmlFor={`bulk-ubwizigame-${index}`}
                    className='block text-sm font-medium text-gray-700 mb-1'
                  >
                    UBWIZIGAME Amount
                  </label>
                  <input
                    id={`bulk-ubwizigame-${index}`}
                    type='number'
                    step='0.01'
                    min='0'
                    value={member.ubwizigameAmount}
                    onChange={e => handleMemberChange(index, 'ubwizigameAmount', e.target.value)}
                    className='w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500'
                    placeholder='0.00'
                  />
                </div>

                <div>
                  <label
                    htmlFor={`bulk-ingoboka-${index}`}
                    className='block text-sm font-medium text-gray-700 mb-1'
                  >
                    INGOBOKA Amount
                  </label>
                  <input
                    id={`bulk-ingoboka-${index}`}
                    type='number'
                    step='0.01'
                    min='0'
                    value={member.ingobokaAmount}
                    onChange={e => handleMemberChange(index, 'ingobokaAmount', e.target.value)}
                    className='w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500'
                    placeholder='0.00'
                  />
                </div>

                <div className='flex items-end'>
                  {memberSavings.length > 1 && (
                    <Button
                      type='button'
                      onClick={() => handleRemoveMember(index)}
                      className='bg-red-600 hover:bg-red-700'
                    >
                      <FaTrash />
                    </Button>
                  )}
                </div>
              </div>
            </div>
          </div>
        ))}
      </div>

      <div className='flex justify-end space-x-4 pt-4 border-t'>
        <Button type='button' onClick={onClose} className='bg-gray-500 hover:bg-gray-600'>
          Cancel
        </Button>
        <Button type='submit' className='bg-blue-600 hover:bg-blue-700'>
          Record Savings
        </Button>
      </div>
    </form>
  );
};

export default BulkSavingsForm;
