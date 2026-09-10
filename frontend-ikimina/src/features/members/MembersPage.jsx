import React, { useState } from 'react';
import { FaUsers, FaUserPlus, FaTimes } from 'react-icons/fa';
import { useAppContext } from '../../contexts/AppContext';

const MembersPage = () => {
  const { t } = useAppContext();
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [formData, setFormData] = useState({
    firstName: '',
    lastName: '',
    email: '',
    phone: '',
    address: '',
    dateOfBirth: '',
    membershipNumber: '',
    status: 'ACTIVE',
  });

  const handleInputChange = e => {
    const { name, value } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: value,
    }));
  };

  const handleSubmit = e => {
    e.preventDefault();
    console.log('Adding member:', formData);
    // TODO: Implement API call to add member
    setIsModalOpen(false);
    setFormData({
      firstName: '',
      lastName: '',
      email: '',
      phone: '',
      address: '',
      dateOfBirth: '',
      membershipNumber: '',
      status: 'ACTIVE',
    });
  };
  return (
    <div className='h-full w-full overflow-y-auto bg-bg'>
      <div className='px-2 sm:px-4 lg:px-8 py-4 sm:py-6 lg:py-8 max-w-full xl:max-w-7xl mx-auto'>
        <div className='flex justify-between items-center mb-6'>
          <h1 className='text-2xl font-bold text-fg'>Members Management</h1>
          <button
            onClick={() => setIsModalOpen(true)}
            className='flex items-center px-4 py-2 bg-primary text-white rounded-md hover:bg-primary-hover transition-colors'
          >
            <FaUserPlus className='mr-2' />
            {t('addMember')}
          </button>
        </div>

        <div className='bg-surface rounded-lg shadow overflow-hidden'>
          <div className='p-6 text-center text-fg-muted'>
            <FaUsers className='mx-auto h-12 w-12 text-fg-subtle mb-4' />
            <p>{t('membersManagementPlaceholder')}</p>
            <p className='text-sm mt-2'>{t('membersManagementDescription')}</p>
          </div>
        </div>

        {/* Add Member Modal */}
        {isModalOpen && (
          <div className='fixed inset-0 z-50 overflow-y-auto'>
            <div className='flex items-center justify-center min-h-screen pt-4 px-4 pb-20 text-center sm:block sm:p-0'>
              <div className='fixed inset-0 transition-opacity' aria-hidden='true'>
                <div className='absolute inset-0 bg-fg opacity-75'></div>
              </div>

              <div className='inline-block align-bottom bg-surface rounded-lg text-left overflow-hidden shadow-xl transform transition-all sm:my-8 sm:align-middle sm:max-w-lg sm:w-full'>
                <div className='bg-surface px-4 pt-5 pb-4 sm:p-6 sm:pb-4'>
                  <div className='flex justify-between items-center mb-4'>
                    <h3 className='text-lg leading-6 font-medium text-fg'>{t('addNewMember')}</h3>
                    <button
                      onClick={() => setIsModalOpen(false)}
                      className='text-fg-subtle hover:text-fg-muted'
                    >
                      <FaTimes className='h-6 w-6' />
                    </button>
                  </div>

                  <form onSubmit={handleSubmit} className='space-y-4'>
                    <div className='grid grid-cols-2 gap-4'>
                      <div>
                        <label className='block text-sm font-medium text-fg'>
                          {t('firstName')}
                        </label>
                        <input
                          type='text'
                          name='firstName'
                          value={formData.firstName}
                          onChange={handleInputChange}
                          required
                          className='mt-1 block w-full border-border rounded-md shadow-sm focus:ring-primary focus:border-primary sm:text-sm px-3 py-2 border'
                        />
                      </div>
                      <div>
                        <label className='block text-sm font-medium text-fg'>{t('lastName')}</label>
                        <input
                          type='text'
                          name='lastName'
                          value={formData.lastName}
                          onChange={handleInputChange}
                          required
                          className='mt-1 block w-full border-border rounded-md shadow-sm focus:ring-primary focus:border-primary sm:text-sm px-3 py-2 border'
                        />
                      </div>
                    </div>

                    <div>
                      <label className='block text-sm font-medium text-fg'>{t('email')}</label>
                      <input
                        type='email'
                        name='email'
                        value={formData.email}
                        onChange={handleInputChange}
                        required
                        className='mt-1 block w-full border-border rounded-md shadow-sm focus:ring-primary focus:border-primary sm:text-sm px-3 py-2 border'
                      />
                    </div>

                    <div>
                      <label className='block text-sm font-medium text-fg'>{t('phone')}</label>
                      <input
                        type='tel'
                        name='phone'
                        value={formData.phone}
                        onChange={handleInputChange}
                        className='mt-1 block w-full border-border rounded-md shadow-sm focus:ring-primary focus:border-primary sm:text-sm px-3 py-2 border'
                      />
                    </div>

                    <div>
                      <label className='block text-sm font-medium text-fg'>{t('address')}</label>
                      <textarea
                        name='address'
                        value={formData.address}
                        onChange={handleInputChange}
                        rows={3}
                        className='mt-1 block w-full border-border rounded-md shadow-sm focus:ring-primary focus:border-primary sm:text-sm px-3 py-2 border'
                      />
                    </div>

                    <div className='grid grid-cols-2 gap-4'>
                      <div>
                        <label className='block text-sm font-medium text-fg'>
                          {t('dateOfBirth')}
                        </label>
                        <input
                          type='date'
                          name='dateOfBirth'
                          value={formData.dateOfBirth}
                          onChange={handleInputChange}
                          className='mt-1 block w-full border-border rounded-md shadow-sm focus:ring-primary focus:border-primary sm:text-sm px-3 py-2 border'
                        />
                      </div>
                      <div>
                        <label className='block text-sm font-medium text-fg'>
                          {t('membershipNumber')}
                        </label>
                        <input
                          type='text'
                          name='membershipNumber'
                          value={formData.membershipNumber}
                          onChange={handleInputChange}
                          className='mt-1 block w-full border-border rounded-md shadow-sm focus:ring-primary focus:border-primary sm:text-sm px-3 py-2 border'
                        />
                      </div>
                    </div>

                    <div>
                      <label className='block text-sm font-medium text-fg'>{t('status')}</label>
                      <select
                        name='status'
                        value={formData.status}
                        onChange={handleInputChange}
                        className='mt-1 block w-full border-border rounded-md shadow-sm focus:ring-primary focus:border-primary sm:text-sm px-3 py-2 border'
                      >
                        <option value='ACTIVE'>{t('active')}</option>
                        <option value='INACTIVE'>{t('inactive')}</option>
                        <option value='SUSPENDED'>{t('suspended')}</option>
                      </select>
                    </div>
                  </form>
                </div>

                <div className='bg-bg px-4 py-3 sm:px-6 sm:flex sm:flex-row-reverse'>
                  <button
                    type='button'
                    onClick={handleSubmit}
                    className='w-full inline-flex justify-center rounded-md border border-transparent shadow-sm px-4 py-2 bg-primary text-base font-medium text-white hover:bg-primary-hover focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary sm:ml-3 sm:w-auto sm:text-sm'
                  >
                    {t('addMember')}
                  </button>
                  <button
                    type='button'
                    onClick={() => setIsModalOpen(false)}
                    className='mt-3 w-full inline-flex justify-center rounded-md border border-border shadow-sm px-4 py-2 bg-surface text-base font-medium text-fg hover:bg-bg focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary sm:mt-0 sm:ml-3 sm:w-auto sm:text-sm'
                  >
                    {t('cancel')}
                  </button>
                </div>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default MembersPage;
