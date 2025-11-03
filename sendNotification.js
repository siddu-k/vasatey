const admin = require('firebase-admin');

const ANDROID_CHANNEL_ID = 'guardian_alert_channel';

// Initialize Firebase Admin SDK if not already initialized
if (!admin.apps.length) {
  try {
    admin.initializeApp({
      credential: admin.credential.cert(JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT))
    });
  } catch (error) {
    console.error('Firebase Admin Initialization Error:', error);
    throw new Error('Firebase configuration error');
  }
}

// CORS headers for cross-origin requests
const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type, Authorization',
  'Access-Control-Max-Age': '86400',
};

export default async function handler(req, res) {
  // Set CORS headers
  Object.entries(corsHeaders).forEach(([key, value]) => {
    res.setHeader(key, value);
  });

  // Handle preflight OPTIONS request
  if (req.method === 'OPTIONS') {
    return res.status(200).end();
  }

  // Only allow POST requests
  if (req.method !== 'POST') {
    return res.status(405).json({ 
      error: 'Method not allowed', 
      message: 'Only POST requests are supported' 
    });
  }

  try {
    // Destructure all fields from the request body
    const { token, title, body, fullName, email, phoneNumber, lastKnownLatitude, lastKnownLongitude } = req.body;

    if (!token) {
      return res.status(400).json({ 
        error: 'Missing required field', 
        message: 'FCM token is required' 
      });
    }

    if (!title || !body) {
      return res.status(400).json({ 
        error: 'Missing required fields', 
        message: 'Both title and body are required' 
      });
    }

    // Construct the FCM message - DATA ONLY (no notification object)
    // Your Android app will handle creating the notification from data
    const message = {
      token: token,
      // THE 'notification' OBJECT IS REMOVED.
      // All data, including what's visible, is now in the 'data' payload.
      data: {
        title: title,
        body: body,
        fullName: fullName || '',
        email: email || '',
        phoneNumber: phoneNumber || '',
        lastKnownLatitude: String(lastKnownLatitude || ''),
        lastKnownLongitude: String(lastKnownLongitude || ''),
        timestamp: new Date().toISOString(),
        source: 'vasatey-notify',
        alertType: 'emergency',
        channelId: ANDROID_CHANNEL_ID,
      },
      android: {
        priority: 'high',
      },
      apns: {
        payload: {
          aps: {
            'content-available': 1, // Silent notification - app handles display
          },
        },
        headers: {
          'apns-priority': '5', // Background priority for data-only
          'apns-push-type': 'background',
        },
      },
    };

    // Send the notification
    const response = await admin.messaging().send(message);
    
    console.log('Successfully sent data message:', response);
    
    return res.status(200).json({
      success: true,
      message: 'Data message sent successfully',
      messageId: response,
      timestamp: new Date().toISOString(),
    });

  } catch (error) {
    console.error('Error sending message:', error);
    console.error('Full error details:', {
      code: error.code,
      message: error.message,
      token: token ? `${token.substring(0, 20)}...` : 'null',
      timestamp: new Date().toISOString()
    });
    
    // Handle specific Firebase errors
    if (error.code === 'messaging/registration-token-not-registered') {
      console.log(`Token not registered: ${token ? token.substring(0, 20) + '...' : 'null'}`);
      return res.status(410).json({
        error: 'Token expired',
        message: 'The FCM token is not registered or has expired. Please refresh your app to get a new token.',
        code: error.code,
        action: 'refresh_token',
        details: 'This token has been invalidated and should be refreshed by the app'
      });
    }
    
    if (error.code === 'messaging/invalid-registration-token') {
      console.log(`Invalid token format: ${token ? token.substring(0, 20) + '...' : 'null'}`);
      return res.status(400).json({
        error: 'Invalid token format',
        message: 'The FCM token format is invalid. Please refresh your app to get a valid token.',
        code: error.code,
        action: 'refresh_token',
        details: 'Token format is malformed'
      });
    }
    
    if (error.code === 'messaging/invalid-argument') {
      return res.status(400).json({
        error: 'Invalid argument',
        message: 'One or more arguments to the request are invalid',
        code: error.code,
        details: error.message,
      });
    }

    if (error.code === 'messaging/message-rate-exceeded') {
      return res.status(429).json({
        error: 'Rate limit exceeded',
        message: 'Message rate exceeded for this token',
        code: error.code,
        action: 'retry_later',
      });
    }

    // Generic error response
    return res.status(500).json({
      error: 'Internal server error',
      message: 'Failed to send notification',
      code: error.code || 'unknown',
      details: process.env.NODE_ENV === 'development' ? error.message : undefined,
    });
  }
}