import React, { createContext, useContext, useState, useEffect } from 'react';
import toast from 'react-hot-toast';

// Translations
const translations = {
  en: {
    // Dashboard
    dashboard: 'Dashboard',
    overview: 'Overview',
    totalMembers: 'Total Members',
    activeLoans: 'Active Loans',
    pendingRequests: 'Pending Requests',
    monthlySavings: 'Monthly Savings',
    totalSavings: 'Total Savings',
    totalAmount: 'Total Amount',
    recentActivity: 'Recent Activity',
    newLoanRequest: 'New Loan Request',
    newMemberRegistration: 'New Member Registration',
    savingsDeposit: 'Savings Deposit',
    loanRepayment: 'Loan Repayment',
    viewAll: 'View All',
    welcome: 'Welcome',
    dashboardWelcomeMessage: 'Here\'s an overview of your savings and loan status',
    currentLoan: 'Current Loan',
    monthlyContribution: 'Monthly Contribution',
    totalContributions: 'Total Contributions',
    thisYear: 'this year',
    nextDue: 'Next Due',
    quickActions: 'Quick Actions',
    makeDeposit: 'Make Deposit',
    applyLoan: 'Apply for Loan',
    viewSavings: 'View Savings',
    updateProfile: 'Update Profile',
    recentTransactions: 'Recent Transactions',
    upcomingActivities: 'Upcoming Activities',
    viewAllTransactions: 'View all transactions',
    viewAllActivities: 'View all activities',
    due: 'Due',
    dashboardWelcome: 'Welcome back! Here\'s what\'s happening with your Ikimina group.',
    latestActivities: 'Latest actions from group members',
    upcomingPayments: 'Upcoming Payments',
    scheduledPayments: 'Scheduled payments for the next 7 days',
    recordPayment: 'Record Payment',
    newLoan: 'New Loan',
    addMember: 'Add Member',
    generateReport: 'Generate Report',
    tomorrow: 'Tomorrow',
    in2Days: 'In 2 days',
    in3Days: 'In 3 days',
    updatedProfile: 'updated profile information',
    
    // Navigation
    dashboardNav: 'Dashboard',
    members: 'Members',
    loans: 'Loans',
    savings: 'Savings',
    reports: 'Reports',
    settings: 'Settings',
    logout: 'Logout',
    
    // General
    ikimina: 'Ikimina',
    search: 'Search',
    filter: 'Filter',
    export: 'Export',
    add: 'Add',
    edit: 'Edit',
    delete: 'Delete',
    save: 'Save',
    cancel: 'Cancel',
    confirm: 'Confirm',
    loading: 'Loading...',
    noData: 'No data available',
    success: 'Success',
    error: 'Error',
    warning: 'Warning',
    info: 'Info',
    
    // Forms
    firstName: 'First Name',
    lastName: 'Last Name',
    email: 'Email',
    phone: 'Phone',
    address: 'Address',
    dateOfBirth: 'Date of Birth',
    joinDate: 'Join Date',
    memberNumber: 'Member Number',
    amount: 'Amount',
    description: 'Description',
    status: 'Status',
    date: 'Date',
    type: 'Type',
    
    // Status
    active: 'Active',
    inactive: 'Inactive',
    pending: 'Pending',
    approved: 'Approved',
    rejected: 'Rejected',
    completed: 'Completed',
    
    // Settings
    general: 'General',
    profile: 'Profile',
    notifications: 'Notifications',
    security: 'Security',
    appearance: 'Appearance',
    languageRegion: 'Language & Region',
    generalSettings: 'General Settings',
    profileSettings: 'Profile Settings',
    notificationSettings: 'Notification Settings',
    securitySettings: 'Security Settings',
    appearanceSettings: 'Appearance Settings',
    languageSettings: 'Language & Region Settings',
    applicationName: 'Application Name',
    defaultCurrency: 'Default Currency',
    timeZone: 'Time Zone',
    fullName: 'Full Name',
    bio: 'Bio',
    emailNotifications: 'Email Notifications',
    pushNotifications: 'Push Notifications',
    smsNotifications: 'SMS Notifications',
    currentPassword: 'Current Password',
    newPassword: 'New Password',
    confirmPassword: 'Confirm Password',
    theme: 'Theme',
    language: 'Language',
    dateFormat: 'Date Format',
    saveChanges: 'Save Changes',
    darkMode: 'Dark Mode',
    lightMode: 'Light Mode',
    system: 'System',
    english: 'English',
    french: 'Français',
    kinyarwanda: 'Ikinyarwanda',
    changesSaved: 'Changes saved successfully!',
    errorOccurred: 'An error occurred while saving changes.',
    
    // Members
    addMember: 'Add Member',
    editMember: 'Edit Member',
    memberDetails: 'Member Details',
    memberList: 'Member List',
    searchMembers: 'Search members...',
    newMember: 'New Member',
    memberInfo: 'Member Information',
    contactInfo: 'Contact Information',
    personalInfo: 'Personal Information',
    addNewMember: 'Add New Member',
    membershipNumber: 'Membership Number',
    membersManagementPlaceholder: 'Members management functionality will be implemented here.',
    membersManagementDescription: 'This will include member registration, profile management, and membership status tracking.',
    suspended: 'Suspended',
    
    // Loans
    loans: 'Loans',
    loanList: 'Loan List',
    loansManagement: 'Loans Management',
    loansManagementDescription: 'Manage member loan applications and approvals',
    newLoanApplication: 'New Loan Application',
    totalApproved: 'Total Approved',
    interest: 'Interest',
    approve: 'Approve',
    reject: 'Reject',
    editLoan: 'Edit Loan',
    selectMember: 'Select a member...',
    loanPurposePlaceholder: 'Describe the purpose of this loan...',
    processing: 'Processing...',
    updateLoan: 'Update Loan',
    submitApplication: 'Submit Application',
    months: 'months',
    
    // Savings
    savings: 'Savings',
    savingsList: 'Savings List',
    memberSavings: 'Member Savings',
    savingsDescription: 'Record and manage member savings (Ubwizigame & Ingoboka)',
    recordSavings: 'Record Savings',
    weeklySavingsTotals: 'Weekly Savings Totals',
    exportPDF: 'Export PDF',
    exportExcel: 'Export Excel',
    member: 'Member',
    ubwizigame: 'Ubwizigame',
    ingoboka: 'Ingoboka',
    weeklyTotal: 'Weekly Total',
    noSavingsRecorded: 'No savings recorded this week',
    grandTotal: 'Grand Total',
    ubwizigameTotal: 'Ubwizigame Total',
    ingobokaTotal: 'Ingoboka Total',
    recordMemberSavings: 'Record Member Savings',
    editSaving: 'Edit Saving',
    
    // Reports
    reports: 'Reports',
    generateReport: 'Generate Report',
    dateRange: 'Date Range',
    reportType: 'Report Type',
    memberReport: 'Member Report',
    loanReport: 'Loan Report',
    savingsReport: 'Savings Report',
    financialReport: 'Financial Report',
    
    // Messages
    confirmDelete: 'Are you sure you want to delete this item?',
    confirmSave: 'Are you sure you want to save these changes?',
    itemDeleted: 'Item deleted successfully',
    itemSaved: 'Item saved successfully',
    itemUpdated: 'Item updated successfully',
    itemAdded: 'Item added successfully',
    invalidInput: 'Please check your input and try again',
    networkError: 'Network error. Please try again.',
    unauthorized: 'You are not authorized to perform this action',
  },
  fr: {
    // Dashboard
    dashboard: 'Tableau de Bord',
    overview: 'Aperçu',
    totalMembers: 'Total des Membres',
    activeLoans: 'Prêts Actifs',
    pendingRequests: 'Demandes en Attente',
    monthlySavings: 'Épargnes Mensuelles',
    totalSavings: 'Total des Épargnes',
    totalAmount: 'Montant Total',
    recentActivity: 'Activité Récente',
    newLoanRequest: 'Nouvelle Demande de Prêt',
    newMemberRegistration: 'Nouvelle Inscription de Membre',
    savingsDeposit: 'Dépôt d\'Épargne',
    loanRepayment: 'Remboursement de Prêt',
    viewAll: 'Voir Tout',
    welcome: 'Bienvenue',
    dashboardWelcomeMessage: 'Voici un aperçu de vos épargnes et de votre statut de prêt',
    currentLoan: 'Prêt Actuel',
    monthlyContribution: 'Contribution Mensuelle',
    totalContributions: 'Total des Contributions',
    thisYear: 'cette année',
    nextDue: 'Prochaine Échéance',
    quickActions: 'Actions Rapides',
    makeDeposit: 'Faire un Dépôt',
    applyLoan: 'Demander un Prêt',
    viewSavings: 'Voir les Épargnes',
    updateProfile: 'Mettre à Jour le Profil',
    recentTransactions: 'Transactions Récentes',
    upcomingActivities: 'Activités à Venir',
    viewAllTransactions: 'Voir toutes les transactions',
    viewAllActivities: 'Voir toutes les activités',
    due: 'Échéance',
    dashboardWelcome: 'Bon retour! Voici ce qui se passe dans votre groupe Ikimina.',
    latestActivities: 'Dernières actions des membres du groupe',
    upcomingPayments: 'Paiements à Venir',
    scheduledPayments: 'Paiements prévus pour les 7 prochains jours',
    recordPayment: 'Enregistrer un Paiement',
    newLoan: 'Nouveau Prêt',
    addMember: 'Ajouter un Membre',
    generateReport: 'Générer un Rapport',
    tomorrow: 'Demain',
    in2Days: 'Dans 2 jours',
    in3Days: 'Dans 3 jours',
    updatedProfile: 'mis à jour les informations du profil',
    
    // Navigation
    dashboardNav: 'Tableau de Bord',
    members: 'Membres',
    loans: 'Prêts',
    savings: 'Épargnes',
    reports: 'Rapports',
    settings: 'Paramètres',
    logout: 'Déconnexion',
    
    // General
    ikimina: 'Ikimina',
    search: 'Rechercher',
    filter: 'Filtrer',
    export: 'Exporter',
    add: 'Ajouter',
    edit: 'Modifier',
    delete: 'Supprimer',
    save: 'Enregistrer',
    cancel: 'Annuler',
    confirm: 'Confirmer',
    loading: 'Chargement...',
    noData: 'Aucune donnée disponible',
    success: 'Succès',
    error: 'Erreur',
    warning: 'Avertissement',
    info: 'Information',
    
    // Forms
    firstName: 'Prénom',
    lastName: 'Nom',
    email: 'Email',
    phone: 'Téléphone',
    address: 'Adresse',
    dateOfBirth: 'Date de Naissance',
    joinDate: 'Date d\'Adhésion',
    memberNumber: 'Numéro de Membre',
    amount: 'Montant',
    description: 'Description',
    status: 'Statut',
    date: 'Date',
    type: 'Type',
    
    // Status
    active: 'Actif',
    inactive: 'Inactif',
    pending: 'En Attente',
    approved: 'Approuvé',
    rejected: 'Rejeté',
    completed: 'Terminé',
    
    // Settings
    general: 'Général',
    profile: 'Profil',
    notifications: 'Notifications',
    security: 'Sécurité',
    appearance: 'Apparence',
    languageRegion: 'Langue et Région',
    generalSettings: 'Paramètres Généraux',
    profileSettings: 'Paramètres du Profil',
    notificationSettings: 'Paramètres de Notification',
    securitySettings: 'Paramètres de Sécurité',
    appearanceSettings: 'Paramètres d\'Apparence',
    languageSettings: 'Paramètres de Langue et Région',
    applicationName: 'Nom de l\'Application',
    defaultCurrency: 'Devise par Défaut',
    timeZone: 'Fuseau Horaire',
    fullName: 'Nom Complet',
    bio: 'Biographie',
    emailNotifications: 'Notifications Email',
    pushNotifications: 'Notifications Push',
    smsNotifications: 'Notifications SMS',
    currentPassword: 'Mot de Passe Actuel',
    newPassword: 'Nouveau Mot de Passe',
    confirmPassword: 'Confirmer le Mot de Passe',
    theme: 'Thème',
    language: 'Langue',
    dateFormat: 'Format de Date',
    saveChanges: 'Sauvegarder les Changements',
    darkMode: 'Mode Sombre',
    lightMode: 'Mode Clair',
    system: 'Système',
    english: 'Anglais',
    french: 'Français',
    kinyarwanda: 'Kinyarwanda',
    changesSaved: 'Changements sauvegardés avec succès!',
    errorOccurred: 'Une erreur s\'est produite lors de la sauvegarde des changements.',
    
    // Members
    addMember: 'Ajouter un Membre',
    editMember: 'Modifier le Membre',
    memberDetails: 'Détails du Membre',
    memberList: 'Liste des Membres',
    searchMembers: 'Rechercher des membres...',
    newMember: 'Nouveau Membre',
    memberInfo: 'Information du Membre',
    contactInfo: 'Information de Contact',
    personalInfo: 'Information Personnelle',
    addNewMember: 'Ajouter un Nouveau Membre',
    membershipNumber: 'Numéro d\'Adhésion',
    membersManagementPlaceholder: 'La fonctionnalité de gestion des membres sera implémentée ici.',
    membersManagementDescription: 'Cela inclura l\'inscription des membres, la gestion des profils et le suivi du statut d\'adhésion.',
    suspended: 'Suspendu',
    
    // Loans
    loans: 'Prêts',
    loanList: 'Liste des Prêts',
    loansManagement: 'Gestion des Prêts',
    loansManagementDescription: 'Gérer les demandes de prêt des membres et les approbations',
    newLoanApplication: 'Nouvelle Demande de Prêt',
    totalApproved: 'Total Approuvé',
    interest: 'Intérêt',
    approve: 'Approuver',
    reject: 'Rejeter',
    editLoan: 'Modifier le Prêt',
    selectMember: 'Sélectionner un membre...',
    loanPurposePlaceholder: 'Décrivez le but de ce prêt...',
    processing: 'Traitement...',
    updateLoan: 'Mettre à Jour le Prêt',
    submitApplication: 'Soumettre la Demande',
    months: 'mois',
    
    // Savings
    savings: 'Épargnes',
    savingsList: 'Liste des Épargnes',
    memberSavings: 'Épargnes des Membres',
    savingsDescription: 'Enregistrer et gérer les épargnes des membres (Ubwizigame & Ingoboka)',
    recordSavings: 'Enregistrer les Épargnes',
    weeklySavingsTotals: 'Totaux des Épargnes Hebdomadaires',
    exportPDF: 'Exporter PDF',
    exportExcel: 'Exporter Excel',
    member: 'Membre',
    ubwizigame: 'Ubwizigame',
    ingoboka: 'Ingoboka',
    weeklyTotal: 'Total Hebdomadaire',
    noSavingsRecorded: 'Aucune épargne enregistrée cette semaine',
    grandTotal: 'Grand Total',
    ubwizigameTotal: 'Total Ubwizigame',
    ingobokaTotal: 'Total Ingoboka',
    recordMemberSavings: 'Enregistrer les Épargnes du Membre',
    editSaving: 'Modifier l\'Épargne',
    
    // Reports
    reports: 'Rapports',
    generateReport: 'Générer un Rapport',
    dateRange: 'Période de Dates',
    reportType: 'Type de Rapport',
    memberReport: 'Rapport de Membre',
    loanReport: 'Rapport de Prêt',
    savingsReport: 'Rapport d\'Épargne',
    financialReport: 'Rapport Financier',
    
    // Messages
    confirmDelete: 'Êtes-vous sûr de vouloir supprimer cet élément?',
    confirmSave: 'Êtes-vous sûr de vouloir sauvegarder ces changements?',
    itemDeleted: 'Élément supprimé avec succès',
    itemSaved: 'Élément sauvegardé avec succès',
    itemUpdated: 'Élément mis à jour avec succès',
    itemAdded: 'Élément ajouté avec succès',
    invalidInput: 'Veuillez vérifier votre saisie et réessayer',
    networkError: 'Erreur réseau. Veuillez réessayer.',
    unauthorized: 'Vous n\'êtes pas autorisé à effectuer cette action',
  },
  rw: {
    // Dashboard
    dashboard: 'Ibirimo',
    overview: 'Incamake',
    totalMembers: 'Abanyamuryango Bose',
    activeLoans: 'Amashyirizamahano Akora',
    pendingRequests: 'Ibisabwa Biri Gutekerezwa',
    monthlySavings: 'Ukwishyura Ukwezi',
    totalSavings: 'Ukwishyura Bwose',
    totalAmount: 'Igiteranyo Cyose',
    recentActivity: 'Ibikorwa Byashize',
    newLoanRequest: 'Ibisabwa Ryishye N\'amashyirizamahano',
    newMemberRegistration: 'Iyinjizwa ry\'umunyamuryango mushya',
    savingsDeposit: 'Kubika amafaranga',
    loanRepayment: 'Kurinda amashyirizamahano',
    viewAll: 'Kureba Byose',
    welcome: 'Murakaza neza',
    dashboardWelcomeMessage: 'Aha urerekana imbonano z\'uwishyura n\'imiterere y\'amashyirizamahano',
    currentLoan: 'Shyirizamahano Shikira',
    monthlyContribution: 'Ukwishyura Buri Kwezi',
    totalContributions: 'Igiteranyo Cy\'Ukwishyura',
    thisYear: 'umwaka',
    nextDue: 'Igihe Gikurikira',
    quickActions: 'Igikorwa Cyihuse',
    makeDeposit: 'Kwishyura',
    applyLoan: 'Gusaba Shyirizamahano',
    viewSavings: 'Kureba Ukwishyura',
    updateProfile: 'Kuvugurura Ibisobanuro',
    recentTransactions: 'Amatangazanyanja Ashize',
    upcomingActivities: 'Ibikorwa Bizaza',
    viewAllTransactions: 'Kureba amatangazanyanja yose',
    viewAllActivities: 'Kureba ibikorwa byose',
    due: 'Igihe',
    dashboardWelcome: 'Murakaza neza! Aha urerekana ibikorerwa mu itsinda ry\'Ikimina.',
    latestActivities: 'Ibikorwa byashize by\'abanyamuryango',
    upcomingPayments: 'Amashyurwa Azaza',
    scheduledPayments: 'Amashyurwa y\'ibyumweru 7 iri imbere',
    recordPayment: 'Kwerekana amafaranga yashyizwe',
    newLoan: 'Shyirizamahano Shishya',
    addMember: 'Kongera Umunyamuryango',
    generateReport: 'Kohereza Raporo',
    tomorrow: 'Ejo',
    in2Days: 'Imezi 2',
    in3Days: 'Imezi 3',
    updatedProfile: 'avuguruye ibisobanuro bya profayili',
    
    // Navigation
    dashboardNav: 'Ibirimo',
    members: 'Abanyamuryango',
    loans: 'Amashyirizamahano',
    savings: 'Ukwishyura',
    reports: 'Raporo',
    settings: 'Igenamiriro',
    logout: 'Gusohoka',
    
    // General
    ikimina: 'Ikimina',
    search: 'Gushaka',
    filter: 'Kunganya',
    export: 'Kohereza',
    add: 'Ongeraho',
    edit: 'Guhindura',
    delete: 'Siba',
    save: 'Bika',
    cancel: 'Kureka',
    confirm: 'Emeza',
    loading: 'Itangira...',
    noData: 'Nta data iboneka',
    success: 'Ibyashize',
    error: 'Ikosa',
    warning: 'Iburira',
    info: 'Ibisobanuro',
    
    // Forms
    firstName: 'Izina Rirangwa',
    lastName: 'Izina Nyakuri',
    email: 'Imeri',
    phone: 'Telefoni',
    address: 'Aderesi',
    dateOfBirth: 'Tariki yavutse',
    joinDate: 'Tariki yinjiye',
    memberNumber: 'Umubare w\'umunyamuryango',
    amount: 'Igiteranyo',
    description: 'Ubusobanuro',
    status: 'Imimerere',
    date: 'Tariki',
    type: 'Ubwoko',
    
    // Status
    active: 'Akora',
    inactive: 'Ntiyikora',
    pending: 'Itegurwa',
    approved: 'Byemewe',
    rejected: 'Byakiriwe',
    completed: 'Byarangiye',
    
    // Settings
    general: 'Rusange',
    profile: 'Profayili',
    notifications: 'Tangaza',
    security: 'Umutekano',
    appearance: 'Imigaragarire',
    languageRegion: 'Ururimi n\'Igihugu',
    generalSettings: 'Igenamiriro Rusange',
    profileSettings: 'Igenamiriro ya Profayili',
    notificationSettings: 'Igenamiriro yo Gutangaza',
    securitySettings: 'Igenamiriro y\'Umutekano',
    appearanceSettings: 'Igenamiriro y\'Imigaragarire',
    languageSettings: 'Igenamiriro y\'Ururimi n\'Igihugu',
    applicationName: 'Izina ry\'Aporogarimu',
    defaultCurrency: 'Imifaranga y\'Ibanze',
    timeZone: 'Igihe cya Wati',
    fullName: 'Amazina Yose',
    bio: 'Ubufasha',
    emailNotifications: 'Amatangazo yo ku Imeri',
    pushNotifications: 'Amatangazo ya Push',
    smsNotifications: 'Amatangazo ya SMS',
    currentPassword: 'Ijambo ry\'Banga Rihari',
    newPassword: 'Ijambo ry\'Banga Rishya',
    confirmPassword: 'Emeza Ijambo ry\'Banga',
    theme: 'Insanganyamatsiko',
    language: 'Ururimi',
    dateFormat: 'Imiterere y\'Itariki',
    saveChanges: 'Tandukanya Amahinduka',
    darkMode: 'Igitondo gisa',
    lightMode: 'Igitondo cyiza',
    system: 'Sisitemu',
    english: 'Icyongereza',
    french: 'Igifaransa',
    kinyarwanda: 'Ikinyarwanda',
    changesSaved: 'Amahinduka atandukanijwe neza!',
    errorOccurred: 'Habayehwo ikibazo mu guhindura amahinduka.',
    
    // Members
    addMember: 'Ongeraho Umunyamuryango',
    editMember: 'Guhindura Umunyamuryango',
    memberDetails: 'Ibisobanuro by\'umunyamuryango',
    memberList: 'Urutonde rw\'abanyamuryango',
    searchMembers: 'Shaka abanyamuryango...',
    newMember: 'Umunyamuryango mushya',
    memberInfo: 'Ibisobanuro by\'umunyamuryango',
    contactInfo: 'Ibisobanuro by\'ubwandiko',
    personalInfo: 'Ibisobanuro by\'iby\'umwuga',
    addNewMember: 'Ongeraho Umunyamuryango Mushya',
    membershipNumber: 'Umubare w\'ubunyamuryango',
    membersManagementPlaceholder: 'Ugenamiganya w\'abanyamuryango uzakorwa hano.',
    membersManagementDescription: 'Ibi bizakoramo iyinjizwa ry\'abanyamuryango, igenamiganya rya profayili no kurikirana imimerere y\'ubunyamuryango.',
    suspended: 'Yahagaritswe',
    
    // Loans
    loans: 'Amashyirizamahano',
    loanList: 'Urutonde rw\'amashyirizamahano',
    loansManagement: 'Ugenamiganya w\'amashyirizamahano',
    loansManagementDescription: 'Genama ibisabwa by\'amashyirizamahano by\'abanyamuryango n\'amezi',
    newLoanApplication: 'Ibisabwa by\'amashyirizamahano byishye',
    totalApproved: 'Byemwe byose',
    interest: 'Inyungu',
    approve: 'Kwemera',
    reject: 'Kureka',
    editLoan: 'Guhindura amashyirizamahano',
    selectMember: 'Hitamo umunyamuryango...',
    loanPurposePlaceholder: 'Kugaragaza inshingano za iyi shyirizamahano...',
    processing: 'Ibyakozwa...',
    updateLoan: 'Kuvugurura shyirizamahano',
    submitApplication: 'Tanga ibisabwa',
    months: 'amezi',
    
    // Savings
    savings: 'Ukwishyura',
    savingsList: 'Urutonde rw\'ibyashyizweho',
    memberSavings: 'Ukwishyura kw\'abanyamuryango',
    savingsDescription: 'Kwerekana no kugeneka ubwishyu bw\'abanyamuryango (Ubwizigame & Ingoboka)',
    recordSavings: 'Kwerekana ubwishyu',
    weeklySavingsTotals: 'Igiteranyo cy\'ubwishyu bw\'icyumweru',
    exportPDF: 'Kohereza PDF',
    exportExcel: 'Kohereza Excel',
    member: 'Umunyamuryango',
    ubwizigame: 'Ubwizigame',
    ingoboka: 'Ingoboka',
    weeklyTotal: 'Igiteranyo cy\'icyumweru',
    noSavingsRecorded: 'Nta bwishyu bwanditse muri uyu kumwe',
    grandTotal: 'Igiteranyo cyose',
    ubwizigameTotal: 'Igiteranyo cya Ubwizigame',
    ingobokaTotal: 'Igiteranyo cya Ingoboka',
    recordMemberSavings: 'Kwerekana ubwishyu bw\'umunyamuryango',
    editSaving: 'Guhindura ubwishyu',
    
    // Reports
    reports: 'Raporo',
    generateReport: 'Kohereza raporo',
    dateRange: 'Intara y\'amatariki',
    reportType: 'Ubwoko bw\'urupapuro',
    memberReport: 'Urupapuro rw\'umunyamuryango',
    loanReport: 'Urupapuro rw\'amashyirizamahano',
    savingsReport: 'Urupapuro rw\'ibyashyizweho',
    financialReport: 'Urupapuro rw\'imari',
    
    // Messages
    confirmDelete: 'Wize ko ushaka gusiba iki kintu?',
    confirmSave: 'Wize ko ushaka kubika aya mahinduka?',
    itemDeleted: 'Ikintu cyasibwe neza',
    itemSaved: 'Ikintu cyabitswe neza',
    itemUpdated: 'Ikintu cyahinduwe neza',
    itemAdded: 'Ikintu cyongerewe neza',
    invalidInput: 'Kugenzura ibyo winjiye kongera ukugerageze',
    networkError: 'Ikosa ry\'urusobe. Kongera ukugerageze.',
    unauthorized: 'Nta burenganzira ufite gukora iki gikorwa',
  }
};

// Create context
const AppContext = createContext();

// Custom hook to use the context
export const useAppContext = () => {
  const context = useContext(AppContext);
  if (!context) {
    throw new Error('useAppContext must be used within an AppProvider');
  }
  return context;
};

// Provider component
export const AppProvider = ({ children }) => {
  // Initialize state from localStorage or defaults
  const [language, setLanguage] = useState(() => {
    return localStorage.getItem('language') || 'en';
  });
  
  const [theme, setTheme] = useState(() => {
    const savedTheme = localStorage.getItem('theme');
    if (savedTheme) {
      return savedTheme;
    }
    // If no saved theme, check system preference
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  });

  // Apply theme to document whenever it changes
  useEffect(() => {
    const root = document.documentElement;
    
    // Remove existing theme classes
    root.classList.remove('light', 'dark');
    
    // Apply new theme
    if (theme === 'system') {
      // Check system preference
      const systemTheme = window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
      root.classList.add(systemTheme);
      console.log('Applied system theme:', systemTheme);
    } else {
      root.classList.add(theme);
      console.log('Applied theme:', theme);
    }
    
    // Save to localStorage
    localStorage.setItem('theme', theme);
    
    // Update meta theme-color for mobile browsers
    const metaTheme = document.querySelector('meta[name="theme-color"]');
    if (metaTheme) {
      const actualTheme = theme === 'system' 
        ? (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light')
        : theme;
      metaTheme.content = actualTheme === 'dark' ? '#1f2937' : '#ffffff';
    }
  }, [theme]);

  // Listen for system theme changes when using system theme
  useEffect(() => {
    if (theme !== 'system') return;

    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
    const handleChange = (e) => {
      const root = document.documentElement;
      root.classList.remove('light', 'dark');
      root.classList.add(e.matches ? 'dark' : 'light');
      
      // Update meta theme-color
      const metaTheme = document.querySelector('meta[name="theme-color"]');
      if (metaTheme) {
        metaTheme.content = e.matches ? '#1f2937' : '#ffffff';
      }
    };

    mediaQuery.addEventListener('change', handleChange);
    return () => mediaQuery.removeEventListener('change', handleChange);
  }, [theme]);

  // Apply language to document whenever it changes
  useEffect(() => {
    // Save to localStorage
    localStorage.setItem('language', language);
    
    // Set document lang attribute
    document.documentElement.lang = language;
    
    // Update document title if needed
    const title = document.querySelector('title');
    if (title) {
        const titles = {
          en: 'Ikimina Management System',
          fr: 'Système de Gestion Ikimina',
          rw: 'Sisitemu y\'Ubugenzuzi Ikimina'
        };
      title.textContent = titles[language] || titles.en;
    }
  }, [language]);

  // Get translation function
  const t = (key) => {
    const keys = key.split('.');
    let value = translations[language];
    
    for (const k of keys) {
      value = value?.[k];
    }
    
    return value || key;
  };

  // Change language function
  const changeLanguage = (newLanguage) => {
    setLanguage(newLanguage);
    toast.success(t('changesSaved'));
  };

  // Change theme function
  const changeTheme = (newTheme) => {
    setTheme(newTheme);
    toast.success(t('changesSaved'));
  };

  // Value object to be provided to consumers
  const value = {
    language,
    theme,
    t,
    changeLanguage,
    changeTheme,
    translations
  };

  return (
    <AppContext.Provider value={value}>
      {children}
    </AppContext.Provider>
  );
};

export default AppContext;
