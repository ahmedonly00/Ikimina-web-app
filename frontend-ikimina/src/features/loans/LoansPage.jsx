import React, { useState, useEffect } from 'react';
import { useLocation } from 'react-router-dom';
import { format } from 'date-fns';
import { toast } from 'react-hot-toast';
import Button from '../../components/ui/Button';
import Modal from '../../components/ui/Modal';
import { FaDollarSign, FaCalendarAlt, FaUser, FaEdit, FaTrash, FaCheckCircle, FaClock, FaTimesCircle } from 'react-icons/fa';
import { useAppContext } from '../../contexts/AppContext';
import { useGetLoansQuery, useRequestLoanMutation } from '../../app/api/apiSlice';
import { useSelector } from 'react-redux';
import { selectCurrentUser } from '../auth/authSlice';

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
    status: 'PENDING'
  });

  // Use RTK Query hooks
  const { data: loans = [], isLoading: loansLoading, error, refetch } = useGetLoansQuery(currentUser?.id);
  const [requestLoan, { isLoading: requestLoading }] = useRequestLoanMutation();

  // Check if we need to open the loan modal from navigation state
  useEffect(() => {
    if (location.state?.openLoanModal) {
      setIsModalOpen(true);
      // Clear the state to prevent reopening on refresh
      window.history.replaceState({}, document.title);
    }
  }, [location.state]);

  const handleInputChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({ ...prev, [name]: value }));
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setIsLoading(true);
    try {
      if (currentLoan) {
        // TODO: Implement update loan API call
        toast.success('Loan updated successfully!');
      } else {
        await requestLoan({
          ...formData,
          userId: currentUser.id
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
        status: 'PENDING'
      });
      refetch(); // Refresh the loans list
    } catch (err) {
      toast.error('Failed to submit loan application: ' + (err.data?.message || err.message));
      console.error('Failed to submit loan:', err);
    } finally {
      setIsLoading(false);
    }
  };

  const handleEdit = (loan) => {
    setCurrentLoan(loan);
    setFormData({
      amount: loan.amount,
      interestRate: loan.interestRate,
      term: loan.term,
      purpose: loan.purpose,
      memberId: loan.memberId,
      status: loan.status
    });
    setIsModalOpen(true);
  };

  const handleDelete = async (id) => {
    if (window.confirm('Are you sure you want to delete this loan application?')) {
      setIsLoading(true);
      try {
        await new Promise(resolve => setTimeout(resolve, 1000));
        setLoans(loans.filter(loan => loan.id !== id));
        toast.success('Loan deleted successfully!');
      } catch (err) {
        toast.error('Failed to delete loan');
      } finally {
        setIsLoading(false);
      }
    }
  };

  const handleStatusChange = async (id, newStatus) => {
    setIsLoading(true);
    try {
      await new Promise(resolve => setTimeout(resolve, 1000));
      const updatedLoans = loans.map(loan => 
        loan.id === id ? { ...loan, status: newStatus } : loan
      );
      setLoans(updatedLoans);
      toast.success(`Loan status updated to ${newStatus}`);
    } catch (err) {
      toast.error('Failed to update loan status');
    } finally {
      setIsLoading(false);
    }
  };

  // Helper functions
  const getMemberName = (memberId) => {
    const members = {
      '1': 'John Doe',
      '2': 'Jane Smith',
      '3': 'Alice Johnson',
      '4': 'David Wilson',
    };
    return members[memberId] || 'Unknown Member';
  };

  const getMemberNumber = (memberId) => {
    const numbers = {
      '1': 'MEM001',
      '2': 'MEM002',
      '3': 'MEM003',
      '4': 'MEM004',
    };
    return numbers[memberId] || 'MEM000';
  };

  const calculateDueDate = (termMonths) => {
    const date = new Date();
    date.setMonth(date.getMonth() + parseInt(termMonths));
    return date.toISOString().split('T')[0];
  };

  const getStatusBadge = (status) => {
    const styles = {
      PENDING: 'bg-yellow-100 text-yellow-800',
      APPROVED: 'bg-green-100 text-green-800',
      REJECTED: 'bg-red-100 text-red-800',
    };
    const icons = {
      PENDING: <FaClock className="mr-1" />,
      APPROVED: <FaCheckCircle className="mr-1" />,
      REJECTED: <FaTimesCircle className="mr-1" />,
    };
    
    return (
      <span className={`inline-flex items-center px-2 py-1 text-xs font-semibold rounded-full ${styles[status]}`}>
        {icons[status]}
        {status}
      </span>
    );
  };

  // Calculate totals
  const totalLoans = loans.reduce((sum, loan) => 
    loan.status === 'APPROVED' ? sum + loan.amount : sum, 0
  );

  return (
    <div className="h-full w-full overflow-y-auto bg-gray-50">
      <div className="px-2 sm:px-4 lg:px-8 py-4 sm:py-6 lg:py-8 max-w-full xl:max-w-7xl mx-auto">
        {/* Header with stats */}
        <div className="mb-6">
          <div className="flex flex-col sm:flex-row sm:justify-between sm:items-center mb-4 sm:mb-6 space-y-4 sm:space-y-0">
            <div>
              <h1 className="text-xl sm:text-2xl font-bold text-gray-800">{t('loansManagement')}</h1>
              <p className="mt-1 text-sm text-gray-500">{t('loansManagementDescription')}</p>
            </div>
            <Button 
              onClick={() => setIsModalOpen(true)} 
              disabled={isLoading}
              className="w-full sm:w-auto"
            >
              <span className="flex items-center">
                <svg className="-ml-1 mr-2 h-5 w-5" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
                </svg>
                {t('newLoanApplication')}
              </span>
            </Button>
          </div>

          {/* Stats Cards */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
            <div className="bg-white rounded-lg shadow p-4 sm:p-6">
              <div className="flex items-center">
                <div className="flex-shrink-0 bg-indigo-100 rounded-md p-3">
                  <FaDollarSign className="h-6 w-6 text-indigo-600" />
                </div>
                <div className="ml-4">
                  <p className="text-sm font-medium text-gray-500">{t('totalApproved')}</p>
                  <p className="text-2xl font-semibold text-gray-900">${totalLoans.toLocaleString()}</p>
                </div>
              </div>
            </div>
            
            <div className="bg-white rounded-lg shadow p-4 sm:p-6">
              <div className="flex items-center">
                <div className="flex-shrink-0 bg-yellow-100 rounded-md p-3">
                  <FaClock className="h-6 w-6 text-yellow-600" />
                </div>
                <div className="ml-4">
                  <p className="text-sm font-medium text-gray-500">{t('pending')}</p>
                  <p className="text-2xl font-semibold text-gray-900">
                    {loans.filter(l => l.status === 'PENDING').length}
                  </p>
                </div>
              </div>
            </div>
            
            <div className="bg-white rounded-lg shadow p-4 sm:p-6">
              <div className="flex items-center">
                <div className="flex-shrink-0 bg-green-100 rounded-md p-3">
                  <FaCheckCircle className="h-6 w-6 text-green-600" />
                </div>
                <div className="ml-4">
                  <p className="text-sm font-medium text-gray-500">{t('approved')}</p>
                  <p className="text-2xl font-semibold text-gray-900">
                    {loans.filter(l => l.status === 'APPROVED').length}
                  </p>
                </div>
              </div>
            </div>
            
            <div className="bg-white rounded-lg shadow p-4 sm:p-6">
              <div className="flex items-center">
                <div className="flex-shrink-0 bg-red-100 rounded-md p-3">
                  <FaTimesCircle className="h-6 w-6 text-red-600" />
                </div>
                <div className="ml-4">
                  <p className="text-sm font-medium text-gray-500">{t('rejected')}</p>
                  <p className="text-2xl font-semibold text-gray-900">
                    {loans.filter(l => l.status === 'REJECTED').length}
                  </p>
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* Loans Table */}
        <div className="bg-white shadow overflow-hidden sm:rounded-lg">
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-gray-200">
              <thead className="bg-gray-50">
                <tr>
                  <th className="px-3 sm:px-4 lg:px-6 py-2 sm:py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">{t('member')}</th>
                  <th className="px-3 sm:px-4 lg:px-6 py-2 sm:py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">{t('amount')}</th>
                  <th className="px-3 sm:px-4 lg:px-6 py-2 sm:py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">{t('interest')}</th>
                  <th className="px-3 sm:px-4 lg:px-6 py-2 sm:py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">{t('term')}</th>
                  <th className="px-3 sm:px-4 lg:px-6 py-2 sm:py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">{t('purpose')}</th>
                  <th className="px-3 sm:px-4 lg:px-6 py-2 sm:py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">{t('status')}</th>
                  <th className="px-3 sm:px-4 lg:px-6 py-2 sm:py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">{t('actions')}</th>
                </tr>
              </thead>
              <tbody className="bg-white divide-y divide-gray-200">
                {loans.map((loan) => (
                  <tr key={loan.id} className="hover:bg-gray-50">
                    <td className="px-3 sm:px-4 lg:px-6 py-3 sm:py-4 whitespace-nowrap">
                      <div>
                        <div className="text-sm font-medium text-gray-900">{loan.memberName}</div>
                        <div className="text-xs text-gray-500">{loan.memberNumber}</div>
                      </div>
                    </td>
                    <td className="px-3 sm:px-4 lg:px-6 py-3 sm:py-4 whitespace-nowrap">
                      <div className="text-sm text-gray-900">${loan.amount.toLocaleString()}</div>
                    </td>
                    <td className="px-3 sm:px-4 lg:px-6 py-3 sm:py-4 whitespace-nowrap">
                      <div className="text-sm text-gray-900">{loan.interestRate}%</div>
                    </td>
                    <td className="px-3 sm:px-4 lg:px-6 py-3 sm:py-4 whitespace-nowrap">
                      <div className="text-sm text-gray-900">{loan.term} months</div>
                    </td>
                    <td className="px-3 sm:px-4 lg:px-6 py-3 sm:py-4">
                      <div className="text-sm text-gray-900 truncate max-w-xs">{loan.purpose}</div>
                    </td>
                    <td className="px-3 sm:px-4 lg:px-6 py-3 sm:py-4 whitespace-nowrap">
                      {getStatusBadge(loan.status)}
                    </td>
                    <td className="px-3 sm:px-4 lg:px-6 py-3 sm:py-4 whitespace-nowrap text-xs font-medium">
                      <div className="flex space-x-2">
                        {loan.status === 'PENDING' && (
                          <>
                            <button
                              onClick={() => handleStatusChange(loan.id, 'APPROVED')}
                              className="text-green-600 hover:text-green-900"
                              title={t('approve')}
                            >
                              <FaCheckCircle />
                            </button>
                            <button
                              onClick={() => handleStatusChange(loan.id, 'REJECTED')}
                              className="text-red-600 hover:text-red-900"
                              title={t('reject')}
                            >
                              <FaTimesCircle />
                            </button>
                          </>
                        )}
                        <button
                          onClick={() => handleEdit(loan)}
                          className="text-indigo-600 hover:text-indigo-900"
                          title={t('edit')}
                        >
                          <FaEdit />
                        </button>
                        <button
                          onClick={() => handleDelete(loan.id)}
                          className="text-red-600 hover:text-red-900"
                          title={t('delete')}
                        >
                          <FaTrash />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
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
              status: 'PENDING'
            });
          }}
          title={currentLoan ? t('editLoan') : t('newLoanApplication')}
        >
          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-700">{t('member')}</label>
              <select
                name="memberId"
                value={formData.memberId}
                onChange={handleInputChange}
                required
                className="mt-1 block w-full pl-3 pr-10 py-2 text-base border-gray-300 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm rounded-md"
                disabled={isLoading}
              >
                <option value="">{t('selectMember')}</option>
                <option value="1">John Doe - MEM001</option>
                <option value="2">Jane Smith - MEM002</option>
                <option value="3">Alice Johnson - MEM003</option>
                <option value="4">David Wilson - MEM004</option>
              </select>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-gray-700">{t('amount')} ($)</label>
                <input
                  type="number"
                  name="amount"
                  value={formData.amount}
                  onChange={handleInputChange}
                  required
                  min="1"
                  className="mt-1 block w-full border-gray-300 rounded-md shadow-sm focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm px-3 py-2 border"
                  disabled={isLoading}
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700">{t('interestRate')} (%)</label>
                <input
                  type="number"
                  name="interestRate"
                  value={formData.interestRate}
                  onChange={handleInputChange}
                  required
                  min="0"
                  max="100"
                  step="0.1"
                  className="mt-1 block w-full border-gray-300 rounded-md shadow-sm focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm px-3 py-2 border"
                  disabled={isLoading}
                />
              </div>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700">{t('term')} ({t('months')})</label>
              <input
                type="number"
                name="term"
                value={formData.term}
                onChange={handleInputChange}
                required
                min="1"
                className="mt-1 block w-full border-gray-300 rounded-md shadow-sm focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm px-3 py-2 border"
                disabled={isLoading}
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700">{t('purpose')}</label>
              <textarea
                name="purpose"
                value={formData.purpose}
                onChange={handleInputChange}
                required
                rows={3}
                className="mt-1 block w-full border-gray-300 rounded-md shadow-sm focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm px-3 py-2 border"
                disabled={isLoading}
                placeholder={t('loanPurposePlaceholder')}
              />
            </div>

            {currentLoan && (
              <div>
                <label className="block text-sm font-medium text-gray-700">{t('status')}</label>
                <select
                  name="status"
                  value={formData.status}
                  onChange={handleInputChange}
                  className="mt-1 block w-full pl-3 pr-10 py-2 text-base border-gray-300 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm rounded-md"
                  disabled={isLoading}
                >
                  <option value="PENDING">{t('pending')}</option>
                  <option value="APPROVED">{t('approved')}</option>
                  <option value="REJECTED">{t('rejected')}</option>
                </select>
              </div>
            )}

            <div className="flex justify-end space-x-3 pt-4">
              <button
                type="button"
                onClick={() => {
                  setIsModalOpen(false);
                  setCurrentLoan(null);
                }}
                disabled={isLoading}
                className="bg-white py-2 px-4 border border-gray-300 rounded-md shadow-sm text-sm font-medium text-gray-700 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 disabled:opacity-50"
              >
                {t('cancel')}
              </button>
              <button
                type="submit"
                disabled={isLoading}
                className="inline-flex justify-center py-2 px-4 border border-transparent shadow-sm text-sm font-medium rounded-md text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 disabled:opacity-50"
              >
                {isLoading ? t('processing') : (currentLoan ? t('updateLoan') : t('submitApplication'))}
              </button>
            </div>
          </form>
        </Modal>
      </div>
    </div>
  );
};

export default LoansPage;