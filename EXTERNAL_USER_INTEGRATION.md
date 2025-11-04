# External User Integration Guide

This guide explains how to integrate external users from your other application into the Quant-UX backend.

## Overview

The Quant-UX backend supports **idempotent external user creation** via `POST /rest/user/external`. This means you can call this endpoint multiple times with the same user ID - it will create the user if they don't exist, or return the existing user if they do.

## API Endpoint

**POST** `/rest/user/external`

### Request Body

```json
{
  "id": "external-user-123", // Required: Your external user ID
  "email": "user@example.com", // Required: User email
  "name": "John", // Required: User first name
  "lastname": "Doe" // Optional: User last name
}
```

### Response

```json
{
  "id": "external-user-123",
  "email": "user@example.com",
  "name": "John",
  "lastname": "Doe",
  "role": "user",
  "external": true,
  "created": 1234567890,
  "lastUpdate": 1234567890,
  "acceptedGDPR": true
}
```

## Integration Patterns

### Pattern 1: On-Demand User Creation (Recommended)

Create users automatically when they first access Quant-UX features from your app.

**Use Case**: When a user clicks "Open in Quant-UX" or accesses a Quant-UX feature for the first time.

```javascript
// Example: Node.js/Express
async function ensureQuantUXUser(externalUserId, userData) {
  try {
    const response = await fetch('https://your-quantux-backend.com/rest/user/external', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        id: externalUserId,
        email: userData.email,
        name: userData.name,
        lastname: userData.lastname || ''
      })
    });

    if (!response.ok) {
      throw new Error(`Failed to create user: ${response.statusText}`);
    }

    const quantUXUser = await response.json();
    return quantUXUser;
  } catch (error) {
    console.error('Error ensuring Quant-UX user:', error);
    throw error;
  }
}

// Usage in your route handler
app.post('/api/open-quantux/:userId', async (req, res) => {
  const externalUserId = req.params.userId;
  const user = await getUserFromYourDatabase(externalUserId);

  // Ensure user exists in Quant-UX (idempotent - safe to call multiple times)
  const quantUXUser = await ensureQuantUXUser(externalUserId, {
    email: user.email,
    name: user.firstName,
    lastname: user.lastName
  });

  // Now you can use quantUXUser.id for Quant-UX operations
  res.json({ quantUXUserId: quantUXUser.id });
});
```

### Pattern 2: Batch User Sync

Pre-create all users in Quant-UX when they're created in your system.

**Use Case**: When you want to ensure all users exist in Quant-UX upfront.

```javascript
// Sync user when created in your system
async function syncUserToQuantUX(user) {
  try {
    // Use your internal user ID as the external ID
    const externalId = user.id.toString();

    await fetch('https://your-quantux-backend.com/rest/user/external', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        id: externalId,
        email: user.email,
        name: user.firstName,
        lastname: user.lastName
      })
    });

    console.log(`Synced user ${externalId} to Quant-UX`);
  } catch (error) {
    console.error(`Failed to sync user ${user.id}:`, error);
    // Consider queuing for retry
  }
}

// In your user creation handler
app.post('/api/users', async (req, res) => {
  const newUser = await createUserInYourSystem(req.body);

  // Sync to Quant-UX (fire and forget or await based on your needs)
  syncUserToQuantUX(newUser).catch((err) => {
    // Log but don't fail user creation
    console.error('Quant-UX sync failed:', err);
  });

  res.json(newUser);
});
```

### Pattern 3: Proxy Authentication

Create a proxy endpoint that handles user creation and returns a JWT token.

**Use Case**: When you want to handle authentication centrally and provide tokens to your frontend.

```javascript
// Backend endpoint in your application
app.post('/api/quantux/auth', authenticate, async (req, res) => {
  const currentUser = req.user; // From your auth middleware

  // Ensure user exists in Quant-UX
  const quantUXUser = await ensureQuantUXUser(currentUser.id, {
    email: currentUser.email,
    name: currentUser.firstName,
    lastname: currentUser.lastName
  });

  // Now login to get Quant-UX JWT token
  // Note: External users have random passwords, so you'll need to handle this differently
  // See "Authentication Options" below

  res.json({
    quantUXUserId: quantUXUser.id
    // You'll need to implement token exchange
  });
});
```

## Authentication Options

### Option A: Direct Token Generation (Recommended)

If you have access to the Quant-UX JWT secret, generate tokens directly in your application.

```javascript
const jwt = require('jsonwebtoken');
const QUANTUX_JWT_SECRET = process.env.QUANTUX_JWT_SECRET;

function generateQuantUXToken(quantUXUser) {
  return jwt.sign(
    {
      id: quantUXUser.id,
      email: quantUXUser.email,
      name: quantUXUser.name,
      lastname: quantUXUser.lastname,
      role: quantUXUser.role
    },
    QUANTUX_JWT_SECRET,
    {
      issuer: 'MATC',
      expiresIn: '7d'
    }
  );
}

// After creating/getting user
const token = generateQuantUXToken(quantUXUser);
```

### Option B: Password-Based Login

External users have randomly generated passwords. You'd need to store these or use a different approach.

**Not Recommended**: This requires storing passwords or implementing password exchange.

### Option C: Token Exchange Endpoint

Create a custom endpoint in Quant-UX that accepts your external auth token and exchanges it for a Quant-UX token.

**Requires backend modification**: You'd need to add this endpoint to the Quant-UX backend.

## Complete Integration Example

Here's a full example showing user creation and app access:

```javascript
// quantux-client.js
class QuantUXClient {
  constructor(baseURL, jwtSecret) {
    this.baseURL = baseURL;
    this.jwtSecret = jwtSecret;
  }

  async ensureUser(externalUserId, userData) {
    const response = await fetch(`${this.baseURL}/rest/user/external`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        id: externalUserId,
        email: userData.email,
        name: userData.name,
        lastname: userData.lastname || ''
      })
    });

    if (!response.ok) {
      throw new Error(`User creation failed: ${response.statusText}`);
    }

    return await response.json();
  }

  generateToken(user) {
    const jwt = require('jsonwebtoken');
    return jwt.sign(
      {
        id: user.id,
        email: user.email,
        name: user.name,
        lastname: user.lastname,
        role: user.role
      },
      this.jwtSecret,
      {
        issuer: 'MATC',
        expiresIn: '7d'
      }
    );
  }

  async createApp(token, appData) {
    const response = await fetch(`${this.baseURL}/rest/apps`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      body: JSON.stringify(appData)
    });

    return await response.json();
  }

  async getApps(token) {
    const response = await fetch(`${this.baseURL}/rest/apps`, {
      headers: {
        Authorization: `Bearer ${token}`
      }
    });

    return await response.json();
  }
}

// Usage in your application
const quantux = new QuantUXClient(process.env.QUANTUX_BACKEND_URL, process.env.QUANTUX_JWT_SECRET);

app.get('/api/my-quantux-apps', authenticate, async (req, res) => {
  try {
    const currentUser = req.user;

    // Ensure user exists in Quant-UX
    const quantUXUser = await quantux.ensureUser(currentUser.id, {
      email: currentUser.email,
      name: currentUser.firstName,
      lastname: currentUser.lastName
    });

    // Generate Quant-UX JWT token
    const token = quantux.generateToken(quantUXUser);

    // Get user's apps
    const apps = await quantux.getApps(token);

    res.json({ apps });
  } catch (error) {
    console.error('Quant-UX integration error:', error);
    res.status(500).json({ error: 'Failed to fetch apps' });
  }
});
```

## Best Practices

### 1. Idempotency

- Always use the same external user ID for the same user
- The endpoint is idempotent - safe to call multiple times
- Use a consistent ID format (e.g., prefix with your app name: `myapp-user-123`)

### 2. User ID Mapping

- Store the mapping between your user ID and Quant-UX user ID in your database
- This allows you to quickly look up Quant-UX users without calling the API

```javascript
// Store mapping
await db.users.updateOne({ id: yourUserId }, { $set: { quantUXUserId: quantUXUser.id } });
```

### 3. Error Handling

- Handle network errors gracefully
- Retry failed requests with exponential backoff
- Don't fail your user creation if Quant-UX sync fails

### 4. User Updates

- When user data changes in your system, update Quant-UX user via `POST /rest/user/{id}.json`
- Keep email, name, and other profile fields in sync

```javascript
async function updateQuantUXUser(quantUXUserId, userData, token) {
  await fetch(`${quantuxBackend}/rest/user/${quantUXUserId}.json`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`
    },
    body: JSON.stringify({
      email: userData.email,
      name: userData.name,
      lastname: userData.lastName
    })
  });
}
```

### 5. Security

- Store Quant-UX JWT secret securely (environment variables, secrets manager)
- Use HTTPS for all API calls
- Validate user data before sending to Quant-UX
- Consider rate limiting for user creation endpoints

### 6. Monitoring

- Log all Quant-UX API calls
- Monitor success/failure rates
- Set up alerts for integration failures
- Track user creation latency

## Common Workflows

### Workflow 1: User Opens Quant-UX Feature

1. User clicks "Open Quant-UX" in your app
2. Your backend calls `POST /rest/user/external` (idempotent)
3. Generate Quant-UX JWT token
4. Redirect frontend to Quant-UX with token
5. Frontend uses token for all Quant-UX API calls

### Workflow 2: Automatic App Creation

1. User creates a project in your app
2. Your backend ensures Quant-UX user exists
3. Create corresponding Quant-UX app via `POST /rest/apps`
4. Store app ID mapping in your database
5. User can now access Quant-UX features for that project

### Workflow 3: User Profile Sync

1. User updates profile in your app
2. Your backend updates Quant-UX user via `POST /rest/user/{id}.json`
3. Keep both systems in sync

## Troubleshooting

### User Already Exists Error

- The endpoint is idempotent - it should return existing users
- Check if you're using different IDs for the same user
- Verify the user ID format is consistent

### Authentication Failures

- Verify JWT secret matches Quant-UX backend configuration
- Check token expiration (default 7 days)
- Ensure token includes all required claims (id, email, name, lastname, role)

### Permission Issues

- External users have `role: "user"` by default
- Users can only access apps they own or have permissions for
- Check app permissions via `app.users.{userId}` field

## Environment Variables

```bash
# Quant-UX Backend Configuration
QUANTUX_BACKEND_URL=https://your-quantux-backend.com
QUANTUX_JWT_SECRET=your-jwt-secret-key
```

## Next Steps

1. **Test the integration**: Start with a single user to verify the flow
2. **Implement user mapping**: Store Quant-UX user IDs in your database
3. **Add error handling**: Implement retry logic and proper error handling
4. **Monitor usage**: Set up logging and monitoring for the integration
5. **Scale gradually**: Test with increasing numbers of users
