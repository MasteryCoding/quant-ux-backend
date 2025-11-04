# Node.js/Express Migration Plan

## Folder Structure

```
quant-ux-backend-node/
├── src/
│   ├── config/
│   │   └── index.js                 # Config management (replaces Config.java)
│   ├── middleware/
│   │   ├── auth.js                  # JWT authentication middleware
│   │   ├── cors.js                  # CORS configuration
│   │   ├── errorHandler.js          # Error handling middleware
│   │   └── acl.js                   # ACL checking middleware
│   ├── models/
│   │   ├── User.js
│   │   ├── App.js
│   │   ├── Team.js
│   │   ├── Comment.js
│   │   ├── Event.js
│   │   ├── Annotation.js
│   │   ├── Invitation.js
│   │   ├── Library.js
│   │   ├── LibraryTeam.js
│   │   ├── CommandStack.js
│   │   ├── TestSetting.js
│   │   ├── Image.js
│   │   ├── AppEvent.js
│   │   └── index.js                 # MongoDB connection & models
│   ├── services/
│   │   ├── auth/
│   │   │   └── tokenService.js      # JWT token service
│   │   ├── blob/
│   │   │   ├── fileSystemService.js # File system blob service
│   │   │   └── index.js
│   │   ├── mail/
│   │   │   ├── mailHandler.js        # Email sending service
│   │   │   └── templates/            # Handlebars email templates
│   │   └── eventBus.js               # Event bus for app events
│   ├── controllers/
│   │   ├── userController.js
│   │   ├── appController.js
│   │   ├── teamController.js
│   │   ├── commentController.js
│   │   ├── eventController.js
│   │   ├── annotationController.js
│   │   ├── invitationController.js
│   │   ├── libraryController.js
│   │   ├── libraryTeamController.js
│   │   ├── commandStackController.js
│   │   ├── testSettingsController.js
│   │   ├── imageController.js
│   │   └── passwordController.js
│   ├── routes/
│   │   ├── index.js                  # Main router
│   │   ├── userRoutes.js
│   │   ├── appRoutes.js
│   │   ├── teamRoutes.js
│   │   ├── commentRoutes.js
│   │   ├── eventRoutes.js
│   │   ├── annotationRoutes.js
│   │   ├── invitationRoutes.js
│   │   ├── libraryRoutes.js
│   │   ├── commandRoutes.js
│   │   ├── testRoutes.js
│   │   └── imageRoutes.js
│   ├── utils/
│   │   ├── db.js                     # MongoDB utilities
│   │   ├── mongoQuery.js             # Query builders
│   │   ├── mongoFilter.js
│   │   ├── validators.js             # Input validation
│   │   ├── imageUpload.js
│   │   ├── jsonMapper.js
│   │   ├── previewEngine.js
│   │   └── helpers.js                # General utilities
│   ├── acl/
│   │   ├── index.js                  # ACL interface
│   │   ├── appAcl.js
│   │   ├── commentAcl.js
│   │   ├── eventAcl.js
│   │   ├── invitationAcl.js
│   │   ├── libraryAcl.js
│   │   └── userAcl.js
│   └── server.js                     # Main entry point (replaces MATC.java)
├── resources/
│   └── emails/
│       └── qux/                      # Email templates (HTML & TXT)
│           ├── reset_password.html
│           ├── reset_password.txt
│           ├── user_created.html
│           ├── user_created.txt
│           ├── team_added.html
│           ├── team_added.txt
│           ├── client_error.html
│           ├── client_error.txt
│           ├── frame.html
│           └── frame.txt
├── tests/
│   ├── unit/
│   ├── integration/
│   └── fixtures/
├── .env.example
├── .gitignore
├── package.json
├── README.md
└── server.js                         # Alternative entry point
```

## Package.json Dependencies

```json
{
  "name": "quant-ux-backend",
  "version": "4.5.6",
  "description": "Quant-UX Server Backend",
  "main": "src/server.js",
  "scripts": {
    "start": "node src/server.js",
    "dev": "nodemon src/server.js",
    "test": "jest",
    "test:watch": "jest --watch",
    "lint": "eslint src/"
  },
  "dependencies": {
    "express": "^4.18.2",
    "mongodb": "^6.0.0",
    "mongoose": "^7.0.0",
    "jsonwebtoken": "^9.0.2",
    "bcryptjs": "^2.4.3",
    "handlebars": "^4.7.8",
    "nodemailer": "^6.9.4",
    "multer": "^1.4.5-lts.1",
    "cors": "^2.8.5",
    "helmet": "^7.0.0",
    "compression": "^1.7.4",
    "dotenv": "^16.3.1",
    "joi": "^17.11.0",
    "express-validator": "^7.0.1",
    "winston": "^3.11.0",
    "winston-daily-rotate-file": "^4.7.1",
    "express-rate-limit": "^7.1.3",
    "mime-types": "^2.1.35",
    "sharp": "^0.32.6",
    "uuid": "^9.0.1",
    "moment": "^2.29.4"
  },
  "devDependencies": {
    "nodemon": "^3.0.1",
    "jest": "^29.7.0",
    "supertest": "^6.3.3",
    "eslint": "^8.53.0",
    "@types/node": "^20.9.0"
  },
  "engines": {
    "node": ">=18.0.0",
    "npm": ">=9.0.0"
  }
}
```

## Dependency Mapping

### Core Framework

- **Vert.x Web** → `express`
- **Vert.x Core** → Built into Node.js (event loop)

### Database

- **vertx-mongo-client** → `mongodb` or `mongoose`
- **MongoDB** → Same MongoDB, just different client

### Authentication & Security

- **java-jwt** → `jsonwebtoken`
- **spring-security-crypto** → `bcryptjs` (for password hashing)
- **commons-codec** → Built into Node.js (crypto module)

### Email

- **vertx-mail-client** → `nodemailer`
- **handlebars** → `handlebars` (same library, different language)

### File Upload & Blob Storage

- **vertx-web** (file upload) → `multer`
- Custom blob service → `fs` module + custom service

### Utilities

- **guava** → Native JavaScript or `lodash`
- **log4j2** → `winston`
- **httpclient** → `axios` or native `fetch` (Node 18+)

### Validation

- **validation package** → `joi` or `express-validator`

### Middleware

- CORS → `cors`
- Compression → `compression`
- Security → `helmet`
- Rate limiting → `express-rate-limit`

## Key Migration Points

### 1. Server Entry Point

**Java (MATC.java)** → **Node.js (server.js)**

- Express app initialization
- Middleware setup
- Route registration
- Server startup

### 2. REST Controllers

**Java (REST classes)** → **Express (controllers + routes)**

- Separate route definitions from controller logic
- Use Express middleware for authentication
- Use async/await instead of callbacks

### 3. ACL System

**Java (ACL classes)** → **Express (middleware + ACL services)**

- Create ACL middleware functions
- Use Express middleware chain for permission checking

### 4. Database Access

**Java (MongoClient)** → **Node.js (MongoDB driver or Mongoose)**

- Use async/await instead of Vert.x callbacks
- Consider Mongoose for schema validation

### 5. Email Templates

**Java (Handlebars)** → **Node.js (Handlebars)**

- Same template engine, just different syntax
- Keep template files in `resources/emails/qux/`

### 6. Configuration

**Java (Config.java)** → **Node.js (config/index.js + dotenv)**

- Use `.env` files for environment variables
- Use `dotenv` package to load config

## Example File Structure

### src/server.js

```javascript
const express = require('express');
const cors = require('cors');
const compression = require('compression');
const helmet = require('helmet');
const config = require('./config');
const { connectDB } = require('./models');
const routes = require('./routes');

const app = express();
const PORT = config.http.port || 8080;

// Middleware
app.use(helmet());
app.use(compression());
app.use(cors(config.cors));
app.use(express.json());
app.use(express.urlencoded({ extended: false }));

// Routes
app.use('/rest', routes);

// Error handling
app.use(require('./middleware/errorHandler'));

// Start server
async function start() {
  await connectDB();
  app.listen(PORT, () => {
    console.log(`******************************************`);
    console.log(`* Quant-UX-Server ${config.version} launched at ${PORT}`);
    console.log(`******************************************`);
  });
}

start();
```

### src/routes/userRoutes.js

```javascript
const express = require('express');
const router = express.Router();
const userController = require('../controllers/userController');
const { authenticate } = require('../middleware/auth');

router.post('/user', userController.create);
router.post('/login', userController.login);
router.delete('/login', authenticate, userController.logout);
router.get('/user', authenticate, userController.current);
router.get('/user/:id', authenticate, userController.findById);
router.post('/user/:id', authenticate, userController.update);
// ... more routes
```

### src/controllers/userController.js

```javascript
const User = require('../models/User');
const { hashPassword, comparePassword } = require('../utils/helpers');
const { generateToken } = require('../services/auth/tokenService');

exports.create = async (req, res, next) => {
  try {
    const { email, password, name, lastname } = req.body;
    // Validation, hashing, user creation
    // ...
  } catch (error) {
    next(error);
  }
};
```

## Migration Steps

1. **Setup project structure** - Create folder structure
2. **Install dependencies** - Run `npm install`
3. **Setup MongoDB connection** - Create connection utility
4. **Migrate models** - Convert Java models to Mongoose schemas or MongoDB documents
5. **Migrate utilities** - Convert utility classes to Node modules
6. **Migrate ACL system** - Convert to Express middleware
7. **Migrate controllers** - Convert REST classes to controllers
8. **Setup routes** - Create Express routes
9. **Migrate email service** - Setup nodemailer with Handlebars
10. **Setup authentication** - JWT middleware
11. **Testing** - Write tests for critical paths
12. **Deployment** - Update deployment configs
