// API Integration Test Utility
import { useLoginMutation, useGetAllGroupsQuery, useGetSavingsQuery } from '../app/api/apiSlice';

export const testBackendIntegration = async () => {
  console.log('Testing Backend Integration...');
  
  // Test 1: Check if backend is reachable
  try {
    const response = await fetch('/api/health', {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
      },
    });
    
    if (response.ok) {
      console.log('✅ Backend is reachable');
    } else {
      console.log('⚠️ Backend health check failed');
    }
  } catch (error) {
    console.log('❌ Cannot reach backend:', error.message);
  }
  
  // Test 2: Test authentication endpoint
  try {
    const loginResponse = await fetch('/api/auth/login', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        email: 'test@example.com',
        password: 'password',
        savingsGroupId: 1
      }),
    });
    
    if (loginResponse.status === 401) {
      console.log('✅ Auth endpoint is working (401 expected for invalid credentials)');
    } else if (loginResponse.ok) {
      const data = await loginResponse.json();
      console.log('✅ Auth endpoint working, received token');
    } else {
      console.log('⚠️ Unexpected auth response:', loginResponse.status);
    }
  } catch (error) {
    console.log('❌ Auth endpoint error:', error.message);
  }
  
  // Test 3: Test groups endpoint
  try {
    const groupsResponse = await fetch('/api/savings-groups', {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
      },
    });
    
    if (groupsResponse.ok) {
      const groups = await groupsResponse.json();
      console.log('✅ Groups endpoint working, found', groups.length, 'groups');
    } else if (groupsResponse.status === 401) {
      console.log('✅ Groups endpoint working (401 - authentication required)');
    } else {
      console.log('⚠️ Groups endpoint unexpected response:', groupsResponse.status);
    }
  } catch (error) {
    console.log('❌ Groups endpoint error:', error.message);
  }
  
  console.log('Backend Integration Test Complete!');
};

// Export a component to test from the browser console
export const ApiTestComponent = () => {
  return (
    <button 
      onClick={testBackendIntegration}
      style={{ position: 'fixed', bottom: 20, right: 20, zIndex: 9999 }}
    >
      Test API Integration
    </button>
  );
};
