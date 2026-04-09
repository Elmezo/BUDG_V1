/**
 * Utility functions for authentication and user session management
 */

/**
 * Get current user ID from session storage
 * @param {boolean} silent - If true, suppress warning when user ID is not found (default: true)
 * @returns {number|null} User ID or null if not found
 */
export function getCurrentUserId(silent = true) {
  try {
    const userStr = sessionStorage.getItem('currentUser');
    if (userStr) {
      const user = JSON.parse(userStr);
      if (user && (user.id || user.ID || user.userId)) {
        return user.id || user.ID || user.userId;
      }
    }
  } catch (e) {
    console.error('Error parsing user from session:', e);
  }
  
  if (!silent) {
    console.warn('⚠️ User ID not found in session storage');
  }
  return null;
}

/**
 * Get current user data from session storage
 * @returns {Object|null} User object or null if not found
 */
export function getCurrentUser() {
  try {
    const userStr = sessionStorage.getItem('currentUser');
    if (userStr) {
      return JSON.parse(userStr);
    }
  } catch (e) {
    console.error('Error parsing user from session:', e);
  }
  
  return null;
}

/**
 * Fetch current user from API and cache in session storage
 * @returns {Promise<Object>} User data
 */
export async function fetchCurrentUser() {
  try {
    const response = await fetch('/api/me', {
      method: 'GET',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json'
      }
    });
    
    if (!response.ok) {
      throw new Error('Failed to fetch user data');
    }
    
    const userData = await response.json();
    
    // Cache in session storage
    sessionStorage.setItem('currentUser', JSON.stringify(userData));
    
    return userData;
  } catch (error) {
    console.error('Error fetching current user:', error);
    throw error;
  }
}

/**
 * Get current user ID, fetching from API if not in session
 * @returns {Promise<number|null>} User ID or null if not found
 */
export async function ensureCurrentUserId() {
  // Try to get from session first (silent mode since we'll fetch from API if not found)
  let userId = getCurrentUserId(true);
  
  if (userId) {
    return userId;
  }
  
  // If not in session, fetch from API
  try {
    const user = await fetchCurrentUser();
    return user.id || user.ID || user.userId || null;
  } catch (error) {
    console.error('Failed to get user ID:', error);
    return null;
  }
}

