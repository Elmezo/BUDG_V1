/**
 * WebSocket utility functions for real-time bulk upload tracking
 */

/**
 * Create WebSocket connection with automatic reconnection
 * @param {string} url - WebSocket URL
 * @param {Object} callbacks - Callback functions
 * @param {Function} callbacks.onMessage - Called when message received
 * @param {Function} callbacks.onOpen - Called when connection opens
 * @param {Function} callbacks.onClose - Called when connection closes
 * @param {Function} callbacks.onError - Called on error
 * @returns {Object} Object with disconnect function and WebSocket instance
 */
export function createWebSocketConnection(url, callbacks) {
  let ws = null;
  let reconnectAttempt = 0;
  const maxReconnectAttempts = 5;
  let reconnectTimeout = null;
  let isIntentionallyClosed = false;
  let pingInterval = null;

  const connect = () => {
    try {
      ws = new WebSocket(url);

      ws.onopen = (event) => {
        console.log('WebSocket connected:', url);
        reconnectAttempt = 0; // Reset reconnect counter on successful connection
        
        // Start ping/pong for keep-alive
        startPingInterval();
        
        if (callbacks.onOpen) {
          callbacks.onOpen(event);
        }
      };

      ws.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data);
          
          // Handle pong response
          if (data.type === 'pong') {
            console.log('Received pong from server');
            return;
          }
          
          if (callbacks.onMessage) {
            callbacks.onMessage(data);
          }
        } catch (error) {
          console.error('Error parsing WebSocket message:', error);
          if (callbacks.onError) {
            callbacks.onError(error);
          }
        }
      };

      ws.onclose = (event) => {
        console.log('WebSocket closed:', event.code, event.reason);
        
        stopPingInterval();
        
        if (callbacks.onClose) {
          callbacks.onClose(event);
        }

        // Attempt to reconnect if not intentionally closed
        if (!isIntentionallyClosed && reconnectAttempt < maxReconnectAttempts) {
          reconnectWithBackoff();
        }
      };

      ws.onerror = (error) => {
        console.error('WebSocket error:', error);
        
        if (callbacks.onError) {
          callbacks.onError(error);
        }
      };

    } catch (error) {
      console.error('Error creating WebSocket:', error);
      if (callbacks.onError) {
        callbacks.onError(error);
      }
    }
  };

  const reconnectWithBackoff = () => {
    reconnectAttempt++;
    const delay = Math.min(1000 * Math.pow(2, reconnectAttempt), 30000); // Exponential backoff, max 30s
    
    console.log(`Reconnecting in ${delay}ms (attempt ${reconnectAttempt}/${maxReconnectAttempts})...`);
    
    reconnectTimeout = setTimeout(() => {
      if (!isIntentionallyClosed) {
        connect();
      }
    }, delay);
  };

  const startPingInterval = () => {
    // Send ping every 30 seconds to keep connection alive
    pingInterval = setInterval(() => {
      if (ws && ws.readyState === WebSocket.OPEN) {
        try {
          ws.send(JSON.stringify({ type: 'ping' }));
        } catch (error) {
          console.error('Error sending ping:', error);
        }
      }
    }, 30000);
  };

  const stopPingInterval = () => {
    if (pingInterval) {
      clearInterval(pingInterval);
      pingInterval = null;
    }
  };

  const disconnect = () => {
    isIntentionallyClosed = true;
    
    if (reconnectTimeout) {
      clearTimeout(reconnectTimeout);
      reconnectTimeout = null;
    }
    
    stopPingInterval();
    
    if (ws) {
      try {
        ws.close(1000, 'Client disconnect');
      } catch (error) {
        console.error('Error closing WebSocket:', error);
      }
      ws = null;
    }
  };

  const send = (data) => {
    if (ws && ws.readyState === WebSocket.OPEN) {
      try {
        ws.send(JSON.stringify(data));
        return true;
      } catch (error) {
        console.error('Error sending WebSocket message:', error);
        return false;
      }
    }
    return false;
  };

  const getReadyState = () => {
    return ws ? ws.readyState : WebSocket.CLOSED;
  };

  // Initial connection
  connect();

  return {
    disconnect,
    send,
    getReadyState,
    get isConnected() {
      return ws && ws.readyState === WebSocket.OPEN;
    }
  };
}

/**
 * Connect to bulk upload job WebSocket
 * @param {number} jobId - Job ID to track
 * @param {Function} onUpdate - Callback for status updates
 * @param {Function} onError - Callback for errors
 * @returns {Function} Disconnect function
 */
export function connectToBulkUploadJob(jobId, onUpdate, onError) {
  // Validate jobId
  if (!jobId || jobId === 'undefined' || jobId === 'null') {
    const error = new Error('Invalid job ID: jobId is required');
    console.error('WebSocket connection error:', error);
    if (onError) {
      onError(error);
    }
    return () => {}; // Return empty disconnect function
  }
  
  const wsUrl = `ws://${window.location.hostname}:8080/ws/bulk-upload/${jobId}`;
  
  const connection = createWebSocketConnection(wsUrl, {
    onOpen: () => {
      console.log(`Connected to job ${jobId} WebSocket`);
    },
    onMessage: (data) => {
      if (data.type === 'connected') {
        console.log('Connection confirmed:', data.message);
      } else {
        // Pass update to callback
        onUpdate(data);
      }
    },
    onClose: (event) => {
      console.log(`Disconnected from job ${jobId}:`, event.reason);
    },
    onError: (error) => {
      console.error(`WebSocket error for job ${jobId}:`, error);
      if (onError) {
        onError(error);
      }
    }
  });

  // Return disconnect function
  return () => connection.disconnect();
}

/**
 * Check if WebSocket is supported by browser
 * @returns {boolean} True if WebSocket is supported
 */
export function isWebSocketSupported() {
  return 'WebSocket' in window || 'MozWebSocket' in window;
}

export default {
  createWebSocketConnection,
  connectToBulkUploadJob,
  isWebSocketSupported
};

