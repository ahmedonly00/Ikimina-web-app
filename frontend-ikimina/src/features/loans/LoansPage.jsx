import React, { useState, useEffect } from 'react';
import { useLocation } from 'react-router-dom';

import { toast } from 'react-hot-toast';
import Button from '../../components/ui/Button';
import QueryError from '../../components/ui/QueryError';
import Modal from '../../components/ui/Modal';
import { FaMoneyBillWave, FaEdit, FaCheckCircle, FaClock, FaTimesCircle } from 'react-icons/fa';
import { useAppContext } from '../../contexts/AppContext';
import {
  useGetLoansQuery,
  useRequestLoanMutation,
  useUpdateLoanStatusMutation,
} from '../../app/api/apiSlice';
import { useSelector } from 'react-redux';
import { selectCurrentUser } from '../auth/authSlice';
import { formatCurrency } from '../../i18n';

const LoansPage = () => {
  const location = useLocation();
  const { t } = useAppContext();
  const currentUser = useSelector(selectCurrentUser);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [currentLoan, setCurrentLoan] = useState(null);
  const [isLoading, setIsLoading] = useState(false);
  const [formData, setFormData] = useState({
    amount: '',
    interestRate: '',
    term: '',
    purpose: '',
    status: 'PENDING',
  });

  // Use RTK Query hooks
  const {
    data: loans = [],
    isLoading: loansLoading,
    error,
    refetch,
  } = useGetLoansQuery(currentUser?.id);
  const [requestLoan, { isLoading: requestLoading }] = useRequestLoanMutation();
  const [updateLoanStatus, { isLoading: statusLoading }] = useUpdateLoanStatusMutation();

  // Check if we need to open the loan modal from navigation state
  useEffect(() => {
    if (location.state?.openLoanModal) {
      setIsModalOpen(true);
      // Clear the state to prevent reopening on refresh
      window.history.replaceState({}, document.title);
    }
  }, [location.state]);

  const handleInputChange = e => {
    const { name, value } = e.target;
    setFormData(prev => ({ ...prev, [name]: value }));
  };

  const handleSubmit = async e => {
    e.preventDefault();
    setIsLoading(true);
    try {
      if (currentLoan) {
        // TODO: Implement update loan API call
        toast.success('Loan updated successfully!');
      } else {
        await requestLoan({
          ...formData,
          userId: currentUser.id,
        }).unwrap();
        toast.success('Loan application submitted successfully!');
      }

      setIsModalOpen(false);
      setCurrentLoan(null);
      setFormData({
        amount: '',
        interestRate: '',
        term: '',
        purpose: '',
        status: 'PENDING',
      });
      refetch(); // Refresh the loans list
    } catch (err) {
      toast.error('Failed to submit loan application: ' + (err.data?.message || err.message));
      console.error('Failed to submit loan:', err);
    } finally {
      setIsLoading(false);
    }
  };

  const handleEdit = loan => {
    setCurrentLoan(loan);
    setFormData({
      amount: loan.amount,
      interestRate: loan.interestRate,
      term: loan.term,
      purpose: loan.purpose,
      memberId: loan.memberId,
      status: loan.status,
    });
    setIsModalOpen(true);
  };

  const handleStatusChange = async (id, newStatus) => {
    try {
      await updateLoanStatus({ loanId: id, status: newStatus }).unwrap();
      toast.success(`Loan ${newStatus.toLowerCase()}`);
      refetch();
    } catch (err) {
      const message =
        (err && err.data && (err.data.detail || err.data.message)) ||
        'Failed to update loan status';
      toast.error(message);
    }
  };
  // Helper functions

  const getStatusBadge = status => {
    const styles = {
      PENDING: 'bg-warning-subtle text-warning',
      APPROVED: 'bg-success-subtle text-success',
      REJECTED: 'bg-danger-subtle text-danger',
    };
    const icons = {
      PENDING: <FaClock className='mr-1' />,
      APPROVED: <FaCheckCircle className='mr-1' />,
      REJECTED: <FaTimesCircle className='mr-1' />,
    };

    return (
      <span
        className={`inline-flex items-center px-2 py-1 text-xs font-semibold rounded-full ${styles[status]}`}
      >
        {icons[status]}
        {status}
      </span>
    );
  };

  // Any in-flight server work disables the form controls, so a slow
  // connection cannot produce a double submission.
  const busy = isLoading || loansLoading || requestLoading || statusLoading;

  // Calculate totals
  const totalLoans = loans.reduce(
    (sum, loan) => (loan.status === 'APPROVED' ? sum + loan.amount : sum),
    0
  );

  return (
    <div className='h-full w-full overflow-y-auto bg-bg'>
      <div className='px-2 sm:px-4 lg:px-8 py-4 sm:py-6 lg:py-8 max-w-full xl:max-w-7xl mx-auto'>
        <QueryError error={error} onRetry={refetch} title='Could not load loans' />
        {/* Header with stats */}
        <div className='mb-6'>
          <div className='flex flex-col sm:flex-row sm:justify-between sm:items-center mb-4 sm:mb-6 space-y-4 sm:space-y-0'>
            <div>
              <h1 className='text-xl sm:text-2xl font-bold text-fg'>{t('loansManagement')}</h1>
              <p className='mt-1 text-sm text-fg-muted'>{t('loansManagementDescription')}</p>
            </div>
            <Button
              onClick={() => setIsModalOpen(true)}
              disabled={busy}
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
                {t('newLoanApplication')}
              </span>
            </Button>
          </div>

          {/* Stats Cards */}
          <div className='grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-6'>
            <div className='bg-surface rounded-lg shadow p-4 sm:p-6'>
              <div className='flex items-center'>
                <div className='flex-shrink-0 bg-primary-subtle rounded-md p-3'>
                  <FaMoneyBillWave className='h-6 w-6 text-primary' />
                </div>
                <div className='ml-4'>
                  <p className='text-sm font-medium text-fg-muted'>{t('totalApproved')}</p>
                  <p className='text-2xl font-semibold text-fg'>{formatCurrency(totalLoans)}</p>
                </div>
              </div>
            </div>

            <div className='bg-surface rounded-lg shadow p-4 sm:p-6'>
              <div className='flex items-center'>
                <div className='flex-shrink-0 bg-warning-subtle rounded-md p-3'>
                  <FaClock className='h-6 w-6 text-warning' />
                </div>
                <div className='ml-4'>
                  <p className='text-sm font-medium text-fg-muted'>{t('pending')}</p>
                  <p className='text-2xl font-semibold text-fg'>
                    {loans.filter(l => l.status === 'PENDING').length}
                  </p>
                </div>
              </div>
            </div>

            <div className='bg-surface rounded-lg shadow p-4 sm:p-6'>
              <div className='flex items-center'>
                <div className='flex-shrink-0 bg-success-subtle rounded-md p-3'>
                  <FaCheckCircle className='h-6 w-6 text-success' />
                </div>
                <div className='ml-4'>
                  <p className='text-sm font-medium text-fg-muted'>{t('approved')}</p>
                  <p className='text-2xl font-semibold text-fg'>
                    {loans.filter(l => l.status === 'APPROVED').length}
                  </p>
                </div>
              </div>
            </div>

            <div className='bg-surface rounded-lg shadow p-4 sm:p-6'>
              <div className='flex items-center'>
                <div className='flex-shrink-0 bg-danger-subtle rounded-md p-3'>
                  <FaTimesCircle className='h-6 w-6 text-danger' />
                </div>
                <div className='ml-4'>
                  <p className='text-sm font-medium text-fg-muted'>{t('rejected')}</p>
                  <p className='text-2xl font-semibold text-fg'>
                    {loans.filter(l => l.status === 'REJECTED').length}
                  </p>
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* Loans Table */}
        <div className='bg-surface shadow overflow-hidden sm:rounded-lg'>
          <div className='overflow-x-auto'>
            <table className='min-w-full divide-y divide-border'>
              <thead className='bg-bg'>
                <tr>
                  <th className='px-3 sm:px-4 lg:px-6 py-2 sm:py-3 text-left text-xs font-medium text-fg-muted uppercase tracking-wider'>
                    {t('member')}
                  </th>
                  <th className='px-3 sm:px-4 lg:px-6 py-2 sm:py-3 text-left text-xs font-medium text-fg-muted uppercase tracking-wider'>
                    {t('amount')}
                  </th>
                  <th className='px-3 sm:px-4 lg:px-6 py-2 sm:py-3 text-left text-xs font-medium text-fg-muted uppercase tracking-wider'>
                    {t('interest')}
                  </th>
                  <th className='px-3 sm:px-4 lg:px-6 py-2 sm:py-3 text-left text-xs font-medium text-fg-muted uppercase tracking-wider'>
                    {t('term')}
                  </th>
                  <th className='px-3 sm:px-4 lg:px-6 py-2 sm:py-3 text-left text-xs font-medium text-fg-muted uppercase tracking-wider'>
                    {t('purpose')}
                  </th>
                  <th className='px-3 sm:px-4 lg:px-6 py-2 sm:py-3 text-left text-xs font-medium text-fg-muted uppercase tracking-wider'>
                    {t('status')}
                  </th>
                  <th className='px-3 sm:px-4 lg:px-6 py-2 sm:py-3 text-left text-xs font-medium text-fg-muted uppercase tracking-wider'>
                    {t('actions')}
                  </th>
                </tr>
              </thead>
              <tbody className='bg-surface divide-y divide-border'>
                {loans.map(loan => (
                  <tr key={loan.id} className='hover:bg-bg'>
                    <td className='px-3 sm:px-4 lg:px-6 py-3 sm:py-4 whitespace-nowrap'>
                      <div>
                        <div className='text-sm font-medium text-fg'>{loan.memberName}</div>
                        <div className='text-xs text-fg-muted'>{loan.memberNumber}</div>
                      </div>
                    </td>
                    <td className='px-3 sm:px-4 lg:px-6 py-3 sm:py-4 whitespace-nowrap'>
                      <div className='text-sm text-fg'>{formatCurrency(loan.amount)}</div>
                    </td>
                    <td className='px-3 sm:px-4 lg:px-6 py-3 sm:py-4 whitespace-nowrap'>
                      <div className='text-sm text-fg'>{loan.interestRate}%</div>
                    </td>
                    <td className='px-3 sm:px-4 lg:px-6 py-3 sm:py-4 whitespace-nowrap'>
                      <div className='text-sm text-fg'>{loan.term} months</div>
                    </td>
                    <td className='px-3 sm:px-4 lg:px-6 py-3 sm:py-4'>
                      <div className='text-sm text-fg truncate max-w-xs'>{loan.purpose}</div>
                    </td>
                    <td className='px-3 sm:px-4 lg:px-6 py-3 sm:py-4 whitespace-nowrap'>
                      {getStatusBadge(loan.status)}
                    </td>
                    <td className='px-3 sm:px-4 lg:px-6 py-3 sm:py-4 whitespace-nowrap text-xs font-medium'>
                      <div className='flex space-x-2'>
                        {loan.status === 'PENDING' && (
                          <>
                            <button
                              onClick={() => handleStatusChange(loan.id, 'APPROVED')}
                              className='text-success hover:opacity-80'
                              title={t('approve')}
                            >
                              <FaCheckCircle />
                            </button>
                            <button
                              onClick={() => handleStatusChange(loan.id, 'REJECTED')}
                              className='text-danger hover:opacity-80'
                              title={t('reject')}
                            >
                              <FaTimesCircle />
                            </button>
                          </>
                        )}
                        <button
                          onClick={() => handleEdit(loan)}
                          className='text-primary hover:text-primary'
                          title={t('edit')}
                        >
                          <FaEdit />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
                {/* Without this the table rendered its headers above a large
                    blank area, which reads as a failed load rather than as
                    "there is nothing here yet". */}
                {!loansLoading && loans.length === 0 && (
                  <tr>
                    <td colSpan={7} className='px-6 py-12 text-center'>
                      <p className='text-sm font-medium text-fg'>{t('noLoans')}</p>
                      <p className='mt-1 text-sm text-fg-muted'>{t('noLoansHint')}</p>
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>

        {/* Loan Modal */}
        <Modal
          isOpen={isModalOpen}
          onClose={() => {
            setIsModalOpen(false);
            setCurrentLoan(null);
            setFormData({
              amount: '',
              interestRate: '',
              term: '',
              purpose: '',
              memberId: '',
              status: 'PENDING',
            });
          }}
          title={currentLoan ? t('editLoan') : t('newLoanApplication')}
        >
          <form onSubmit={handleSubmit} className='space-y-4'>
            <div>
              <label className='block text-sm font-medium text-fg'>{t('member')}</label>
              <select
                name='memberId'
                value={formData.memberId}
                onChange={handleInputChange}
                required
                className='mt-1 block w-full pl-3 pr-10 py-2 text-base border-border focus:outline-none focus:ring-primary focus:border-primary sm:text-sm rounded-md'
                disabled={busy}
              >
                <option value=''>{t('selectMember')}</option>
                <option value='1'>John Doe - MEM001</option>
                <option value='2'>Jane Smith - MEM002</option>
                <option value='3'>Alice Johnson - MEM003</option>
                <option value='4'>David Wilson - MEM004</option>
              </select>
            </div>

            <div className='grid grid-cols-2 gap-4'>
              <div>
                <label className='block text-sm font-medium text-fg'>{t('amount')} ($)</label>
                <input
                  type='number'
                  name='amount'
                  value={formData.amount}
                  onChange={handleInputChange}
                  required
                  min='1'
                  className='mt-1 block w-full border-border rounded-md shadow-sm focus:ring-primary focus:border-primary sm:text-sm px-3 py-2 border'
                  disabled={busy}
                />
              </div>
              <div>
                <label className='block text-sm font-medium text-fg'>{t('interestRate')} (%)</label>
                <input
                  type='number'
                  name='interestRate'
                  value={formData.interestRate}
                  onChange={handleInputChange}
                  required
                  min='0'
                  max='100'
                  step='0.1'
                  className='mt-1 block w-full border-border rounded-md shadow-sm focus:ring-primary focus:border-primary sm:text-sm px-3 py-2 border'
                  disabled={busy}
                />
              </div>
            </div>

            <div>
              <label className='block text-sm font-medium text-fg'>
                {t('term')} ({t('months')})
              </label>
              <input
                type='number'
                name='term'
                value={formData.term}
                onChange={handleInputChange}
                required
                min='1'
                className='mt-1 block w-full border-border rounded-md shadow-sm focus:ring-primary focus:border-primary sm:text-sm px-3 py-2 border'
                disabled={busy}
              />
            </div>

            <div>
              <label className='block text-sm font-medium text-fg'>{t('purpose')}</label>
              <textarea
                name='purpose'
                value={formData.purpose}
                onChange={handleInputChange}
                required
                rows={3}
                className='mt-1 block w-full border-border rounded-md shadow-sm focus:ring-primary focus:border-primary sm:text-sm px-3 py-2 border'
                disabled={busy}
                placeholder={t('loanPurposePlaceholder')}
              />
            </div>

            {currentLoan && (
              <div>
                <label className='block text-sm font-medium text-fg'>{t('status')}</label>
                <select
                  name='status'
                  value={formData.status}
                  onChange={handleInputChange}
                  className='mt-1 block w-full pl-3 pr-10 py-2 text-base border-border focus:outline-none focus:ring-primary focus:border-primary sm:text-sm rounded-md'
                  disabled={busy}
                >
                  <option value='PENDING'>{t('pending')}</option>
                  <option value='APPROVED'>{t('approved')}</option>
                  <option value='REJECTED'>{t('rejected')}</option>
                </select>
              </div>
            )}

            <div className='flex justify-end space-x-3 pt-4'>
              <button
                type='button'
                onClick={() => {
                  setIsModalOpen(false);
                  setCurrentLoan(null);
                }}
                disabled={busy}
                className='bg-surface py-2 px-4 border border-border rounded-md shadow-sm text-sm font-medium text-fg hover:bg-bg focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary disabled:opacity-50'
              >
                {t('cancel')}
              </button>
              <button
                type='submit'
                disabled={busy}
                className='inline-flex justify-center py-2 px-4 border border-transparent shadow-sm text-sm font-medium rounded-md text-white bg-primary hover:bg-primary-hover focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary disabled:opacity-50'
              >
                {isLoading
                  ? t('processing')
                  : currentLoan
                    ? t('updateLoan')
                    : t('submitApplication')}
              </button>
            </div>
          </form>
        </Modal>
      </div>
    </div>
  );
};

export default LoansPage;
