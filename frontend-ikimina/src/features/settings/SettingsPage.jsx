import React, { useState, useEffect } from 'react';
import { FaCog, FaUser, FaBell, FaLock, FaPalette, FaGlobe, FaSave } from 'react-icons/fa';
import toast from 'react-hot-toast';
import { useAppContext } from '../../contexts/AppContext';

const SettingsPage = () => {
  const { language, theme, t, changeLanguage, changeTheme } = useAppContext();
  const [activeTab, setActiveTab] = useState('general');
  const [isSaving, setIsSaving] = useState(false);

  // Form states
  const [generalForm, setGeneralForm] = useState({
    appName: 'Ikimina Savings Manager',
    currency: 'USD',
    timeZone: 'Africa/Kigali',
  });

  const [profileForm, setProfileForm] = useState({
    fullName: 'John Doe',
    email: 'john.doe@example.com',
    phone: '+250 788 123 456',
    bio: 'Savings manager at Ikimina Management System',
  });

  const [notificationForm, setNotificationForm] = useState({
    email: true,
    push: false,
    sms: true,
  });

  const [securityForm, setSecurityForm] = useState({
    currentPassword: '',
    newPassword: '',
    confirmPassword: '',
  });

  const [appearanceForm, setAppearanceForm] = useState({
    theme: theme || 'light',
  });

  // Update appearance form when global theme changes
  useEffect(() => {
    setAppearanceForm(prev => ({ ...prev, theme: theme || 'light' }));
  }, [theme]);

  const [languageForm, setLanguageForm] = useState({
    language: language || 'en',
    dateFormat: 'MM/DD/YYYY',
  });

  const handleSave = async section => {
    setIsSaving(true);
    try {
      // Simulate API call
      await new Promise(resolve => setTimeout(resolve, 1000));

      if (section === 'language') {
        changeLanguage(languageForm.language);
      }

      if (section === 'appearance') {
        changeTheme(appearanceForm.theme);
      }

      toast.success(t('changesSaved'));
    } catch (error) {
      toast.error(t('errorOccurred'));
    } finally {
      setIsSaving(false);
    }
  };

  const renderContent = () => {
    switch (activeTab) {
      case 'general':
        return (
          <div className='bg-white rounded-lg shadow'>
            <div className='px-4 py-5 sm:p-6'>
              <h3 className='text-lg leading-6 font-medium text-gray-900 mb-4'>
                {t('generalSettings')}
              </h3>

              <div className='space-y-6'>
                <div>
                  <label className='block text-sm font-medium text-gray-700'>
                    {t('applicationName')}
                  </label>
                  <input
                    type='text'
                    className='mt-1 block w-full border-gray-300 rounded-md shadow-sm focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm px-3 py-2 border'
                    value={generalForm.appName}
                    onChange={e => setGeneralForm({ ...generalForm, appName: e.target.value })}
                  />
                </div>

                <div>
                  <label className='block text-sm font-medium text-gray-700'>
                    {t('defaultCurrency')}
                  </label>
                  <select
                    className='mt-1 block w-full border-gray-300 rounded-md shadow-sm focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm px-3 py-2 border'
                    value={generalForm.currency}
                    onChange={e => setGeneralForm({ ...generalForm, currency: e.target.value })}
                  >
                    <option value='USD'>USD - US Dollar</option>
                    <option value='RWF'>RWF - Rwandan Franc</option>
                    <option value='EUR'>EUR - Euro</option>
                  </select>
                </div>

                <div>
                  <label className='block text-sm font-medium text-gray-700'>{t('timeZone')}</label>
                  <select
                    className='mt-1 block w-full border-gray-300 rounded-md shadow-sm focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm px-3 py-2 border'
                    value={generalForm.timeZone}
                    onChange={e => setGeneralForm({ ...generalForm, timeZone: e.target.value })}
                  >
                    <option value='Africa/Kigali'>Africa/Kigali</option>
                    <option value='UTC'>UTC</option>
                    <option value='GMT'>GMT</option>
                  </select>
                </div>

                <div className='pt-4 flex space-x-3'>
                  <button
                    onClick={() => handleSave('general')}
                    disabled={isSaving}
                    className='bg-indigo-600 py-2 px-4 border border-transparent rounded-md shadow-sm text-sm font-medium text-white hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 disabled:opacity-50'
                  >
                    {isSaving ? (
                      <div className='flex items-center'>
                        <svg
                          className='animate-spin -ml-1 mr-2 h-4 w-4 text-white'
                          xmlns='http://www.w3.org/2000/svg'
                          fill='none'
                          viewBox='0 0 24 24'
                        >
                          <circle
                            className='opacity-25'
                            cx='12'
                            cy='12'
                            r='10'
                            stroke='currentColor'
                            strokeWidth='4'
                          ></circle>
                          <path
                            className='opacity-75'
                            fill='currentColor'
                            d='M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z'
                          ></path>
                        </svg>
                        Saving...
                      </div>
                    ) : (
                      <span className='flex items-center'>
                        <FaSave className='mr-2' />
                        {t('saveChanges')}
                      </span>
                    )}
                  </button>
                </div>
              </div>
            </div>
          </div>
        );

      case 'profile':
        return (
          <div className='bg-white rounded-lg shadow'>
            <div className='px-4 py-5 sm:p-6'>
              <h3 className='text-lg leading-6 font-medium text-gray-900 mb-4'>
                {t('profileSettings')}
              </h3>

              <div className='space-y-6'>
                <div>
                  <label className='block text-sm font-medium text-gray-700'>{t('fullName')}</label>
                  <input
                    type='text'
                    className='mt-1 block w-full border-gray-300 rounded-md shadow-sm focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm px-3 py-2 border'
                    value={profileForm.fullName}
                    onChange={e => setProfileForm({ ...profileForm, fullName: e.target.value })}
                  />
                </div>

                <div>
                  <label className='block text-sm font-medium text-gray-700'>{t('email')}</label>
                  <input
                    type='email'
                    className='mt-1 block w-full border-gray-300 rounded-md shadow-sm focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm px-3 py-2 border'
                    value={profileForm.email}
                    onChange={e => setProfileForm({ ...profileForm, email: e.target.value })}
                  />
                </div>

                <div>
                  <label className='block text-sm font-medium text-gray-700'>{t('phone')}</label>
                  <input
                    type='tel'
                    className='mt-1 block w-full border-gray-300 rounded-md shadow-sm focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm px-3 py-2 border'
                    value={profileForm.phone}
                    onChange={e => setProfileForm({ ...profileForm, phone: e.target.value })}
                  />
                </div>

                <div>
                  <label className='block text-sm font-medium text-gray-700'>{t('bio')}</label>
                  <textarea
                    rows={3}
                    className='mt-1 block w-full border-gray-300 rounded-md shadow-sm focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm px-3 py-2 border'
                    value={profileForm.bio}
                    onChange={e => setProfileForm({ ...profileForm, bio: e.target.value })}
                  />
                </div>

                <div className='pt-4 flex space-x-3'>
                  <button
                    onClick={() => handleSave('profile')}
                    disabled={isSaving}
                    className='bg-indigo-600 py-2 px-4 border border-transparent rounded-md shadow-sm text-sm font-medium text-white hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 disabled:opacity-50'
                  >
                    <FaSave className='mr-2 inline' />
                    {t('saveChanges')}
                  </button>
                </div>
              </div>
            </div>
          </div>
        );

      case 'notifications':
        return (
          <div className='bg-white rounded-lg shadow'>
            <div className='px-4 py-5 sm:p-6'>
              <h3 className='text-lg leading-6 font-medium text-gray-900 mb-4'>
                {t('notificationSettings')}
              </h3>

              <div className='space-y-6'>
                <div className='flex items-center justify-between'>
                  <div>
                    <label className='text-sm font-medium text-gray-700'>
                      {t('emailNotifications')}
                    </label>
                    <p className='text-sm text-gray-500'>
                      Receive email notifications about your account
                    </p>
                  </div>
                  <input
                    type='checkbox'
                    className='h-4 w-4 text-indigo-600 focus:ring-indigo-500 border-gray-300 rounded'
                    checked={notificationForm.email}
                    onChange={e =>
                      setNotificationForm({ ...notificationForm, email: e.target.checked })
                    }
                  />
                </div>

                <div className='flex items-center justify-between'>
                  <div>
                    <label className='text-sm font-medium text-gray-700'>
                      {t('pushNotifications')}
                    </label>
                    <p className='text-sm text-gray-500'>
                      Receive push notifications in your browser
                    </p>
                  </div>
                  <input
                    type='checkbox'
                    className='h-4 w-4 text-indigo-600 focus:ring-indigo-500 border-gray-300 rounded'
                    checked={notificationForm.push}
                    onChange={e =>
                      setNotificationForm({ ...notificationForm, push: e.target.checked })
                    }
                  />
                </div>

                <div className='flex items-center justify-between'>
                  <div>
                    <label className='text-sm font-medium text-gray-700'>
                      {t('smsNotifications')}
                    </label>
                    <p className='text-sm text-gray-500'>
                      Receive SMS notifications for important updates
                    </p>
                  </div>
                  <input
                    type='checkbox'
                    className='h-4 w-4 text-indigo-600 focus:ring-indigo-500 border-gray-300 rounded'
                    checked={notificationForm.sms}
                    onChange={e =>
                      setNotificationForm({ ...notificationForm, sms: e.target.checked })
                    }
                  />
                </div>

                <div className='pt-4 flex space-x-3'>
                  <button
                    onClick={() => handleSave('notifications')}
                    disabled={isSaving}
                    className='bg-indigo-600 py-2 px-4 border border-transparent rounded-md shadow-sm text-sm font-medium text-white hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 disabled:opacity-50'
                  >
                    <FaSave className='mr-2 inline' />
                    {t('saveChanges')}
                  </button>
                </div>
              </div>
            </div>
          </div>
        );

      case 'security':
        return (
          <div className='bg-white rounded-lg shadow'>
            <div className='px-4 py-5 sm:p-6'>
              <h3 className='text-lg leading-6 font-medium text-gray-900 mb-4'>
                {t('securitySettings')}
              </h3>

              <div className='space-y-6'>
                <div>
                  <label className='block text-sm font-medium text-gray-700'>
                    {t('currentPassword')}
                  </label>
                  <input
                    type='password'
                    className='mt-1 block w-full border-gray-300 rounded-md shadow-sm focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm px-3 py-2 border'
                    value={securityForm.currentPassword}
                    onChange={e =>
                      setSecurityForm({ ...securityForm, currentPassword: e.target.value })
                    }
                  />
                </div>

                <div>
                  <label className='block text-sm font-medium text-gray-700'>
                    {t('newPassword')}
                  </label>
                  <input
                    type='password'
                    className='mt-1 block w-full border-gray-300 rounded-md shadow-sm focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm px-3 py-2 border'
                    value={securityForm.newPassword}
                    onChange={e =>
                      setSecurityForm({ ...securityForm, newPassword: e.target.value })
                    }
                  />
                </div>

                <div>
                  <label className='block text-sm font-medium text-gray-700'>
                    {t('confirmPassword')}
                  </label>
                  <input
                    type='password'
                    className='mt-1 block w-full border-gray-300 rounded-md shadow-sm focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm px-3 py-2 border'
                    value={securityForm.confirmPassword}
                    onChange={e =>
                      setSecurityForm({ ...securityForm, confirmPassword: e.target.value })
                    }
                  />
                </div>

                <div className='pt-4 flex space-x-3'>
                  <button
                    onClick={() => handleSave('security')}
                    disabled={isSaving}
                    className='bg-indigo-600 py-2 px-4 border border-transparent rounded-md shadow-sm text-sm font-medium text-white hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 disabled:opacity-50'
                  >
                    <FaSave className='mr-2 inline' />
                    {t('saveChanges')}
                  </button>
                </div>
              </div>
            </div>
          </div>
        );

      case 'appearance':
        return (
          <div className='bg-white rounded-lg shadow'>
            <div className='px-4 py-5 sm:p-6'>
              <h3 className='text-lg leading-6 font-medium text-gray-900 mb-4'>
                {t('appearanceSettings')}
              </h3>

              <div className='space-y-6'>
                <div>
                  <label className='block text-sm font-medium text-gray-700 mb-3'>
                    {t('theme')}
                  </label>
                  <div className='space-y-2'>
                    <label className='flex items-center'>
                      <input
                        type='radio'
                        name='theme'
                        value='light'
                        checked={appearanceForm.theme === 'light'}
                        onChange={e =>
                          setAppearanceForm({ ...appearanceForm, theme: e.target.value })
                        }
                        className='h-4 w-4 text-indigo-600 focus:ring-indigo-500 border-gray-300'
                      />
                      <span className='ml-2 text-sm text-gray-700'>{t('lightMode')}</span>
                    </label>
                    <label className='flex items-center'>
                      <input
                        type='radio'
                        name='theme'
                        value='dark'
                        checked={appearanceForm.theme === 'dark'}
                        onChange={e =>
                          setAppearanceForm({ ...appearanceForm, theme: e.target.value })
                        }
                        className='h-4 w-4 text-indigo-600 focus:ring-indigo-500 border-gray-300'
                      />
                      <span className='ml-2 text-sm text-gray-700'>{t('darkMode')}</span>
                    </label>
                    <label className='flex items-center'>
                      <input
                        type='radio'
                        name='theme'
                        value='system'
                        checked={appearanceForm.theme === 'system'}
                        onChange={e =>
                          setAppearanceForm({ ...appearanceForm, theme: e.target.value })
                        }
                        className='h-4 w-4 text-indigo-600 focus:ring-indigo-500 border-gray-300'
                      />
                      <span className='ml-2 text-sm text-gray-700'>{t('system')}</span>
                    </label>
                  </div>
                </div>

                <div className='pt-4 flex space-x-3'>
                  <button
                    onClick={() => handleSave('appearance')}
                    disabled={isSaving}
                    className='bg-indigo-600 py-2 px-4 border border-transparent rounded-md shadow-sm text-sm font-medium text-white hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 disabled:opacity-50'
                  >
                    <FaSave className='mr-2 inline' />
                    {t('saveChanges')}
                  </button>
                </div>
              </div>
            </div>
          </div>
        );

      case 'language':
        return (
          <div className='bg-white rounded-lg shadow'>
            <div className='px-4 py-5 sm:p-6'>
              <h3 className='text-lg leading-6 font-medium text-gray-900 mb-4'>
                {t('languageSettings')}
              </h3>

              <div className='space-y-6'>
                <div>
                  <label className='block text-sm font-medium text-gray-700'>{t('language')}</label>
                  <select
                    className='mt-1 block w-full border-gray-300 rounded-md shadow-sm focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm px-3 py-2 border'
                    value={languageForm.language}
                    onChange={e => setLanguageForm({ ...languageForm, language: e.target.value })}
                  >
                    <option value='en'>{t('english')}</option>
                    <option value='fr'>{t('french')}</option>
                    <option value='rw'>{t('kinyarwanda')}</option>
                  </select>
                  <p className='mt-2 text-sm text-gray-500'>
                    {languageForm.language === 'en' &&
                      'Select your preferred language for the interface.'}
                    {languageForm.language === 'fr' &&
                      "Sélectionnez votre langue préférée pour l'interface."}
                    {languageForm.language === 'rw' && 'Hitamo ururimi wihariye kurubuga.'}
                  </p>
                </div>

                <div>
                  <label className='block text-sm font-medium text-gray-700'>
                    {t('dateFormat')}
                  </label>
                  <select
                    className='mt-1 block w-full border-gray-300 rounded-md shadow-sm focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm px-3 py-2 border'
                    value={languageForm.dateFormat}
                    onChange={e => setLanguageForm({ ...languageForm, dateFormat: e.target.value })}
                  >
                    <option value='MM/DD/YYYY'>MM/DD/YYYY</option>
                    <option value='DD/MM/YYYY'>DD/MM/YYYY</option>
                    <option value='YYYY-MM-DD'>YYYY-MM-DD</option>
                  </select>
                </div>

                <div className='pt-4 flex space-x-3'>
                  <button
                    onClick={() => handleSave('language')}
                    disabled={isSaving}
                    className='bg-indigo-600 py-2 px-4 border border-transparent rounded-md shadow-sm text-sm font-medium text-white hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 disabled:opacity-50'
                  >
                    {isSaving ? (
                      <div className='flex items-center'>
                        <svg
                          className='animate-spin -ml-1 mr-2 h-4 w-4 text-white'
                          xmlns='http://www.w3.org/2000/svg'
                          fill='none'
                          viewBox='0 0 24 24'
                        >
                          <circle
                            className='opacity-25'
                            cx='12'
                            cy='12'
                            r='10'
                            stroke='currentColor'
                            strokeWidth='4'
                          ></circle>
                          <path
                            className='opacity-75'
                            fill='currentColor'
                            d='M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z'
                          ></path>
                        </svg>
                        {t('saveChanges')}
                      </div>
                    ) : (
                      <span className='flex items-center'>
                        <FaSave className='mr-2' />
                        {t('saveChanges')}
                      </span>
                    )}
                  </button>
                </div>
              </div>
            </div>
          </div>
        );

      default:
        return null;
    }
  };

  return (
    <div className='h-full w-full overflow-y-auto bg-gray-50 dark:bg-gray-900'>
      <div className='px-2 sm:px-4 lg:px-8 py-4 sm:py-6 lg:py-8 max-w-full xl:max-w-7xl mx-auto'>
        <h1 className='text-2xl font-bold text-gray-800 dark:text-gray-100 mb-6'>
          {t('settings')}
        </h1>

        <div className='grid grid-cols-1 lg:grid-cols-3 gap-6'>
          <div className='lg:col-span-1'>
            <nav className='space-y-1'>
              <button
                onClick={() => setActiveTab('general')}
                className={`w-full flex items-center px-4 py-2 text-sm font-medium rounded-md ${
                  activeTab === 'general'
                    ? 'bg-indigo-100 text-indigo-700'
                    : 'text-gray-700 hover:bg-gray-100'
                }`}
              >
                <FaCog className='mr-3' />
                {t('general')}
              </button>
              <button
                onClick={() => setActiveTab('profile')}
                className={`w-full flex items-center px-4 py-2 text-sm font-medium rounded-md ${
                  activeTab === 'profile'
                    ? 'bg-indigo-100 text-indigo-700'
                    : 'text-gray-700 hover:bg-gray-100'
                }`}
              >
                <FaUser className='mr-3' />
                {t('profile')}
              </button>
              <button
                onClick={() => setActiveTab('notifications')}
                className={`w-full flex items-center px-4 py-2 text-sm font-medium rounded-md ${
                  activeTab === 'notifications'
                    ? 'bg-indigo-100 text-indigo-700'
                    : 'text-gray-700 hover:bg-gray-100'
                }`}
              >
                <FaBell className='mr-3' />
                {t('notifications')}
              </button>
              <button
                onClick={() => setActiveTab('security')}
                className={`w-full flex items-center px-4 py-2 text-sm font-medium rounded-md ${
                  activeTab === 'security'
                    ? 'bg-indigo-100 text-indigo-700'
                    : 'text-gray-700 hover:bg-gray-100'
                }`}
              >
                <FaLock className='mr-3' />
                {t('security')}
              </button>
              <button
                onClick={() => setActiveTab('appearance')}
                className={`w-full flex items-center px-4 py-2 text-sm font-medium rounded-md ${
                  activeTab === 'appearance'
                    ? 'bg-indigo-100 text-indigo-700'
                    : 'text-gray-700 hover:bg-gray-100'
                }`}
              >
                <FaPalette className='mr-3' />
                {t('appearance')}
              </button>
              <button
                onClick={() => setActiveTab('language')}
                className={`w-full flex items-center px-4 py-2 text-sm font-medium rounded-md ${
                  activeTab === 'language'
                    ? 'bg-indigo-100 text-indigo-700'
                    : 'text-gray-700 hover:bg-gray-100'
                }`}
              >
                <FaGlobe className='mr-3' />
                {t('languageRegion')}
              </button>
            </nav>
          </div>

          <div className='lg:col-span-2'>{renderContent()}</div>
        </div>
      </div>
    </div>
  );
};

export default SettingsPage;
